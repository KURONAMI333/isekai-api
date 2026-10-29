package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.remap.ColumnBand;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.CarverScope;
import com.kuronami.isekaiapi.impl.DimensionFeatureScope;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.PlacedFeatureRebuilder;
import com.kuronami.isekaiapi.impl.RemapEngine;
import com.kuronami.isekaiapi.impl.RemapTargets;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * ADD-phase logic for {@code isekai_api:apply_worldshape}. Four concerns:
 * <ul>
 *   <li>{@link #registerFeatureScope} — record the keys this pass takes over, so decoration
 *       drops the originals in this dimension and nowhere else.</li>
 *   <li>{@link #additionalFeatures} — inject each entry of {@code additions.features}
 *       into the named decoration step.</li>
 *   <li>{@link #additionalCarvers} — record each entry of {@code additions.carvers} for the
 *       descriptor's dimension, so the carve pass appends it to the named carving step there
 *       and not in every dimension that shares the biome ({@link CarverScope}).</li>
 *   <li>{@link #remappedOreFeatures} — synthesize new PlacedFeatures by applying the
 *       active {@code oreStrategy} to the VerticalRange of every remap target of this
 *       biome and re-inject under the original decoration step(s). Despite the name the
 *       {@code oreStrategy} governs every height-ranged feature in the biome, not just
 *       ores — springs, geodes and lakes ride the same path.</li>
 * </ul>
 *
 * <p>All three short-circuit when the server context isn't available (datagen, early
 * lifecycle) or when there's nothing to add. The remap path additionally short-circuits
 * on {@link RemapStrategy.Identity}.
 *
 * <p>Every injected feature is a distinct copy carrying a
 * {@code isekai_api:dimension_guard} modifier, so it places only in the descriptor's
 * dimension. A {@code feature_predicates} entry on a remap target is prepended to that copy
 * as well — the original feature is no longer rewritten in place, so the predicate travels
 * with the replacement instead.
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

    /**
     * Record what this ADD pass takes over in {@code biome}, so decoration drops exactly the
     * originals whose replacement was just injected. Called first in the ADD phase.
     *
     * <p>This is the only place the takeover set is computed: {@code /reload} does not re-run
     * biome modifiers, so deriving it again at decoration time would desynchronize the drop from
     * the injection (a feature added to {@code feature_predicates} by a reload would vanish, a
     * removed one would generate twice). See {@link DimensionFeatureScope}.
     */
    public static void registerFeatureScope(WorldshapeDescriptor descriptor,
                                             ResourceKey<Biome> biomeKey) {
        if (biomeKey == null) return;
        Set<ResourceKey<PlacedFeature>> skip = new java.util.HashSet<>(descriptor.exclusions().features());
        skip.addAll(descriptor.featurePredicates().keySet());
        skip.addAll(remapTargets(descriptor, biomeKey));
        DimensionFeatureScope.record(descriptor.dimension(), biomeKey, skip);
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
            generation.addFeature(af.step(), Holder.direct(
                    DimensionFeatureScope.guarded(holder.value(), descriptor.dimension(), null)));
            added++;
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] added {} additional placed features (dim={})",
                    added, descriptor.dimension().location());
        }
    }

    /**
     * Record {@code additions.carvers} for the descriptor's dimension and this biome, so the
     * carve pass appends them there and nowhere else. Only a dimension whose generator the
     * carve-pass hook cannot reach ({@link CarverScope#hookable}) keeps the old biome-wide
     * insertion, because there the alternative is losing the addition entirely.
     */
    public static void additionalCarvers(WorldshapeDescriptor descriptor,
                                          ResourceKey<Biome> biomeKey,
                                          ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var additions = descriptor.additions().carvers();
        if (additions.isEmpty() || biomeKey == null) return;
        MinecraftServer server = serverOrNull("additional_carvers");
        if (server == null) return;
        var lookup = server.registryAccess().lookupOrThrow(Registries.CONFIGURED_CARVER);
        List<WorldshapeDescriptor.AdditionalCarver> resolved = new java.util.ArrayList<>(additions.size());
        for (var ac : additions) {
            if (lookup.get(ac.carver()).isEmpty()) {
                IsekaiApi.LOGGER.warn("[Isekai] additional_carvers: '{}' not in registry; skipping",
                        ac.carver().location());
                continue;
            }
            resolved.add(ac);
        }
        if (resolved.isEmpty()) return;
        if (CarverScope.hookable(descriptor.dimension())) {
            CarverScope.recordAdditions(descriptor.dimension(), biomeKey, resolved);
            return;
        }
        var generation = builder.getGenerationSettings();
        for (var ac : resolved) {
            generation.getCarvers(ac.step()).add(lookup.getOrThrow(ac.carver()));
        }
        IsekaiApi.LOGGER.debug("[Isekai] added {} additional carvers (dim={})",
                resolved.size(), descriptor.dimension().location());
    }

    /**
     * Inject a guarded, predicated copy of every {@code feature_predicates} entry the biome
     * actually contains — the original stays in place and is dropped for this dimension at
     * decoration time by {@link DimensionFeatureScope}, so the predicate no longer rewrites
     * the shared biome.
     *
     * <p>Two entries are skipped: one whose key is in {@code exclusions.features} (the author
     * said it must not generate, and a copy would undo that), and one the remap already takes
     * over ({@link #remappedOreFeatures} applies its predicate to the rebuilt copy).
     */
    public static void predicatedFeatures(WorldshapeDescriptor descriptor,
                                           ResourceKey<Biome> biomeKey,
                                           ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var predicates = descriptor.featurePredicates();
        if (predicates.isEmpty()) return;
        MinecraftServer server = serverOrNull("feature_predicates");
        if (server == null) return;
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        var generation = builder.getGenerationSettings();
        Set<ResourceKey<PlacedFeature>> remapTargets = remapTargets(descriptor, biomeKey);
        int added = 0;
        for (var entry : predicates.entrySet()) {
            ResourceKey<PlacedFeature> key = entry.getKey();
            if (remapTargets.contains(key)) continue;
            if (descriptor.exclusions().features().contains(key)) continue;
            var holder = lookup.get(key).orElse(null);
            if (holder == null) {
                IsekaiApi.LOGGER.warn("[Isekai] feature_predicates: '{}' not in registry; skipping",
                        key.location());
                continue;
            }
            PlacedFeature copy = DimensionFeatureScope.guarded(
                    holder.value(), descriptor.dimension(), entry.getValue());
            Holder<PlacedFeature> copyHolder = Holder.direct(copy);
            for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
                if (containsKey(generation.getFeatures(step), key)) {
                    generation.addFeature(step, copyHolder);
                    added++;
                }
            }
        }
        if (added > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] injected {} predicated feature copy/copies (dim={})",
                    added, descriptor.dimension().location());
        }
    }

    /** The remap takeover set for this biome, or empty when the strategy is identity. */
    private static Set<ResourceKey<PlacedFeature>> remapTargets(WorldshapeDescriptor descriptor,
                                                                 ResourceKey<Biome> biomeKey) {
        if (descriptor.oreStrategy() instanceof RemapStrategy.Identity) return Set.of();
        if (biomeKey == null) return Set.of();
        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        if (snapshot == null) return Set.of();
        return RemapTargets.select(snapshot, biomeKey,
                descriptor.dimension(), descriptor.exclusions().features());
    }

    private static boolean containsKey(List<Holder<PlacedFeature>> step,
                                        ResourceKey<PlacedFeature> key) {
        for (Holder<PlacedFeature> holder : step) {
            if (holder.is(key)) return true;
        }
        return false;
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

        // The set the decoration-time scope drops, computed by the same function so ADD can
        // neither miss one nor invent one. It is scoped to this biome (otherwise plains would
        // receive diamond and every modded ore) and it honours exclusions.features — a
        // re-injected feature is an anonymous Holder.direct with no ResourceKey, so an
        // excluded key put back here could never be removed again by any later pass.
        ResourceKey<Level> dimension = descriptor.dimension();
        Set<ResourceKey<PlacedFeature>> targets = RemapTargets.select(
                snapshot, biomeKey, dimension, descriptor.exclusions().features());
        if (targets.isEmpty()) return;

        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        var generation = builder.getGenerationSettings();
        var strategy = descriptor.oreStrategy();
        var playable = descriptor.playableRange();
        // Source envelope is the dimension's real build height — the nether is 0..255,
        // not the overworld's -64..319. Sourced from the LevelStem registry, so it is
        // already correct on the first modifier pass (before any ServerLevel exists).
        int worldBottom = snapshot.worldBottom(dimension);
        int worldTop = snapshot.worldTop(dimension);
        int added = 0;
        for (PlacedFeatureInfo info : snapshot.placedFeatures()) {
            if (!targets.contains(info.key())) continue;
            var original = lookup.get(info.key()).orElse(null);
            if (original == null) continue;
            // Targets already exclude features whose anchors invert here, so a present
            // override is a real range; absent means this dimension shares overworld bounds.
            var sourceRange = snapshot.placedFeatureRangeInDimension(info.key(), dimension)
                    .orElse(info.range());
            // Terrain-relative strategies resolve per column at placement time; everything else
            // bakes one absolute band. Both rebuild paths return null on the same condition
            // (no HeightRangePlacement), keeping ADD symmetric with REMOVE.
            var band = RemapEngine.applyColumn(strategy, sourceRange, playable,
                    worldBottom, worldTop);
            PlacedFeature rebuilt;
            if (band.isPresent()) {
                warnCollapsedBandOnce(descriptor, info.key(), band.get());
                rebuilt = PlacedFeatureRebuilder.withColumnBand(original.value(), band.get());
            } else {
                var newRange = RemapEngine.apply(strategy, sourceRange, playable,
                        worldBottom, worldTop);
                rebuilt = PlacedFeatureRebuilder.withNewRange(original.value(), newRange);
            }
            if (rebuilt == null) continue;
            // The copy keeps the original's feature and rebuilt placement, but is pinned to
            // the descriptor's dimension and, when the author asked for one, gated by the
            // feature predicate. The original stays in the biome untouched; the decoration
            // scope drops it in this dimension so the two never both run.
            PlacedFeature guarded = DimensionFeatureScope.guarded(
                    rebuilt, dimension, descriptor.featurePredicates().get(info.key()));
            Holder<PlacedFeature> rebuiltHolder = Holder.direct(guarded);
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

    /** Feature keys already reported per dimension, so the notice fires once per pair. */
    private static final Set<String> COLLAPSED_WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Report exactly once per (dimension, feature) when a terrain-relative projection has
     * collapsed onto one of its anchors — see {@link ColumnBand#collapsedAtAnchor()}. The
     * feature still generates, at the single depth the collapsed band names, so nothing here
     * throws or drops it; but every sample lands on the same plane at the very edge of the
     * body, which for an ore is indistinguishable from "this ore does not spawn". The strategy
     * cannot say which reference levels the author meant, and the author cannot see the band —
     * only the source Y range and the resulting stone that never appears — so the log line
     * names both ends of the mismatch, which is the whole diagnosis.
     */
    private static void warnCollapsedBandOnce(WorldshapeDescriptor descriptor,
                                              ResourceKey<PlacedFeature> key,
                                              ColumnBand band) {
        if (!band.collapsedAtAnchor()) return;
        String dim = descriptor.dimension().location().toString();
        if (!COLLAPSED_WARNED.add(dim + "|" + key.location())) return;
        IsekaiApi.LOGGER.warn(
                "[Isekai] {}: {} remapped to a zero-width band at depth {} — its whole vanilla Y "
                        + "range lies outside the ore_strategy's reference column, so every sample "
                        + "resolves to the same plane at the edge of the terrain and the feature is "
                        + "effectively absent. Widen surface_y/floor_y in the worldshape's "
                        + "ore_strategy to cover this feature's Y range.",
                dim, key.location(), band.fromDepth());
    }
}
