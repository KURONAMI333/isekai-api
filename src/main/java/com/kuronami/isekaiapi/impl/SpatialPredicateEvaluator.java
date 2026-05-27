package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Evaluate a {@link SpatialPredicate} against a (position, world context) pair. Used by
 * the structure-placement Mixin to gate structure spawning at consumer-declared spatial
 * conditions.
 *
 * <p>At structure placement time (during {@code Structure.findValidGenerationPoint}),
 * chunks are not yet fully populated — we have a {@link ChunkGenerator}, a
 * {@link BiomeSource}, and the {@link RandomState}-derived sampler. Every variant has a
 * concrete evaluator at this stage:
 * <ul>
 *   <li>{@code Always} / {@code Never} / {@code YInRange} — trivial</li>
 *   <li>{@code And} / {@code Or} / {@code Not} — recursive composition</li>
 *   <li>{@code SolidFloor} / {@code SolidCeiling} — sample the NoiseColumn at (x,z)</li>
 *   <li>{@code InFluid} — sample the NoiseColumn at (x,y,z), check getFluidState</li>
 *   <li>{@code NearBlock} — sample columns in a radius around (x,z), check Y window</li>
 *   <li>{@code NearBiome} — sample BiomeSource at quart-positions in a radius</li>
 *   <li>{@code TerrainSlope} — sample base height at 4 cardinal neighbours, derive slope</li>
 * </ul>
 *
 * <p>Per-variant cost: trivial (Y/boolean) → column-local (clearance, in-fluid, slope)
 * → multi-column scan (NearBlock, NearBiome). The expensive ones grow as {@code (2r+1)^2}
 * columns. Keep {@code maxDistance} small (≤ 8) in production to avoid per-chunk-gen
 * hitch.
 */
public final class SpatialPredicateEvaluator {

    private SpatialPredicateEvaluator() {}

    public static boolean evaluate(SpatialPredicate predicate, BlockPos pos, Context ctx) {
        if (predicate instanceof SpatialPredicate.Always) return true;
        if (predicate instanceof SpatialPredicate.Never) return false;
        if (predicate instanceof SpatialPredicate.YInRange y) {
            return pos.getY() >= y.min() && pos.getY() <= y.max();
        }
        if (predicate instanceof SpatialPredicate.And and) {
            for (var child : and.all()) if (!evaluate(child, pos, ctx)) return false;
            return true;
        }
        if (predicate instanceof SpatialPredicate.Or or) {
            for (var child : or.any()) if (evaluate(child, pos, ctx)) return true;
            return false;
        }
        if (predicate instanceof SpatialPredicate.Not not) {
            return !evaluate(not.inner(), pos, ctx);
        }
        if (predicate instanceof SpatialPredicate.SolidFloor sf) {
            return checkClearance(pos, ctx, sf.minClearance(), Direction.DOWN);
        }
        if (predicate instanceof SpatialPredicate.SolidCeiling sc) {
            return checkClearance(pos, ctx, sc.minClearance(), Direction.UP);
        }
        if (predicate instanceof SpatialPredicate.InFluid in) {
            return checkInFluid(pos, ctx, in);
        }
        if (predicate instanceof SpatialPredicate.NearBlock nb) {
            return checkNearBlock(pos, ctx, nb);
        }
        if (predicate instanceof SpatialPredicate.NearBiome nb) {
            return checkNearBiome(pos, ctx, nb);
        }
        if (predicate instanceof SpatialPredicate.TerrainSlope ts) {
            return checkTerrainSlope(pos, ctx, ts);
        }
        IsekaiApi.LOGGER.debug("[Isekai] SpatialPredicate.{} not handled; defaulting to allow",
                predicate.getClass().getSimpleName());
        return true;
    }

    // ------------------------------------------------------------------
    // Column-local evaluators
    // ------------------------------------------------------------------

    private static boolean checkClearance(BlockPos pos, Context ctx, int minClearance, Direction dir) {
        try {
            NoiseColumn column = ctx.chunkGenerator.getBaseColumn(
                    pos.getX(), pos.getZ(), ctx.heightAccessor, ctx.randomState);
            int targetY = pos.getY();
            if (dir == Direction.DOWN) {
                if (column.getBlock(targetY - 1).isAir()) return false;
                for (int i = 1; i <= minClearance; i++) {
                    if (!column.getBlock(targetY + i).isAir()) return false;
                }
                return true;
            } else {
                if (column.getBlock(targetY + 1).isAir()) return false;
                for (int i = 1; i <= minClearance; i++) {
                    if (!column.getBlock(targetY - i).isAir()) return false;
                }
                return true;
            }
        } catch (Exception e) {
            IsekaiApi.LOGGER.debug("[Isekai] clearance check failed; allowing: {}", e.getMessage());
            return true;
        }
    }

    private static boolean checkInFluid(BlockPos pos, Context ctx, SpatialPredicate.InFluid in) {
        try {
            NoiseColumn column = ctx.chunkGenerator.getBaseColumn(
                    pos.getX(), pos.getZ(), ctx.heightAccessor, ctx.randomState);
            BlockState state = column.getBlock(pos.getY());
            return state.getFluidState().getType() == in.fluid();
        } catch (Exception e) {
            IsekaiApi.LOGGER.debug("[Isekai] in_fluid check failed; allowing: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Approximate terrain slope at {@code pos} by sampling the heightmap height at
     * (x±1, z) and (x, z±1) via {@link ChunkGenerator#getBaseHeight} and computing the
     * max absolute Y delta. Slope = deltaY / 1 block horizontal; 1.0 = 45°. The check
     * returns true when {@code min ≤ slope ≤ max}.
     */
    private static boolean checkTerrainSlope(BlockPos pos, Context ctx, SpatialPredicate.TerrainSlope ts) {
        try {
            int hCenter = ctx.chunkGenerator.getBaseHeight(pos.getX(), pos.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor, ctx.randomState);
            int hN = ctx.chunkGenerator.getBaseHeight(pos.getX(), pos.getZ() - 1,
                    Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor, ctx.randomState);
            int hS = ctx.chunkGenerator.getBaseHeight(pos.getX(), pos.getZ() + 1,
                    Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor, ctx.randomState);
            int hE = ctx.chunkGenerator.getBaseHeight(pos.getX() + 1, pos.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor, ctx.randomState);
            int hW = ctx.chunkGenerator.getBaseHeight(pos.getX() - 1, pos.getZ(),
                    Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor, ctx.randomState);
            int maxDelta = Math.max(Math.max(Math.abs(hN - hCenter), Math.abs(hS - hCenter)),
                    Math.max(Math.abs(hE - hCenter), Math.abs(hW - hCenter)));
            double slope = maxDelta;  // delta per 1 block = the slope value
            return slope >= ts.minSlope() && slope <= ts.maxSlope();
        } catch (Exception e) {
            IsekaiApi.LOGGER.debug("[Isekai] terrain_slope check failed; allowing: {}", e.getMessage());
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Multi-column / multi-position evaluators
    // ------------------------------------------------------------------

    /**
     * Scan a (2r+1)^2 grid of columns centred on {@code pos.x, pos.z}; for each column,
     * check a vertical window of ±maxDistance around {@code pos.y} for a block matching
     * the HolderSet {@code targets}. Stops at first match.
     */
    private static boolean checkNearBlock(BlockPos pos, Context ctx, SpatialPredicate.NearBlock nb) {
        try {
            int r = nb.maxDistance();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    NoiseColumn column = ctx.chunkGenerator.getBaseColumn(
                            pos.getX() + dx, pos.getZ() + dz, ctx.heightAccessor, ctx.randomState);
                    int yMin = Math.max(ctx.heightAccessor.getMinBuildHeight(), pos.getY() - r);
                    int yMax = Math.min(ctx.heightAccessor.getMaxBuildHeight() - 1, pos.getY() + r);
                    for (int y = yMin; y <= yMax; y++) {
                        BlockState state = column.getBlock(y);
                        if (nb.targets().contains(state.getBlockHolder())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            IsekaiApi.LOGGER.debug("[Isekai] near_block check failed; allowing: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Scan a (2r+1)^2 grid of quart-positions around {@code pos}; sample the biome at
     * each and check whether any match {@code nb.biome}. {@code BiomeSource} works in
     * quart (4-block) coordinates, so we divide the block radius by 4.
     */
    private static boolean checkNearBiome(BlockPos pos, Context ctx, SpatialPredicate.NearBiome nb) {
        try {
            BiomeSource biomeSource = ctx.biomeSource;
            if (biomeSource == null) return true;
            var sampler = ctx.randomState.sampler();
            int qx = pos.getX() >> 2;
            int qy = pos.getY() >> 2;
            int qz = pos.getZ() >> 2;
            int rq = Math.max(1, nb.maxDistance() >> 2);
            for (int dqx = -rq; dqx <= rq; dqx++) {
                for (int dqz = -rq; dqz <= rq; dqz++) {
                    Holder<Biome> here = biomeSource.getNoiseBiome(qx + dqx, qy, qz + dqz, sampler);
                    if (here.is(nb.biome())) return true;
                }
            }
            return false;
        } catch (Exception e) {
            IsekaiApi.LOGGER.debug("[Isekai] near_biome check failed; allowing: {}", e.getMessage());
            return true;
        }
    }

    private enum Direction { DOWN, UP }

    /** Context bundle for predicate evaluation at structure placement time. */
    public record Context(
            ChunkGenerator chunkGenerator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            BiomeSource biomeSource
    ) {}
}
