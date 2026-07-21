package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.predicate.EvaluationContext;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.Fluid;
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
        return predicate.test(new FeatureContext(pos, ctx.getLevel())) ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.SPATIAL_PREDICATE.get();
    }

    /**
     * Feature-decoration {@link EvaluationContext}: base terrain blocks are already placed, so
     * clearance / fluid queries read the {@link WorldGenLevel} directly. The multi-column scans
     * ({@code nearBlock} / {@code nearBiome} / {@code terrainSlope}) need ChunkGenerator /
     * BiomeSource samples that aren't cleanly available here, so they stay permissive (allow) —
     * exactly the historical behavior; those predicates remain enforced at structure-placement time.
     */
    private record FeatureContext(BlockPos pos, WorldGenLevel level) implements EvaluationContext {

        @Override
        public boolean solidFloor(int minClearance) {
            return checkSolidFloor(pos, level, minClearance);
        }

        @Override
        public boolean solidCeiling(int minClearance) {
            return checkSolidCeiling(pos, level, minClearance);
        }

        @Override
        public boolean inFluid(Fluid fluid) {
            FluidState fs = level.getFluidState(pos);
            // "is this position inside the named fluid". Empty fluid matches "no fluid" intent
            // if the consumer set fluid = empty registry key.
            return fs.is(fluid);
        }

        @Override
        public boolean nearBlock(HolderSet<Block> targets, int maxDistance) {
            return unsupported("near_block");
        }

        @Override
        public boolean nearBiome(ResourceKey<Biome> biome, int maxDistance) {
            return unsupported("near_biome");
        }

        @Override
        public boolean terrainSlope(double minSlope, double maxSlope) {
            return unsupported("terrain_slope");
        }

        private static boolean unsupported(String what) {
            IsekaiApi.LOGGER.debug("[Isekai] SpatialPredicatePlacementModifier: {} unsupported at "
                    + "placement-time, allowing", what);
            return true;
        }
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
