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
 * Shared decay-safe leaf placement for Isekai foliage placers — the part of vanilla's
 * {@code FoliagePlacer.tryPlaceLeaf} contract that's missing for spread / disconnected leaves.
 *
 * <p>Background. Minecraft leaves with {@code persistent:false} decay unless they have a path
 * to a log within block-distance 6. Vanilla's {@code tryPlaceLeaf} leaves the {@code distance}
 * blockstate at its default and relies on a neighbour update — but worldgen does NOT trigger
 * neighbour updates, so any leaf that isn't physically adjacent to a log (or to another leaf
 * that resolves to a low distance via vanilla's compact crowns) decays. A placer that draws
 * fronds, hanging strands, branching tips or any non-compact crown therefore loses its outer
 * leaves the first time the chunk ticks — the tree visibly falls apart.
 *
 * <p>Fix. For any leaf NOT physically anchored to a log, place it with {@link LeavesBlock#DISTANCE}
 * pinned to a value &lt; 7 (5 here, matching Nature's Spirit's coconut implementation). The
 * pinned distance survives because worldgen skips neighbour updates. Compact-core leaves can
 * keep calling vanilla's {@code tryPlaceLeaf} directly from the placer subclass — they resolve
 * to a low distance naturally from log adjacency.
 *
 * <p>This is the canonical Isekai mechanism for spread-leaf placement — every new foliage
 * placer should call {@link #placePinned} for non-log-adjacent leaves rather than reimplement
 * the {@link BlockStateProperties#WATERLOGGED} / distance dance inline.
 */
@ApiStatus.Internal
public final class LeafPlacing {

    /** Pinned distance for spread leaves. {@code < 7} prevents decay; 5 matches vanilla's max. */
    public static final int SAFE_PINNED_DISTANCE = 5;

    private LeafPlacing() {}

    /**
     * Place a leaf with {@link LeavesBlock#DISTANCE} pinned to {@link #SAFE_PINNED_DISTANCE} so
     * it does not decay even when not in a connected leaf-path to a log. Use for fronds, hanging
     * strands, branching tips, disc/weeping placements — anything outside a compact log-adjacent
     * core. For core leaves that ARE log-adjacent, call vanilla's
     * {@code FoliagePlacer.tryPlaceLeaf} directly from the placer subclass.
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
     * Pins {@link LeavesBlock#DISTANCE} when {@code state} is a leaf block, so leaves placed by a
     * tree decorator decay like the crown's own leaves instead of falling apart on the first tick.
     *
     * <p>Decorators set blocks one at a time and never run through the feature's leaf
     * distance pass, so a leaf provider written into a decorator would otherwise keep
     * {@code distance: 7} — the "no log nearby" state — and vanish. Any other state is returned
     * unchanged, so this is safe to call from every decorator placement.
     */
    public static BlockState pinIfLeaves(BlockState state) {
        if (state.hasProperty(LeavesBlock.DISTANCE)) {
            return state.setValue(LeavesBlock.DISTANCE, SAFE_PINNED_DISTANCE);
        }
        return state;
    }
}
