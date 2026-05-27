package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Evaluate a {@link SpatialPredicate} against a (position, world context) pair. Used by
 * the structure-placement Mixin to gate structure spawning at consumer-declared spatial
 * conditions.
 *
 * <p>At structure placement time (during {@code Structure.findValidGenerationPoint}),
 * chunks are not yet fully populated — we have a {@link ChunkGenerator} and can probe
 * column data but not load arbitrary blocks. Cheap variants (Y / Always / Never / And /
 * Or / Not) evaluate directly. Expensive variants (SolidFloor, SolidCeiling, NearBlock,
 * NearBiome, TerrainSlope, InFluid) sample the {@link NoiseColumn} for the column at
 * the target position.
 *
 * <p>Caller passes a context bundle so each predicate variant can pull what it needs;
 * unsupported variants fall back to {@code true} (let the structure spawn) with a
 * debug warning, since blocking everything on an unimplemented evaluator path would be
 * surprising.
 */
public final class SpatialPredicateEvaluator {

    private SpatialPredicateEvaluator() {}

    /**
     * Evaluate {@code predicate} at {@code pos} using {@code ctx}. Returns {@code true}
     * if the structure should be allowed to spawn here.
     */
    public static boolean evaluate(SpatialPredicate predicate, BlockPos pos, Context ctx) {
        if (predicate instanceof SpatialPredicate.Always) return true;
        if (predicate instanceof SpatialPredicate.Never) return false;
        if (predicate instanceof SpatialPredicate.YInRange y) {
            return pos.getY() >= y.min() && pos.getY() <= y.max();
        }
        if (predicate instanceof SpatialPredicate.And and) {
            for (var child : and.all()) {
                if (!evaluate(child, pos, ctx)) return false;
            }
            return true;
        }
        if (predicate instanceof SpatialPredicate.Or or) {
            for (var child : or.any()) {
                if (evaluate(child, pos, ctx)) return true;
            }
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
        // Variants we don't yet have a sound evaluator for at structure placement time
        // (NearBlock / NearBiome / TerrainSlope / InFluid). Default-allow so we don't
        // block structures unexpectedly. Consumer can still rely on the Y / clearance
        // cases which cover most worldshape gates.
        IsekaiApi.LOGGER.debug(
                "[Isekai] SpatialPredicate.{} not yet evaluated at structure placement time; allowing",
                predicate.getClass().getSimpleName());
        return true;
    }

    /**
     * Sample the noise column at {@code pos.x, pos.z} and check whether the requested
     * direction has the requested vertical clearance from solid surface at {@code pos.y}.
     * {@code NoiseColumn.getBlock(int)} takes <em>absolute</em> Y; out-of-column queries
     * return air, so we don't need explicit bounds checks.
     */
    private static boolean checkClearance(BlockPos pos, Context ctx, int minClearance, Direction dir) {
        try {
            NoiseColumn column = ctx.chunkGenerator.getBaseColumn(
                    pos.getX(), pos.getZ(), ctx.heightAccessor, ctx.randomState);
            int targetY = pos.getY();

            if (dir == Direction.DOWN) {
                // Need at least one solid block immediately below + minClearance air above target.
                if (column.getBlock(targetY - 1).isAir()) return false;
                for (int i = 1; i <= minClearance; i++) {
                    if (!column.getBlock(targetY + i).isAir()) return false;
                }
                return true;
            } else { // UP — ceiling
                if (column.getBlock(targetY + 1).isAir()) return false;
                for (int i = 1; i <= minClearance; i++) {
                    if (!column.getBlock(targetY - i).isAir()) return false;
                }
                return true;
            }
        } catch (Exception e) {
            IsekaiApi.LOGGER.debug("[Isekai] SpatialPredicate clearance check failed; allowing: {}", e.getMessage());
            return true;
        }
    }

    private enum Direction { DOWN, UP }

    /** Context bundle for predicate evaluation at structure placement time. */
    public record Context(
            ChunkGenerator chunkGenerator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState
    ) {}
}
