package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.IsekaiQuery;
import com.kuronami.isekaiapi.api.query.MobSpawnInfo;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.query.StructurePlacementInfo;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.query.WorldshapeSnapshot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * v0.2 implementation backed by {@link VanillaRuleSnapshot}. The lifecycle hook
 * ({@link com.kuronami.isekaiapi.lifecycle.IsekaiLifecycle#onServerAboutToStart})
 * scans the registries and calls {@link #setSnapshot} to populate the cache;
 * queries then read from it in O(1).
 *
 * <p>Before the lifecycle has fired (cold start), the snapshot is
 * {@link VanillaRuleSnapshot#EMPTY} and all queries return empty results — the
 * same behavior as the v0.1 stub.
 */
public final class IsekaiQueryImpl implements IsekaiQuery {

    private final AtomicReference<VanillaRuleSnapshot> snapshot =
            new AtomicReference<>(VanillaRuleSnapshot.EMPTY);

    public void setSnapshot(VanillaRuleSnapshot s) {
        var prev = snapshot.getAndSet(s);
        IsekaiApi.LOGGER.debug("[Isekai] Vanilla rule snapshot replaced: prev empty={}, new empty={}",
                prev.isEmpty(), s.isEmpty());
    }

    /**
     * Direct read of the currently-published snapshot. Internal — exposed for
     * {@link com.kuronami.isekaiapi.biomemodifier.ApplyWorldshapeBiomeModifier} which needs
     * raw access to per-feature VerticalRanges + world bounds for strategy remapping.
     */
    public VanillaRuleSnapshot getSnapshot() {
        return snapshot.get();
    }

    @Override
    public Optional<VerticalRange> getOreVerticalRange(ResourceKey<PlacedFeature> ore) {
        return snapshot.get().ores().stream()
                .filter(info -> info.key().equals(ore))
                .findFirst()
                .map(PlacedFeatureInfo::range);
    }

    @Override public List<PlacedFeatureInfo> getAllOres() { return snapshot.get().ores(); }

    @Override
    public List<PlacedFeatureInfo> getOresByTag(TagKey<PlacedFeature> tag) {
        return snapshot.get().oresForTag(tag);
    }

    @Override
    public Optional<StructurePlacementInfo> getStructurePlacement(ResourceKey<Structure> structure) {
        return snapshot.get().structures().stream()
                .filter(info -> info.key().equals(structure))
                .findFirst();
    }

    @Override public List<StructurePlacementInfo> getAllStructures() { return snapshot.get().structures(); }

    @Override
    public List<MobSpawnInfo> getMobSpawnsForBiome(ResourceKey<Biome> biome) {
        return snapshot.get().mobsForBiome(biome);
    }

    @Override
    public List<MobSpawnInfo> getMobsByCategory(MobCategory category) {
        return snapshot.get().mobsForCategory(category);
    }

    @Override public Optional<DensityFunction> getVanillaDensityFunction(ResourceKey<DensityFunction> key) { return Optional.empty(); }
    @Override public Optional<NoiseGeneratorSettings> getVanillaNoiseSettings(ResourceKey<NoiseGeneratorSettings> key) { return Optional.empty(); }

    @Override
    public WorldshapeSnapshot getSnapshot(ResourceKey<Level> dimension) {
        // The per-dimension WorldshapeSnapshot is a different concept from the global
        // VanillaRuleSnapshot — it's the consumer's declared remap applied to vanilla.
        // Lands in v0.3 alongside the biome modifier generator.
        return new WorldshapeSnapshot(dimension, List.of(), List.of(), List.of());
    }

    @Override public Set<ResourceKey<Level>> getDimensionsWithWorldshape() { return Set.of(); }
}
