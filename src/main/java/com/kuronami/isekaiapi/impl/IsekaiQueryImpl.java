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

/**
 * v0.1 no-op stub. Vanilla rule scanner lands in v0.2.
 */
public final class IsekaiQueryImpl implements IsekaiQuery {

    @Override public Optional<VerticalRange> getOreVerticalRange(ResourceKey<PlacedFeature> ore) {
        IsekaiApi.LOGGER.debug("[Isekai v0.1 stub] getOreVerticalRange({}) -> empty", ore);
        return Optional.empty();
    }
    @Override public List<PlacedFeatureInfo> getAllOres() { return List.of(); }
    @Override public List<PlacedFeatureInfo> getOresByTag(TagKey<PlacedFeature> tag) { return List.of(); }

    @Override public Optional<StructurePlacementInfo> getStructurePlacement(ResourceKey<Structure> structure) { return Optional.empty(); }
    @Override public List<StructurePlacementInfo> getAllStructures() { return List.of(); }

    @Override public List<MobSpawnInfo> getMobSpawnsForBiome(ResourceKey<Biome> biome) { return List.of(); }
    @Override public List<MobSpawnInfo> getMobsByCategory(MobCategory category) { return List.of(); }

    @Override public Optional<DensityFunction> getVanillaDensityFunction(ResourceKey<DensityFunction> key) { return Optional.empty(); }
    @Override public Optional<NoiseGeneratorSettings> getVanillaNoiseSettings(ResourceKey<NoiseGeneratorSettings> key) { return Optional.empty(); }

    @Override public WorldshapeSnapshot getSnapshot(ResourceKey<Level> dimension) {
        return WorldshapeSnapshot.of(dimension, List.of(), List.of(), List.of());
    }
    @Override public Set<ResourceKey<Level>> getDimensionsWithWorldshape() { return Set.of(); }
}
