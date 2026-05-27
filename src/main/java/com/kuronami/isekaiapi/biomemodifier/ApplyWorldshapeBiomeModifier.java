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
            removeExcludedCarvers(builder);
            removeOriginalsThatWillBeRemapped(builder);
        } else if (phase == Phase.ADD) {
            addAdditionalFeatures(builder);
            addAdditionalCarvers(builder);
            addRemappedOreFeatures(builder);
        } else if (phase == Phase.MODIFY) {
            applyMobSpawnStrategy(builder);
            applyAtmosphereOverride(builder);
        }
    }

    /**
     * MODIFY phase: apply any set fields of {@link WorldshapeDescriptor#atmosphere()} to
     * the biome's {@code ClimateSettings} and {@code BiomeSpecialEffects}. Unset fields
     * (Optional.empty()) leave the biome's value unchanged.
     */
    private void applyAtmosphereOverride(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var atmos = worldshape.atmosphere();
        if (atmos.isNoOp()) {
            return;
        }
        var climate = builder.getClimateSettings();
        atmos.hasPrecipitation().ifPresent(climate::setHasPrecipitation);
        atmos.temperature().ifPresent(climate::setTemperature);
        atmos.downfall().ifPresent(climate::setDownfall);

        var effects = builder.getSpecialEffects();
        atmos.skyColor().ifPresent(effects::skyColor);
        atmos.fogColor().ifPresent(effects::fogColor);
        atmos.waterColor().ifPresent(effects::waterColor);
        atmos.waterFogColor().ifPresent(effects::waterFogColor);
        atmos.foliageColor().ifPresent(effects::foliageColorOverride);
        atmos.grassColor().ifPresent(effects::grassColorOverride);

        IsekaiApi.LOGGER.debug("[Isekai] applied atmosphere override (descriptor dim={})",
                worldshape.dimension().location());
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
     * and inject it into <em>each decoration step the original feature lived in</em>
     * (recorded at scan time, queried via {@link VanillaRuleSnapshot#stepsFor}).
     *
     * <p>Features the snapshot didn't see in any biome at scan time (e.g. ones added by a
     * later biome modifier) fall back to {@code UNDERGROUND_ORES} so they still generate.
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
        for (PlacedFeatureInfo info : snapshot.ores()) {
            // Only remap features whose vanilla VerticalRange we successfully extracted;
            // anything else doesn't have a HRP to swap.
            if (snapshot.isFallback(info)) {
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
                continue;
            }
            Holder<PlacedFeature> rebuiltHolder = Holder.direct(rebuilt);
            Set<GenerationStep.Decoration> steps = snapshot.stepsFor(info.key());
            if (steps.isEmpty()) {
                generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, rebuiltHolder);
            } else {
                for (GenerationStep.Decoration step : steps) {
                    generation.addFeature(step, rebuiltHolder);
                }
            }
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
            if (snapshot.isFallback(info)) continue;
            set.add(info.key());
        }
        return set;
    }

    /**
     * MODIFY phase: scale every mob spawn entry's weight by the effective count factor of
     * the per-category strategy resolved from
     * {@link WorldshapeDescriptor#resolveMobSpawnStrategy(net.minecraft.world.entity.MobCategory)}.
     * Identity / non-CountScale strategies leave weights unchanged. Each SpawnerData is
     * rebuilt with the scaled weight via the mutable backing list returned by
     * {@code MobSpawnSettingsBuilder.getSpawner(category)}.
     *
     * <p>Resolution order: per-category override from
     * {@link WorldshapeDescriptor#mobSpawnStrategyByCategory()} if present, else the
     * global {@link WorldshapeDescriptor#mobSpawnStrategy()}. Categories never present
     * in either path keep vanilla weights.
     */
    private void applyMobSpawnStrategy(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var spawnBuilder = builder.getMobSpawnSettings();
        int rebuilt = 0;
        for (var category : net.minecraft.world.entity.MobCategory.values()) {
            double factor = RemapEngine.effectiveCountFactor(worldshape.resolveMobSpawnStrategy(category));
            if (Math.abs(factor - 1.0) < 1e-6) continue;
            var list = spawnBuilder.getSpawner(category);
            for (int i = 0; i < list.size(); i++) {
                var sd = list.get(i);
                int oldWeight = sd.getWeight().asInt();
                int newWeight = Math.max(0, (int) Math.round(oldWeight * factor));
                if (newWeight == oldWeight) continue;
                list.set(i, new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(
                        sd.type, newWeight, sd.minCount, sd.maxCount));
                rebuilt++;
            }
        }
        if (rebuilt > 0) {
            IsekaiApi.LOGGER.debug(
                    "[Isekai] scaled {} mob spawn weights (descriptor dim={}, per-category overrides={})",
                    rebuilt, worldshape.dimension().location(),
                    worldshape.mobSpawnStrategyByCategory().keySet());
        }
    }

    private VanillaRuleSnapshot currentSnapshot() {
        // IsekaiQueryImpl is the canonical holder of the live snapshot; reach via the facade.
        var query = Isekai.query();
        if (query instanceof com.kuronami.isekaiapi.impl.IsekaiQueryImpl impl) {
            return impl.getSnapshot();
        }
        return null;
    }

    /**
     * REMOVE phase: drop every ConfiguredWorldCarver whose key appears in
     * {@link WorldshapeDescriptor#excludedCarvers()} from every carving step. Carvers are
     * the cave/canyon generation pass — removing them unlocks worldshapes like 'mountain
     * world with no carved caves' or 'surface world only'.
     */
    private void removeExcludedCarvers(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = worldshape.excludedCarvers();
        if (excluded.isEmpty()) return;
        var generation = builder.getGenerationSettings();
        int removed = 0;
        for (var step : net.minecraft.world.level.levelgen.GenerationStep.Carving.values()) {
            var stepCarvers = generation.getCarvers(step);
            int before = stepCarvers.size();
            stepCarvers.removeIf(holder ->
                    holder.unwrapKey().map(excluded::contains).orElse(false));
            removed += before - stepCarvers.size();
        }
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded carvers (descriptor dim={})",
                    removed, worldshape.dimension().location());
        }
    }

    /**
     * ADD phase: resolve each {@link WorldshapeDescriptor#additionalCarvers()} entry
     * against the CONFIGURED_CARVER registry and add it to the named carving step.
     * Mirrors the additional_features path but for carvers.
     */
    private void addAdditionalCarvers(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var additions = worldshape.additionalCarvers();
        if (additions.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        var lookup = server.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_CARVER);
        var generation = builder.getGenerationSettings();
        int added = 0;
        for (var ac : additions) {
            var holder = lookup.get(ac.carver()).orElse(null);
            if (holder == null) {
                IsekaiApi.LOGGER.warn(
                        "[Isekai] additional_carvers: '{}' not in CONFIGURED_CARVER registry; skipping",
                        ac.carver().location());
                continue;
            }
            generation.getCarvers(ac.step()).add(holder);
            added++;
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] added {} additional carvers (descriptor dim={})",
                    added, worldshape.dimension().location());
        }
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
