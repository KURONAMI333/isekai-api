package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.biomesource.ClimateZonesBiomeSource;
import com.kuronami.isekaiapi.biomesource.OverrideBiomeSource;
import com.kuronami.isekaiapi.biomesource.RuleBiomeSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.jetbrains.annotations.ApiStatus;

/**
 * Decides which nether and end definitions Isekai vouches for, so vanilla does not flag the world
 * as using experimental settings.
 *
 * <p>Vanilla's {@code WorldDimensions.isStableNether}/{@code isStableEnd} accept only the stock
 * biome source and the stock noise settings key. A mod that replaces the nether with an
 * {@code isekai_api:} biome source therefore triggers the experimental-settings prompt on world
 * creation and first open, even when it ships the dimension inside its own jar. This check
 * accepts a nether or end that keeps the stock dimension slot and dimension type key, generates
 * with a noise generator (any noise settings), and takes its biomes from an Isekai source.
 *
 * <p>Everything else keeps vanilla's verdict: the overworld (vanilla already accepts a wrapped
 * overworld), extra dimensions, a world whose dimension count is not three, a nether or end whose
 * dimension type key was swapped, a non-Isekai biome source, and the lifecycle that the worldgen
 * registries carry for entries a user datapack supplies.
 */
@ApiStatus.Internal
public final class IsekaiDimensionStability {

    private IsekaiDimensionStability() {}

    /** True when {@code stem} is a nether or end that differs from vanilla only through Isekai. */
    public static boolean isVouchedReplacement(ResourceKey<LevelStem> key, LevelStem stem) {
        if (key == LevelStem.NETHER) {
            if (!stem.type().is(BuiltinDimensionTypes.NETHER)) return false;
        } else if (key == LevelStem.END) {
            if (!stem.type().is(BuiltinDimensionTypes.END)) return false;
        } else {
            return false;
        }
        return stem.generator() instanceof NoiseBasedChunkGenerator
                && isIsekaiSource(stem.generator().getBiomeSource());
    }

    private static boolean isIsekaiSource(BiomeSource source) {
        return source instanceof OverrideBiomeSource
                || source instanceof RuleBiomeSource
                || source instanceof ClimateZonesBiomeSource;
    }
}
