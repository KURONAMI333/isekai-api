package com.kuronami.isekaiapi.impl;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.ApiStatus;

/**
 * Resolves which {@link Level} a {@link BiomeSource} belongs to. Used by
 * {@code StructureFindValidGenerationPointMixin} to scope a worldshape's structure
 * predicate to the dimension a structure is actually generating in, rather than applying
 * every declared dimension's predicate (which would over-suppress structures in other
 * dimensions that reuse the same biomes).
 *
 * <p>Implementation: walks {@code server.getAllLevels()} and matches the BiomeSource
 * via reference equality against each ServerLevel's
 * {@code chunkSource.getGenerator().getBiomeSource()}. Returns {@code null} when no
 * match (server not running yet, transient fake level, or off-main-thread before worlds
 * are populated) — callers treat null as "don't apply any predicate."
 *
 * <p>Cost: O(N) per call where N is the number of loaded dimensions (typically 3 +
 * modded dims). Called once per valid structure-placement point. The BiomeSource identity
 * is stable per server start, so a cache could trim it to O(1) if profiling ever warrants.
 */
@ApiStatus.Internal
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
