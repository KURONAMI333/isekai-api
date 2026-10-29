package com.kuronami.isekaiapi.impl;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Maps a generation-stage {@link ChunkAccess} to the dimension that owns it.
 *
 * <p>NeoForge 1.21.1 hands a {@code Structure.GenerationContext} its chunk as the
 * {@code LevelHeightAccessor} during ordinary chunk generation, and a {@code ChunkAccess}
 * exposes no dimension of its own. The chunk does know it at construction time: {@code ProtoChunk}
 * receives the owning {@code ServerLevel} as its {@code LevelHeightAccessor}, and
 * {@code ProtoChunkDimensionMixin} records that pairing here. {@link DimensionResolver} then
 * reads it back, so generation-time resolution is exact even when two dimensions share one
 * {@code BiomeSource} instance.
 *
 * <p>The map is weak-keyed (chunks leave it when the server drops every reference) and
 * identity-keyed ({@link ChunkAccess} overrides neither {@code equals} nor {@code hashCode}),
 * so two chunks at the same position in different dimensions never alias.
 */
@ApiStatus.Internal
public final class ChunkDimensionIndex {

    private static final Map<ChunkAccess, ResourceKey<Level>> DIMENSIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ChunkDimensionIndex() {}

    public static void remember(ChunkAccess chunk, ResourceKey<Level> dimension) {
        if (chunk == null || dimension == null) return;
        DIMENSIONS.put(chunk, dimension);
    }

    /**
     * Read the dimension recorded for {@code accessor}, or {@code null} when it has none.
     *
     * <p>An {@link ImposterProtoChunk} is itself a {@code ChunkAccess}, but it wraps an already
     * generated {@code LevelChunk}: prefer the wrapper's entry when present, then the imposter's
     * own. Any other object (a fake level, a {@code WorldGenLevel}, a chunk from before the
     * server started) yields {@code null} and the caller falls back to its other paths.
     */
    public static ResourceKey<Level> dimensionOf(Object accessor) {
        if (accessor instanceof ImposterProtoChunk imposter) {
            ResourceKey<Level> wrapped = DIMENSIONS.get(imposter.getWrapped());
            if (wrapped != null) return wrapped;
            return DIMENSIONS.get(imposter);
        }
        if (accessor instanceof ChunkAccess chunk) {
            return DIMENSIONS.get(chunk);
        }
        return null;
    }

    /** Drop every entry. Called on server stop so one world's chunks never leak into the next. */
    public static void clear() {
        DIMENSIONS.clear();
    }
}
