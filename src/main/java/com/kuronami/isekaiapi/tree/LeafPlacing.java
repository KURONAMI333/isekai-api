package com.kuronami.isekaiapi.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.ApiStatus;

/**
 * Shared leaf placement for Isekai foliage placers and tree decorators.
 *
 * <p>Leaves with {@code persistent:false} survive only while {@link LeavesBlock#DISTANCE} is below
 * 7. That distance is the length of the shortest chain of leaves reaching a log (a log directly
 * next to the leaf counts as 1). Vanilla's {@code tryPlaceLeaf} writes the provider's state as-is
 * and relies on a later neighbour update to fill the distance in, and worldgen does not run
 * neighbour updates: a leaf placed away from the trunk keeps the provider's default
 * {@code distance: 7} and is removed the first time it is random-ticked.
 *
 * <p>{@link #placePinned} and {@link #pinIfLeaves} write {@code distance: 5} instead. That carries
 * the leaf through generation: the tree feature's closing shape pass
 * ({@code StructureTemplate.updateShapeAtEdge}) calls {@link LeavesBlock#updateShape} on the blocks
 * at the shape boundary, which schedules a tick, and {@link LeavesBlock#tick} then recomputes the
 * distance from the real neighbour graph. A pinned leaf within 6 steps of a log gets its true
 * distance and stays; one further away becomes 7 and decays on its next random tick.
 *
 * <p>The pin is therefore not persistence. It keeps the placement itself — without it every spread
 * leaf would be gone before the chunk ticks once — and the crown still has to put its leaves within
 * reach of the trunk. Call vanilla's {@code tryPlaceLeaf} for a compact log-adjacent core, and the
 * pinned helpers for fronds, strands and tips that are close enough to survive.
 */
@ApiStatus.Internal
public final class LeafPlacing {

    /**
     * Distance written by the pinned helpers. Below 7, so the leaf survives long enough for the
     * scheduler to compute its real distance; 5 is vanilla's largest reachable non-decaying value.
     */
    public static final int SAFE_PINNED_DISTANCE = 5;

    private LeafPlacing() {}

    /**
     * Places a leaf with {@link LeavesBlock#DISTANCE} pinned to {@link #SAFE_PINNED_DISTANCE}.
     *
     * <p>Use for fronds, hanging strands, branching tips and disc/weeping placements, where the
     * provider's own distance (usually 7) would be removed before the chunk ticks. The pin keeps
     * the block through generation only: once the leaf ticker recomputes from the real neighbour
     * graph, a leaf with no chain of leaves to a log within 6 steps turns to distance 7 and decays.
     * For core leaves that are log-adjacent, call vanilla's {@code FoliagePlacer.tryPlaceLeaf}
     * directly from the placer subclass.
     */
    public static void placePinned(LevelSimulatedReader level, FoliagePlacer.FoliageSetter setter,
                                   RandomSource random, TreeConfiguration config, BlockPos pos) {
        if (!TreeFeature.validTreePos(level, pos)) {
            return;
        }
        BlockState state = config.foliageProvider.getState(random, pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED,
                    level.isFluidAtPosition(pos, f -> f.isSourceOfType(Fluids.WATER)));
        }
        if (state.hasProperty(LeavesBlock.DISTANCE)) {
            state = state.setValue(LeavesBlock.DISTANCE, SAFE_PINNED_DISTANCE);
        }
        setter.set(pos, state);
    }

    /**
     * Pins {@link LeavesBlock#DISTANCE} when {@code state} is a leaf block, so a leaf a decorator
     * writes survives long enough for the scheduler to compute its real distance.
     *
     * <p>Decorators set blocks one at a time and never run through the feature's leaf distance
     * pass, so a leaf provider written into a decorator would keep {@code distance: 7} and vanish
     * on the first random tick, even when it touches the trunk. The recomputation from the real
     * neighbour graph decides afterwards: a leaf within 6 steps of a log keeps its distance and
     * stays, one further away becomes 7 and decays. Any other state is returned unchanged, so this
     * is safe to call from every decorator placement.
     */
    public static BlockState pinIfLeaves(BlockState state) {
        if (state.hasProperty(LeavesBlock.DISTANCE)) {
            return state.setValue(LeavesBlock.DISTANCE, SAFE_PINNED_DISTANCE);
        }
        return state;
    }
}
