package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.FluidState;

import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * Gates a feature placement on a {@link SpatialPredicate}. Inserted automatically by the
 * {@code apply_worldshape} BiomeModifier into the placement-modifier list of each placed
 * feature listed in {@code featurePredicates}, so feature placement respects the same
 * spatial conditions consumers use elsewhere (Y range, solid floor, fluid context, etc.).
 *
 * <p>This evaluator differs from {@link com.kuronami.isekaiapi.impl.SpatialPredicateEvaluator}
 * (used by structure placement) in that it reads from a {@link WorldGenLevel} — by feature
 * decoration time, base terrain blocks have already been placed, so we can sample them
 * directly without falling back to noise-column sampling.
 *
 * <p>Direct JSON usage is supported but rarely needed; the worldshape pipeline is the
 * intended consumer.
 */
@ApiStatus.Internal
public class SpatialPredicatePlacementModifier extends PlacementModifier {

    public static final MapCodec<SpatialPredicatePlacementModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SpatialPredicate.CODEC.fieldOf("predicate").forGetter(m -> m.predicate)
    ).apply(i, SpatialPredicatePlacementModifier::new));

    private final SpatialPredicate predicate;

    public SpatialPredicatePlacementModifier(SpatialPredicate predicate) {
        this.predicate = predicate;
    }

    public SpatialPredicate predicate() { return predicate; }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        return evaluate(predicate, pos, ctx.getLevel()) ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.SPATIAL_PREDICATE.get();
    }

    private static boolean evaluate(SpatialPredicate p, BlockPos pos, WorldGenLevel level) {
        if (p instanceof SpatialPredicate.Always) return true;
        if (p instanceof SpatialPredicate.Never) return false;
        if (p instanceof SpatialPredicate.YInRange y) {
            return pos.getY() >= y.min() && pos.getY() <= y.max();
        }
        if (p instanceof SpatialPredicate.And and) {
            for (var c : and.all()) if (!evaluate(c, pos, level)) return false;
            return true;
        }
        if (p instanceof SpatialPredicate.Or or) {
            for (var c : or.any()) if (evaluate(c, pos, level)) return true;
            return false;
        }
        if (p instanceof SpatialPredicate.Not not) {
            return !evaluate(not.inner(), pos, level);
        }
        if (p instanceof SpatialPredicate.SolidFloor sf) {
            return checkSolidFloor(pos, level, sf.minClearance());
        }
        if (p instanceof SpatialPredicate.SolidCeiling sc) {
            return checkSolidCeiling(pos, level, sc.minClearance());
        }
        if (p instanceof SpatialPredicate.InFluid in) {
            FluidState fs = level.getFluidState(pos);
            // InFluid checks "is at this position inside the named fluid". Empty fluid
            // matches "no fluid" intent if consumer set fluid = empty registry key.
            return fs.is(in.fluid());
        }
        // NearBlock / NearBiome / TerrainSlope: these are structure-time predicates that
        // require ChunkGenerator / BiomeSource samples not cleanly available at placement
        // decoration. Treat as allow at placement-time; structures still gate them.
        IsekaiApi.LOGGER.debug("[Isekai] SpatialPredicatePlacementModifier: {} unsupported at " +
                "placement-time, allowing", p.getClass().getSimpleName());
        return true;
    }

    /**
     * {@code SolidFloor} per the {@link SpatialPredicate.SolidFloor} contract: "solid ground
     * directly beneath AND at least {@code minClearance} blocks of empty space above." This
     * matches {@code SpatialPredicateEvaluator.checkClearance(DOWN)} used at structure-placement
     * time, so the same predicate behaves identically whether it gates a structure or a feature.
     */
    private static boolean checkSolidFloor(BlockPos pos, WorldGenLevel level, int minClearance) {
        BlockPos.MutableBlockPos cur = pos.mutable();
        // Solid (non-air, non-fluid) block immediately below.
        cur.set(pos).move(Direction.DOWN);
        BlockState below = level.getBlockState(cur);
        if (below.isAir() || !below.getFluidState().isEmpty()) return false;
        // minClearance blocks of air above.
        for (int i = 1; i <= minClearance; i++) {
            cur.set(pos).move(Direction.UP, i);
            if (!level.getBlockState(cur).isAir()) return false;
        }
        return true;
    }

    /**
     * {@code SolidCeiling} per the {@link SpatialPredicate.SolidCeiling} contract: solid block
     * directly above AND {@code minClearance} blocks of empty space below. Mirror of
     * {@link #checkSolidFloor}; matches the structure-time evaluator.
     */
    private static boolean checkSolidCeiling(BlockPos pos, WorldGenLevel level, int minClearance) {
        BlockPos.MutableBlockPos cur = pos.mutable();
        cur.set(pos).move(Direction.UP);
        BlockState above = level.getBlockState(cur);
        if (above.isAir() || !above.getFluidState().isEmpty()) return false;
        for (int i = 1; i <= minClearance; i++) {
            cur.set(pos).move(Direction.DOWN, i);
            if (!level.getBlockState(cur).isAir()) return false;
        }
        return true;
    }
}
