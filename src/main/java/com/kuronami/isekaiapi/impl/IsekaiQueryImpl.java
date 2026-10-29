package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.DimensionProfile;
import com.kuronami.isekaiapi.api.query.IsekaiQuery;
import com.kuronami.isekaiapi.api.query.MobSpawnInfo;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.query.StructurePlacementInfo;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.query.WorldshapeSnapshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;

/**
 * {@link IsekaiQuery} implementation backed by an immutable {@link VanillaRuleSnapshot}.
 * The lifecycle hook ({@link com.kuronami.isekaiapi.lifecycle.IsekaiLifecycle#onServerAboutToStart})
 * scans the registries at server start and calls {@link #setSnapshot} to populate the
 * cache; subsequent datapack reloads rebuild it via
 * {@link com.kuronami.isekaiapi.lifecycle.SnapshotRefreshListener}. Reads are O(1) once
 * the cache is populated.
 *
 * <p>Before the first scan completes (very early lifecycle stages), the snapshot is
 * {@link VanillaRuleSnapshot#EMPTY} and all queries return empty results.
 *
 * <p>Density function and noise settings lookups bypass the snapshot and read directly
 * from the live registry via {@link ServerLifecycleHooks#getCurrentServer()}, so they
 * always reflect the post-reload state without needing a fresh scan.
 */
@ApiStatus.Internal
public final class IsekaiQueryImpl implements IsekaiQuery {

    private final AtomicReference<VanillaRuleSnapshot> snapshot =
            new AtomicReference<>(VanillaRuleSnapshot.EMPTY);

    void setSnapshot(VanillaRuleSnapshot s) {
        var prev = snapshot.getAndSet(s);
        IsekaiApi.LOGGER.debug("[Isekai] Vanilla rule snapshot replaced: prev empty={}, new empty={}",
                prev.isEmpty(), s.isEmpty());
    }

    /**
     * Direct read of the currently-published snapshot. Package-private — phase appliers
     * reach this via {@link IsekaiInternal#currentSnapshot()} for raw access to per-feature
     * VerticalRanges and world bounds during strategy remapping.
     */
    VanillaRuleSnapshot getSnapshot() {
        return snapshot.get();
    }

    @Override
    public Optional<VerticalRange> getPlacedFeatureVerticalRange(ResourceKey<PlacedFeature> feature) {
        return snapshot.get().placedFeatures().stream()
                .filter(info -> info.key().equals(feature))
                .findFirst()
                .map(PlacedFeatureInfo::range);
    }

    @Override public List<PlacedFeatureInfo> getAllPlacedFeatures() { return snapshot.get().placedFeatures(); }

    @Override
    public Optional<VerticalRange> getPlacedFeatureVerticalRangeInDimension(ResourceKey<PlacedFeature> feature,
                                                                             ResourceKey<Level> dimension) {
        // Try the per-dim override first; fall back to the global (overworld-resolved) entry
        // when the dimension shares overworld bounds (no override stored).
        VanillaRuleSnapshot snap = snapshot.get();
        var override = snap.placedFeatureRangeInDimension(feature, dimension);
        if (override.isPresent()) {
            // A stored FALLBACK means the feature's anchors do not fit this dimension's
            // height column at all — don't hand the caller the overworld envelope instead.
            return override.get() == HeightProviderExtraction.FALLBACK_RANGE
                    ? Optional.empty() : override;
        }
        return getPlacedFeatureVerticalRange(feature);
    }

    @Override
    public List<PlacedFeatureInfo> getPlacedFeaturesByTag(TagKey<PlacedFeature> tag) {
        return snapshot.get().placedFeaturesForTag(tag);
    }

    @Override
    public Optional<StructurePlacementInfo> getStructurePlacement(ResourceKey<Structure> structure) {
        return snapshot.get().structures().stream()
                .filter(info -> info.key().equals(structure))
                .findFirst();
    }

    @Override public List<StructurePlacementInfo> getAllStructures() { return snapshot.get().structures(); }

    @Override
    public List<StructurePlacementInfo> getStructuresByTag(TagKey<Structure> tag) {
        return snapshot.get().structuresForTag(tag);
    }

    @Override
    public List<MobSpawnInfo> getMobSpawnsForBiome(ResourceKey<Biome> biome) {
        return snapshot.get().mobsForBiome(biome);
    }

    @Override
    public List<MobSpawnInfo> getMobsByCategory(MobCategory category) {
        return snapshot.get().mobsForCategory(category);
    }

    @Override
    public Optional<DensityFunction> getVanillaDensityFunction(ResourceKey<DensityFunction> key) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        return server.registryAccess().lookupOrThrow(Registries.DENSITY_FUNCTION)
                .get(key).map(net.minecraft.core.Holder::value);
    }

    @Override
    public Optional<NoiseGeneratorSettings> getVanillaNoiseSettings(ResourceKey<NoiseGeneratorSettings> key) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        return server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS)
                .get(key).map(net.minecraft.core.Holder::value);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Read from the snapshot's {@code LevelStem} scan, so it is available before any
     * {@code ServerLevel} exists (the biome-modifier pass runs earlier than level creation).
     */
    @Override
    public Optional<DimensionProfile> getDimensionProfile(ResourceKey<Level> dimension) {
        return snapshot.get().dimensionProfile(dimension);
    }

    /**
     * Per-dimension snapshot. Filters the global feature list down to entries whose Y
     * range overlaps the dimension's build height, and pulls the per-dim VerticalRange
     * override (set by {@link VanillaRuleSnapshot#placedFeatureRangeInDimension}) when available.
     * Structures pass through unchanged; mob spawn entries are absent here (callers should
     * use {@link #getMobSpawnsForBiome} for per-biome spawn data).
     *
     * <p>This view is purely diagnostic — it's the input data {@code IsekaiRemap} would
     * see for that dimension, not the post-modifier result. Use the corresponding biome
     * modifier output to inspect what actually generates.
     */
    @Override
    public WorldshapeSnapshot getSnapshot(ResourceKey<Level> dimension) {
        VanillaRuleSnapshot snap = snapshot.get();
        List<PlacedFeatureInfo> dimFeatures = snap.placedFeatures().stream().flatMap(info -> {
            var override = snap.placedFeatureRangeInDimension(info.key(), dimension);
            if (override.isEmpty()) return java.util.stream.Stream.of(info);
            // A stored FALLBACK means "not representable in this dimension" — don't surface
            // the overworld sentinel as if it were a real range.
            if (override.get() == HeightProviderExtraction.FALLBACK_RANGE) {
                return java.util.stream.Stream.empty();
            }
            return java.util.stream.Stream.of(
                    new PlacedFeatureInfo(info.key(), override.get(), info.count(), info.biomes()));
        }).toList();
        return new WorldshapeSnapshot(dimension, dimFeatures, snap.structures(), List.of());
    }

    @Override
    public Set<ResourceKey<Level>> getDimensionsWithWorldshape() {
        // Delegate to the remap-side registry, which is the canonical source for
        // 'which dimensions have a declaration'. IsekaiRemapImpl tracks single + layered.
        return com.kuronami.isekaiapi.api.Isekai.remap().getDeclaredDimensions();
    }
}
