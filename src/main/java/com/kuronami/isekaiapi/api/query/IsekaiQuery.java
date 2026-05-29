package com.kuronami.isekaiapi.api.query;

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

/**
 * Read-only inspection of vanilla + modded worldgen rules.
 * Implementation caches snapshot at {@code ServerAboutToStartEvent}; all queries are O(1) after warmup.
 * Returned collections are immutable.
 */
public interface IsekaiQuery {

    // placed features (every PlacedFeature in the registry — ores, trees, lakes, etc.)
    Optional<VerticalRange> getPlacedFeatureVerticalRange(ResourceKey<PlacedFeature> feature);
    List<PlacedFeatureInfo> getAllPlacedFeatures();
    List<PlacedFeatureInfo> getPlacedFeaturesByTag(TagKey<PlacedFeature> tag);

    /**
     * Per-dimension VerticalRange resolution. Features with {@code VerticalAnchor.AboveBottom}
     * or {@code BelowTop} resolve against the named dimension's build height instead of the
     * overworld defaults. Features with absolute anchors return the same Y regardless of
     * dimension. Returns {@code Optional.empty()} if the feature wasn't scanned or the
     * dimension wasn't loaded at scan time.
     */
    Optional<VerticalRange> getPlacedFeatureVerticalRangeInDimension(ResourceKey<PlacedFeature> feature,
                                                                      ResourceKey<Level> dimension);

    // structures
    Optional<StructurePlacementInfo> getStructurePlacement(ResourceKey<Structure> structure);
    List<StructurePlacementInfo> getAllStructures();
    List<StructurePlacementInfo> getStructuresByTag(TagKey<Structure> tag);

    // mob spawns
    List<MobSpawnInfo> getMobSpawnsForBiome(ResourceKey<Biome> biome);
    List<MobSpawnInfo> getMobsByCategory(MobCategory category);

    // density / noise
    Optional<DensityFunction> getVanillaDensityFunction(ResourceKey<DensityFunction> key);
    Optional<NoiseGeneratorSettings> getVanillaNoiseSettings(ResourceKey<NoiseGeneratorSettings> key);

    // dimension snapshots
    WorldshapeSnapshot getSnapshot(ResourceKey<Level> dimension);
    Set<ResourceKey<Level>> getDimensionsWithWorldshape();
}
