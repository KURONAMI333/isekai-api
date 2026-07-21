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
 * @since 1.0.0
 */
public interface IsekaiQuery {

    // placed features (every PlacedFeature in the registry — ores, trees, lakes, etc.)

    /** Overworld-resolved Y range of one placed feature, or empty if it wasn't scanned. @since 1.0.0 */
    Optional<VerticalRange> getPlacedFeatureVerticalRange(ResourceKey<PlacedFeature> feature);

    /** Every scanned placed feature (ores, trees, lakes, …) with its resolved Y range. @since 1.0.0 */
    List<PlacedFeatureInfo> getAllPlacedFeatures();

    /** Scanned placed features carrying the given tag. @since 1.0.0 */
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

    /** Placement info (spacing, biomes, generation step) for one structure, or empty if not scanned. @since 1.0.0 */
    Optional<StructurePlacementInfo> getStructurePlacement(ResourceKey<Structure> structure);

    /** Every scanned structure with its placement info. @since 1.0.0 */
    List<StructurePlacementInfo> getAllStructures();

    /** Scanned structures carrying the given tag. @since 1.0.0 */
    List<StructurePlacementInfo> getStructuresByTag(TagKey<Structure> tag);

    // mob spawns

    /** Mob spawn entries a biome contributes, across all categories. @since 1.0.0 */
    List<MobSpawnInfo> getMobSpawnsForBiome(ResourceKey<Biome> biome);

    /** Mob spawn entries of one category, across all scanned biomes. @since 1.0.0 */
    List<MobSpawnInfo> getMobsByCategory(MobCategory category);

    // density / noise

    /** A density function from the server's worldgen registry, or empty if absent. @since 1.0.0 */
    Optional<DensityFunction> getVanillaDensityFunction(ResourceKey<DensityFunction> key);

    /** A {@code noise_settings} entry from the server's worldgen registry, or empty if absent. @since 1.0.0 */
    Optional<NoiseGeneratorSettings> getVanillaNoiseSettings(ResourceKey<NoiseGeneratorSettings> key);

    // dimension snapshots

    /** The worldshape snapshot for a dimension; an empty snapshot if none is declared. @since 1.0.0 */
    WorldshapeSnapshot getSnapshot(ResourceKey<Level> dimension);

    /** Dimensions that currently have a declared worldshape. @since 1.0.0 */
    Set<ResourceKey<Level>> getDimensionsWithWorldshape();
}
