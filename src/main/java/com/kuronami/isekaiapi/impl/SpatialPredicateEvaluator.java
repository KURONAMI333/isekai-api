package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.predicate.EvaluationContext;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.ApiStatus;

/**
 * Evaluate a {@link SpatialPredicate} at structure-placement time. Used by the
 * structure-placement Mixin to gate structure spawning at consumer-declared spatial conditions.
 *
 * <p>During {@code Structure.findValidGenerationPoint} chunks are not yet populated — we have a
 * {@link ChunkGenerator}, a {@link BiomeSource}, and the {@link RandomState}-derived sampler.
 * This class adapts those into the {@link EvaluationContext} seam so each predicate variant
 * evaluates itself ({@link SpatialPredicate#test(EvaluationContext)}) with no type-dispatch here.
 * Terrain queries sample the generator's noise columns; the feature-decoration counterpart
 * ({@link com.kuronami.isekaiapi.placementmodifier.SpatialPredicatePlacementModifier}) reads
 * placed blocks from a {@code WorldGenLevel} instead.
 *
 * <p>Per-query cost: trivial (Y/boolean) → column-local (clearance, in-fluid, slope) →
 * multi-column scan (NearBlock, NearBiome). The expensive ones grow as {@code (2r+1)^2} columns;
 * keep {@code maxDistance} small (≤ 8) in production to avoid per-chunk-gen hitch.
 */
@ApiStatus.Internal
public final class SpatialPredicateEvaluator {

    private SpatialPredicateEvaluator() {}

    public static boolean evaluate(SpatialPredicate predicate, BlockPos pos, Context ctx) {
        return predicate.test(new StructureContext(pos, ctx));
    }

    private enum Direction { DOWN, UP }

    /** Context bundle for predicate evaluation at structure placement time. */
    public record Context(
            ChunkGenerator chunkGenerator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState,
            BiomeSource biomeSource
    ) {}

    /** Structure-placement {@link EvaluationContext}: answers terrain queries from noise columns. */
    private record StructureContext(BlockPos pos, Context ctx) implements EvaluationContext {

        @Override
        public boolean solidFloor(int minClearance) {
            return checkClearance(minClearance, Direction.DOWN);
        }

        @Override
        public boolean solidCeiling(int minClearance) {
            return checkClearance(minClearance, Direction.UP);
        }

        private boolean checkClearance(int minClearance, Direction dir) {
            try {
                NoiseColumn column = ctx.chunkGenerator().getBaseColumn(
                        pos.getX(), pos.getZ(), ctx.heightAccessor(), ctx.randomState());
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

        @Override
        public boolean inFluid(Fluid fluid) {
            try {
                NoiseColumn column = ctx.chunkGenerator().getBaseColumn(
                        pos.getX(), pos.getZ(), ctx.heightAccessor(), ctx.randomState());
                BlockState state = column.getBlock(pos.getY());
                return state.getFluidState().getType() == fluid;
            } catch (Exception e) {
                IsekaiApi.LOGGER.debug("[Isekai] in_fluid check failed; allowing: {}", e.getMessage());
                return true;
            }
        }

        /**
         * Scan a (2r+1)^2 grid of columns centred on {@code pos.x, pos.z}; for each column, check
         * a vertical window of ±maxDistance around {@code pos.y} for a block matching {@code targets}.
         * Stops at first match.
         */
        @Override
        public boolean nearBlock(HolderSet<Block> targets, int maxDistance) {
            try {
                int r = maxDistance;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        NoiseColumn column = ctx.chunkGenerator().getBaseColumn(
                                pos.getX() + dx, pos.getZ() + dz, ctx.heightAccessor(), ctx.randomState());
                        int yMin = Math.max(ctx.heightAccessor().getMinBuildHeight(), pos.getY() - r);
                        int yMax = Math.min(ctx.heightAccessor().getMaxBuildHeight() - 1, pos.getY() + r);
                        for (int y = yMin; y <= yMax; y++) {
                            BlockState state = column.getBlock(y);
                            if (targets.contains(state.getBlockHolder())) {
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
         * Scan a (2r+1)^2 grid of quart-positions around {@code pos}; sample the biome at each and
         * check whether any match {@code biome}. {@code BiomeSource} works in quart (4-block)
         * coordinates, so we divide the block radius by 4.
         */
        @Override
        public boolean nearBiome(ResourceKey<Biome> biome, int maxDistance) {
            try {
                BiomeSource biomeSource = ctx.biomeSource();
                if (biomeSource == null) return true;
                var sampler = ctx.randomState().sampler();
                int qx = pos.getX() >> 2;
                int qy = pos.getY() >> 2;
                int qz = pos.getZ() >> 2;
                // Round UP — consumer specifies block radius; (N + 3) >> 2 == ceil(N / 4) ensures
                // small radii (e.g. 7 blocks) still produce a ≥1 quart radius that fully covers them.
                int rq = Math.max(1, (maxDistance + 3) >> 2);
                for (int dqx = -rq; dqx <= rq; dqx++) {
                    for (int dqz = -rq; dqz <= rq; dqz++) {
                        Holder<Biome> here = biomeSource.getNoiseBiome(qx + dqx, qy, qz + dqz, sampler);
                        if (here.is(biome)) return true;
                    }
                }
                return false;
            } catch (Exception e) {
                IsekaiApi.LOGGER.debug("[Isekai] near_biome check failed; allowing: {}", e.getMessage());
                return true;
            }
        }

        /**
         * Approximate terrain slope at {@code pos} by sampling the heightmap height at (x±1, z)
         * and (x, z±1) via {@link ChunkGenerator#getBaseHeight} and computing the max absolute Y
         * delta. Slope = deltaY / 1 block horizontal; 1.0 = 45°.
         */
        @Override
        public boolean terrainSlope(double minSlope, double maxSlope) {
            try {
                int hCenter = ctx.chunkGenerator().getBaseHeight(pos.getX(), pos.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                int hN = ctx.chunkGenerator().getBaseHeight(pos.getX(), pos.getZ() - 1,
                        Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                int hS = ctx.chunkGenerator().getBaseHeight(pos.getX(), pos.getZ() + 1,
                        Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                int hE = ctx.chunkGenerator().getBaseHeight(pos.getX() + 1, pos.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                int hW = ctx.chunkGenerator().getBaseHeight(pos.getX() - 1, pos.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
                int maxDelta = Math.max(Math.max(Math.abs(hN - hCenter), Math.abs(hS - hCenter)),
                        Math.max(Math.abs(hE - hCenter), Math.abs(hW - hCenter)));
                double slope = maxDelta;  // delta per 1 block = the slope value
                return slope >= minSlope && slope <= maxSlope;
            } catch (Exception e) {
                IsekaiApi.LOGGER.debug("[Isekai] terrain_slope check failed; allowing: {}", e.getMessage());
                return true;
            }
        }
    }
}
