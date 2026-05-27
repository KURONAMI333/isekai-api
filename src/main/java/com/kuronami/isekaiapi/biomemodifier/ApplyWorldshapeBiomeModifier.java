package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.PlacedFeatureRebuilder;
import com.kuronami.isekaiapi.impl.RemapEngine;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link BiomeModifier} that applies an entire Isekai {@link WorldshapeDescriptor}
 * to matching biomes. Datapack consumers reference this via
 * {@code data/<ns>/neoforge/biome_modifier/foo.json} like:
 *
 * <pre>{@code
 * {
 *   "type": "isekai_api:apply_worldshape",
 *   "worldshape": {
 *     // ... full WorldshapeDescriptor JSON (same shape as data/<ns>/isekai/worldshape/*.json)
 *   }
 * }
 * }</pre>
 *
 * <p>v0.6 / v0.7 behavior:
 * <ul>
 *   <li>{@link Phase#REMOVE}: drops every {@link WorldshapeDescriptor#excludedFeatures()}
 *       from the biome's generation settings across all decoration steps.</li>
 *   <li>{@link Phase#ADD}: walks {@link WorldshapeDescriptor#additionalFeatures()} and
 *       calls {@code addFeature(step, holder)} for each — consumers can inject extra
 *       PlacedFeatures into specific decoration steps without writing a separate
 *       {@code neoforge:add_features} modifier per step.</li>
 * </ul>
 * Strategy-driven remap output (synthesizing new PlacedFeatures from
 * {@code oreStrategy} / {@code structureStrategy} / {@code mobSpawnStrategy}) is the
 * next v0.7+ deliverable and requires dynamic PlacedFeature creation.
 *
 * <p>The {@code appliesTo} filter is biome-keyed only. Dimension scoping is not
 * representable through {@link BiomeModifier} alone — biomes are reusable across
 * dimensions. Consumers wanting "only in the overworld" should either list every
 * overworld biome explicitly, or pair this modifier with a biome tag.
 */
public record ApplyWorldshapeBiomeModifier(WorldshapeDescriptor worldshape) implements BiomeModifier {

    public static final MapCodec<ApplyWorldshapeBiomeModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WorldshapeDescriptor.CODEC.fieldOf("worldshape").forGetter(ApplyWorldshapeBiomeModifier::worldshape)
    ).apply(i, ApplyWorldshapeBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (!matches(biome)) {
            return;
        }
        if (phase == Phase.REMOVE) {
            removeExcludedFeatures(builder);
            removeOriginalsThatWillBeRemapped(builder);
        } else if (phase == Phase.ADD) {
            addAdditionalFeatures(builder);
            addRemappedOreFeatures(builder);
        }
        // Phase.MODIFY for climate/visual adjustments (not in scope yet).
    }

    /**
     * @return {@code true} if this biome matches the descriptor's filter.
     *         Empty {@code appliesTo} = match every biome (caller controls scope via
     *         biome tags or by listing explicit biomes).
     */
    private boolean matches(Holder<Biome> biome) {
        var filter = worldshape.appliesTo();
        if (filter.isEmpty()) {
            return true;
        }
        for (var key : filter) {
            if (biome.is(key)) {
                return true;
            }
        }
        return false;
    }

    private void removeExcludedFeatures(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = worldshape.excludedFeatures();
        if (excluded.isEmpty()) {
            return;
        }
        var generation = builder.getGenerationSettings();
        int removed = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            // Each step holds a HolderSet<PlacedFeature>; getFeatures returns the mutable
            // backing list of holders.
            var stepFeatures = generation.getFeatures(step);
            int sizeBefore = stepFeatures.size();
            stepFeatures.removeIf(holder -> {
                var optKey = holder.unwrapKey();
                return optKey.isPresent() && excluded.contains(optKey.get());
            });
            removed += sizeBefore - stepFeatures.size();
        }
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded placed features (descriptor dim={})",
                    removed, worldshape.dimension().location());
        }
    }

    /**
     * REMOVE phase: drop every PlacedFeature whose key appears in the snapshot's ore list
     * and which will be re-added by the ADD-phase remap. Skipped when oreStrategy is
     * Identity (the in-place feature is fine) or when there's no server context.
     */
    private void removeOriginalsThatWillBeRemapped(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (worldshape.oreStrategy() instanceof RemapStrategy.Identity) {
            return;
        }
        Set<ResourceKey<PlacedFeature>> remapTargets = collectRemapTargets();
        if (remapTargets.isEmpty()) {
            return;
        }
        var generation = builder.getGenerationSettings();
        int removed = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            var stepFeatures = generation.getFeatures(step);
            int before = stepFeatures.size();
            stepFeatures.removeIf(holder -> holder.unwrapKey()
                    .map(remapTargets::contains).orElse(false));
            removed += before - stepFeatures.size();
        }
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} original features pending remap (descriptor dim={})",
                    removed, worldshape.dimension().location());
        }
    }

    /**
     * ADD phase: for each snapshot ore feature, apply the descriptor's ore strategy to its
     * vanilla VerticalRange, rebuild the PlacedFeature with the new HeightRangePlacement,
     * and inject it into the {@code UNDERGROUND_ORES} step as a direct holder.
     *
     * <p>v0.7 limitation: all remapped features land in {@code UNDERGROUND_ORES} regardless
     * of their original decoration step, since PlacedFeatures don't store their own step
     * (that's a per-biome property). For features whose actual step matters (e.g. surface
     * vegetation features), this would generate at the wrong phase. Per-step preservation
     * lands in v0.8 alongside a step-aware snapshot rebuild.
     */
    private void addRemappedOreFeatures(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (worldshape.oreStrategy() instanceof RemapStrategy.Identity) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        VanillaRuleSnapshot snapshot = currentSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);

        var generation = builder.getGenerationSettings();
        var strategy = worldshape.oreStrategy();
        var playable = worldshape.playableRange();
        int added = 0;
        int skipped = 0;
        for (PlacedFeatureInfo info : snapshot.ores()) {
            // Only remap features whose vanilla VerticalRange we successfully extracted;
            // anything else doesn't have a HRP to swap, so the rebuilder would no-op.
            if (info.range().minY() == -64 && info.range().maxY() == 320) {
                // Fallback range — skip.
                skipped++;
                continue;
            }
            var original = lookup.get(info.key()).orElse(null);
            if (original == null) {
                continue;
            }
            var newRange = RemapEngine.apply(strategy, info.range(), playable,
                    snapshot.worldBottom(), snapshot.worldTop());
            PlacedFeature rebuilt = PlacedFeatureRebuilder.withNewRange(original.value(), newRange);
            if (rebuilt == null) {
                // No HRP to swap — caller can't sensibly remap, leave as-is.
                continue;
            }
            generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, Holder.direct(rebuilt));
            added++;
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug(
                    "[Isekai] remapped {} ore features (descriptor dim={}, strategy={}, playable={})",
                    added, worldshape.dimension().location(),
                    strategy.getClass().getSimpleName(), playable);
        }
    }

    private Set<ResourceKey<PlacedFeature>> collectRemapTargets() {
        VanillaRuleSnapshot snapshot = currentSnapshot();
        if (snapshot == null) return Set.of();
        Set<ResourceKey<PlacedFeature>> set = new HashSet<>();
        for (PlacedFeatureInfo info : snapshot.ores()) {
            if (info.range().minY() == -64 && info.range().maxY() == 320) continue; // fallback
            set.add(info.key());
        }
        return set;
    }

    private VanillaRuleSnapshot currentSnapshot() {
        // IsekaiQueryImpl is the canonical holder of the live snapshot; reach via the facade.
        var query = Isekai.query();
        if (query instanceof com.kuronami.isekaiapi.impl.IsekaiQueryImpl impl) {
            return impl.getSnapshot();
        }
        return null;
    }

    private void addAdditionalFeatures(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var additions = worldshape.additionalFeatures();
        if (additions.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // Biome modifier is being asked to apply before the server registries are
            // resolved (e.g. early datagen). Skip — registry lookups would NPE.
            IsekaiApi.LOGGER.debug("[Isekai] addAdditionalFeatures: no server context, skipping");
            return;
        }
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        var generation = builder.getGenerationSettings();
        int added = 0;
        for (WorldshapeDescriptor.AdditionalFeature af : additions) {
            var holder = lookup.get(af.feature()).orElse(null);
            if (holder == null) {
                IsekaiApi.LOGGER.warn(
                        "[Isekai] additional_features: '{}' not in PLACED_FEATURE registry; skipping",
                        af.feature().location());
                continue;
            }
            generation.addFeature(af.step(), holder);
            added++;
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] added {} additional placed features (descriptor dim={})",
                    added, worldshape.dimension().location());
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return IsekaiBiomeModifiers.APPLY_WORLDSHAPE.get();
    }
}
