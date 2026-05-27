package com.kuronami.isekaiapi.impl;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Resolves which {@link Level} a {@link BiomeSource} belongs to. Used by the
 * structure-placement Mixin chain to scope {@code structure_strategy} per-dimension
 * instead of applying the first declared factor uniformly across every dim.
 *
 * <p>Implementation: walks {@code server.getAllLevels()} and matches the BiomeSource
 * via reference equality against each ServerLevel's
 * {@code chunkSource.getGenerator().getBiomeSource()}. Returns {@code null} when no
 * match (which means the server isn't running yet, the chunk-gen is for a transient
 * fake level, or we're being asked off the main thread before worlds are populated).
 *
 * <p>Cost: O(N) per call where N is the number of loaded dimensions (typically 3 for
 * vanilla overworld/nether/end, plus modded dims). spacing()/separation() are called
 * once per (chunk × structure-placement) pair so this cost is acceptable. A cache
 * could trim it to O(1) but the BiomeSource identity is stable per server start.
 */
public final class DimensionResolver {

    private DimensionResolver() {}

    public static ResourceKey<Level> resolveByBiomeSource(BiomeSource biomeSource) {
        if (biomeSource == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        for (var level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator().getBiomeSource() == biomeSource) {
                return level.dimension();
            }
        }
        return null;
    }
}
