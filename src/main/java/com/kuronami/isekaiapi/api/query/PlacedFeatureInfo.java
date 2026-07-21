package com.kuronami.isekaiapi.api.query;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.Set;

/**
 * A scanned placed feature: its registry key, resolved vertical {@link VerticalRange},
 * placement {@code count}, and the biome tags it is placed in.
 * @since 1.0.0
 */
public record PlacedFeatureInfo(
        ResourceKey<PlacedFeature> key,
        VerticalRange range,
        int count,
        Set<TagKey<Biome>> biomes
) {
    public PlacedFeatureInfo {
        biomes = Set.copyOf(biomes);
    }
}
