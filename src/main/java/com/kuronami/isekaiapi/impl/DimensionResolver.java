package com.kuronami.isekaiapi.impl;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.ApiStatus;

/**
 * Resolves which {@link Level} a structure is generating in, so a worldshape's structure
 * predicate and exclusions apply only to their own dimension instead of every dimension
 * that reuses the same biomes. Used by {@code StructureFindValidGenerationPointMixin}.
 *
 * <p>Two paths, in order:
 * <ol>
 *   <li>{@link #resolve(net.minecraft.world.level.LevelHeightAccessor, BiomeSource)} — when the
 *       accessor names its dimension: a generation-stage {@code ChunkAccess} is looked up in
 *       {@link ChunkDimensionIndex} (recorded when its {@code ProtoChunk} was built with a
 *       {@code ServerLevel}), and a {@code WorldGenLevel} or {@code ServerLevel} reports its own
 *       key. Exact for vanilla and modded dimensions alike, including dimensions that share one
 *       {@code BiomeSource} instance.</li>
 *   <li>{@link #resolveByBiomeSource(BiomeSource)} — only when the accessor names no dimension
 *       (absent from the index, a transient fake level, off-main-thread before worlds are
 *       populated). Matches the {@link BiomeSource} by reference against each level's generator.</li>
 * </ol>
 *
 * <p>{@code resolveByBiomeSource} walks {@code server.getAllLevels()} and matches the
 * {@link BiomeSource} by reference against each level's generator. It returns {@code null}
 * when nothing matches — callers treat null as "don't apply any predicate". Its known hole:
 * two dimensions sharing one {@code BiomeSource} instance are indistinguishable, so this
 * last-resort path is only as exact as that assumption. Decorated features do not use
 * this class at all — {@code ChunkGeneratorDecorationMixin} reads the dimension off the
 * {@code WorldGenLevel} it is handed, exactly, for every dimension.
 *
 * <p>Cost: O(1) for the index lookup; O(N) for the biome-source fallback where N is the
 * number of loaded dimensions (typically 3 + modded dims). Called once per valid
 * structure-placement point. The index is weak-keyed so chunks leave it when the server
 * drops them; {@link ChunkDimensionIndex#clear()} on server stop is belt and braces.
 */
@ApiStatus.Internal
public final class DimensionResolver {

    private DimensionResolver() {}

    /**
     * Resolve the dimension from the accessor, in order: the {@link ChunkDimensionIndex}
     * entry recorded for a generation-stage {@code ProtoChunk}, then a real level
     * ({@link net.minecraft.world.level.WorldGenLevel} or
     * {@link net.minecraft.server.level.ServerLevel}, used by {@code /locate} and the
     * {@code /place} commands), and finally the biome-source match.
     *
     * <p>During ordinary chunk generation NeoForge passes the {@code ChunkAccess}
     * ({@code ProtoChunk}) as the context's height accessor. That chunk is in the index
     * because its constructor received the owning {@code ServerLevel}, so the dimension is
     * exact even when two dimensions share one {@code BiomeSource}. Only an accessor absent
     * from the index falls through to {@link #resolveByBiomeSource(BiomeSource)}.
     */
    public static ResourceKey<Level> resolve(net.minecraft.world.level.LevelHeightAccessor accessor,
                                              BiomeSource biomeSource) {
        ResourceKey<Level> indexed = ChunkDimensionIndex.dimensionOf(accessor);
        if (indexed != null) {
            return indexed;
        }
        if (accessor instanceof net.minecraft.world.level.WorldGenLevel level) {
            // ServerLevel implements WorldGenLevel, so this branch covers it too.
            return level.getLevel().dimension();
        }
        return resolveByBiomeSource(biomeSource);
    }

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
