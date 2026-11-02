package com.kuronami.isekaiapi.structure;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.ApiStatus;

/**
 * The five columns the placement gates sample: the chunk middle and the four corners of the
 * footprint.
 *
 * <p>This is the single definition of "the ground under this structure". The gates in
 * {@link GroundedTemplateStructure} and the report in
 * {@code com.kuronami.isekaiapi.survey.FootprintCheck} both read it, so an author who inspects a
 * chunk sees the same heights the gates judged. The corners are inset by half the footprint, not by
 * its full width, so a 9x9 template is judged on x-4..x+4 — the piece's own box overhangs the
 * sampled columns by a block at most.
 *
 * @param centerX    block X of the chunk middle
 * @param centerZ    block Z of the chunk middle
 * @param halfX      half the rotated footprint width
 * @param halfZ      half the rotated footprint depth
 * @param center     surface height at the chunk middle
 * @param northWest  surface height at (centerX - halfX, centerZ - halfZ)
 * @param northEast  surface height at (centerX + halfX, centerZ - halfZ)
 * @param southWest  surface height at (centerX - halfX, centerZ + halfZ)
 * @param southEast  surface height at (centerX + halfX, centerZ + halfZ)
 */
@ApiStatus.Internal
public record Footprint(int centerX, int centerZ, int halfX, int halfZ, int center,
                        int northWest, int northEast, int southWest, int southEast) {

    /** The lowest sampled surface — the column that decides {@code clearance_above_fluid}. */
    public int min() {
        return Math.min(center, Math.min(Math.min(northWest, northEast), Math.min(southWest, southEast)));
    }

    /** The highest sampled surface. */
    public int max() {
        return Math.max(center, Math.max(Math.max(northWest, northEast), Math.max(southWest, southEast)));
    }

    /** Height spread across the samples — what {@code max_slope} limits. */
    public int spread() {
        return max() - min();
    }

    /**
     * Samples the footprint of a template whose rotated size is {@code rotatedSize} in the chunk
     * {@code chunkPos}.
     */
    public static Footprint sample(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                                   RandomState randomState, ChunkPos chunkPos, Vec3i rotatedSize) {
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int halfX = rotatedSize.getX() / 2;
        int halfZ = rotatedSize.getZ() / 2;
        return new Footprint(x, z, halfX, halfZ,
                height(generator, heightAccessor, randomState, x, z),
                height(generator, heightAccessor, randomState, x - halfX, z - halfZ),
                height(generator, heightAccessor, randomState, x + halfX, z - halfZ),
                height(generator, heightAccessor, randomState, x - halfX, z + halfZ),
                height(generator, heightAccessor, randomState, x + halfX, z + halfZ));
    }

    /** One surface sample at the same heightmap the gates read. */
    public static int height(ChunkGenerator generator, LevelHeightAccessor heightAccessor,
                             RandomState randomState, int x, int z) {
        return generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor,
                randomState);
    }
}
