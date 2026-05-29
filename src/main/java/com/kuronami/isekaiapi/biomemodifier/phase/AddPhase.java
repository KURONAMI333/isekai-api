package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.PlacedFeatureRebuilder;
import com.kuronami.isekaiapi.impl.RemapEngine;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * ADD-phase logic for {@code isekai_api:apply_worldshape}. Three concerns:
 * <ul>
 *   <li>{@link #additionalFeatures} — inject each entry of {@code additions.features}
 *       into the named decoration step.</li>
 *   <li>{@link #additionalCarvers} — inject each entry of {@code additions.carvers}
 *       into the named carving step.</li>
 *   <li>{@link #remappedOreFeatures} — synthesize new PlacedFeatures by applying the
 *       active {@code oreStrategy} to each snapshot ore's VerticalRange and re-inject
 *       under the original decoration step(s).</li>
 * </ul>
 *
 * <p>All three short-circuit when the server context isn't available (datagen, early
 * lifecycle) or when there's nothing to add. The remap path additionally short-circuits
 * on {@link RemapStrategy.Identity}.
 */
@ApiStatus.Internal
public final class AddPhase {

    private AddPhase() {}

    /**
     * Return the current server, or {@code null} when the modifier runs outside a server
     * context (datagen, early lifecycle). All ADD-phase operations need
     * {@link MinecraftServer#registryAccess()} so a null result is the universal exit.
     */
    private static MinecraftServer serverOrNull(String tag) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            IsekaiApi.LOGGER.debug("[Isekai] {}: no server context, skipping", tag);
        }
        return server;
    }

    public static void additionalFeatures(WorldshapeDescriptor descriptor,
                                           ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var additions = descriptor.additions().features();
        if (additions.isEmpty()) return;
        MinecraftServer server = serverOrNull("additional_features");
        if (server == null) return;
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        var generation = builder.getGenerationSettings();
        int added = 0;
        for (var af : additions) {
            var holder = lookup.get(af.feature()).orElse(null);
            if (holder == null) {
                IsekaiApi.LOGGER.warn("[Isekai] additional_features: '{}' not in registry; skipping",
                        af.feature().location());
                continue;
            }
            generation.addFeature(af.step(), holder);
            added++;
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] added {} additional placed features (dim={})",
                    added, descriptor.dimension().location());
        }
    }

    public static void additionalCarvers(WorldshapeDescriptor descriptor,
                                          ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var additions = descriptor.additions().carvers();
        if (additions.isEmpty()) return;
        MinecraftServer server = serverOrNull("additional_carvers");
        if (server == null) return;
        var lookup = server.registryAccess().lookupOrThrow(Registries.CONFIGURED_CARVER);
        var generation = builder.getGenerationSettings();
        int added = 0;
        for (var ac : additions) {
            var holder = lookup.get(ac.carver()).orElse(null);
            if (holder == null) {
                IsekaiApi.LOGGER.warn("[Isekai] additional_carvers: '{}' not in registry; skipping",
                        ac.carver().location());
                continue;
            }
            generation.getCarvers(ac.step()).add(holder);
            added++;
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] added {} additional carvers (dim={})",
                    added, descriptor.dimension().location());
        }
    }

    public static void remappedOreFeatures(WorldshapeDescriptor descriptor,
                                            ResourceKey<Biome> biomeKey,
                                            ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (descriptor.oreStrategy() instanceof RemapStrategy.Identity) return;
        if (biomeKey == null) return;  // Holder<Biome> without a key (direct holder); skip.
        MinecraftServer server = serverOrNull("remapped_ore_features");
        if (server == null) return;
        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        if (snapshot == null || snapshot.isEmpty()) return;

        // Per-biome scoping: only re-inject ores that were originally in THIS biome at
        // scan time. Without this filter, every matched biome would receive every snapshot
        // ore — plains would generate diamond, modded ores, etc, even though it never had them.
        Set<net.minecraft.resources.ResourceKey<PlacedFeature>> originalFeatures = snapshot.featuresInBiome(biomeKey);
        if (originalFeatures.isEmpty()) return;

        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        var generation = builder.getGenerationSettings();
        var strategy = descriptor.oreStrategy();
        var playable = descriptor.playableRange();
        int added = 0;
        for (PlacedFeatureInfo info : snapshot.placedFeatures()) {
            if (snapshot.isFallback(info)) continue;
            if (!originalFeatures.contains(info.key())) continue;
            var original = lookup.get(info.key()).orElse(null);
            if (original == null) continue;
            var newRange = RemapEngine.apply(strategy, info.range(), playable,
                    snapshot.worldBottom(), snapshot.worldTop());
            PlacedFeature rebuilt = PlacedFeatureRebuilder.withNewRange(original.value(), newRange);
            if (rebuilt == null) continue;
            Holder<PlacedFeature> rebuiltHolder = Holder.direct(rebuilt);
            Set<GenerationStep.Decoration> steps = snapshot.stepsFor(info.key());
            if (steps.isEmpty()) {
                generation.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, rebuiltHolder);
            } else {
                for (var step : steps) generation.addFeature(step, rebuiltHolder);
            }
            added++;
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] remapped {} ore features in biome {} (dim={}, strategy={}, playable={})",
                    added, biomeKey.location(), descriptor.dimension().location(),
                    strategy.getClass().getSimpleName(), playable);
        }
    }
}
