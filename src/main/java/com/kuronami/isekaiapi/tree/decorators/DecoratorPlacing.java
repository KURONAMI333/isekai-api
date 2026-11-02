package com.kuronami.isekaiapi.tree.decorators;

import com.kuronami.isekaiapi.tree.LeafPlacing;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Placement rules shared by Isekai's tree decorators.
 *
 * <p>Two things every decorator here has to get right, so they live in one place instead of four:
 * leaves written by a decorator must go through {@link LeafPlacing} (otherwise they keep the
 * decayed {@code distance: 7} state and disappear on the first tick), and a block that has a
 * facing property must point away from whatever it is attached to.
 */
@ApiStatus.Internal
final class DecoratorPlacing {

    private DecoratorPlacing() {}

    /** Writes {@code state} at {@code pos}, pinning leaf distance first. */
    static void place(TreeDecorator.Context context, BlockPos pos, BlockState state) {
        context.setBlock(pos, LeafPlacing.pinIfLeaves(state));
    }

    /**
     * The tree's own trunk positions, lowest first.
     *
     * <p>{@link TreeDecorator.Context#logs()} is not only the trunk: {@code TrunkPlacer.setDirtAt}
     * writes the ground block under the base through the same setter. A 2x2 trunk placer
     * ({@code giant_trunk_placer}, {@code dark_oak_trunk_placer}) calls it for all four columns, so
     * on grass it adds four dirt entries — every one of them one block below the trunk. A
     * decorator that measures the trunk, reuses its block or counts its blocks has to skip them.
     *
     * <p>Every dirt-like entry at the lowest Y is dropped, and only there: the ground blocks all
     * share that Y, while the trunk starts above it. The test is the same one {@code TrunkPlacer}
     * uses to decide whether to write the ground block at all ({@code DIRT} plus farmland). A trunk
     * built out of basalt, blackstone or planks is therefore kept whole; a datapack that sets a
     * {@code dirt_provider} to something that is not dirt-tagged keeps the extra entries, which
     * cost one block of height range and nothing else. When the tree has only one Y of entries,
     * nothing is dropped: there is no trunk above the ground to fall back to.
     *
     * <p>Positions an earlier decorator has turned to air (for example {@code isekai_api:broken_top})
     * are left out, so a later decorator never hangs a branch, a vine or a patch off a log that is
     * no longer there. {@code context.logs()} itself still lists them; only this view drops them.
     */
    static List<BlockPos> logs(TreeDecorator.Context context) {
        List<BlockPos> all = context.logs();
        if (all.isEmpty()) {
            return List.of();
        }
        int lowestY = all.get(0).getY();
        boolean hasHigher = all.get(all.size() - 1).getY() > lowestY;
        List<BlockPos> standing = new java.util.ArrayList<>(all.size());
        for (BlockPos pos : all) {
            if (hasHigher && pos.getY() == lowestY) {
                BlockState state = stateAt(context.level(), pos);
                if (state != null && (state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND))) {
                    continue;
                }
            }
            if (!context.isAir(pos)) {
                standing.add(pos);
            }
        }
        return standing;
    }

    /**
     * The lowest trunk position of the tree being decorated, or {@code null} when the tree has
     * neither logs nor roots.
     *
     * <p>{@link TreeDecorator.Context#logs()} arrives sorted by Y, so the first entry is the base —
     * the same anchor vanilla's {@code alter_ground} uses. That entry is often the ground block the
     * trunk placer set below the trunk rather than a log, which is what a ground-level decorator
     * wants; use {@link #logs} when the logs themselves are needed. Roots are the fallback because a
     * tree with a root placer (mangrove-style) can start below its first log.
     */
    static BlockPos trunkBase(TreeDecorator.Context context) {
        if (!context.logs().isEmpty()) {
            return context.logs().get(0);
        }
        if (!context.roots().isEmpty()) {
            return context.roots().get(0);
        }
        return null;
    }

    /**
     * The state actually written at {@code pos}, or {@code null} when the level in this context
     * cannot be read as a block source.
     *
     * <p>{@link TreeDecorator.Context#level()} is typed as {@link LevelSimulatedReader}, which can
     * test states but not return them. During worldgen the instance is the {@code WorldGenLevel}, a
     * {@link net.minecraft.world.level.BlockGetter}, so the read succeeds; the cast is checked and
     * the caller has to cope with a {@code null} rather than assume it.
     */
    static @Nullable BlockState stateAt(LevelSimulatedReader level, BlockPos pos) {
        if (level instanceof net.minecraft.world.level.BlockGetter getter) {
            return getter.getBlockState(pos);
        }
        return null;
    }

    /**
     * Turns {@code state} to face {@code outward} — away from the log or leaf it sits against.
     *
     * <p>Handles the three property shapes a decorative block plausibly has: a horizontal facing
     * (ladders, buttons, wall signs), a full six-way facing (trapdoors, hoppers, observers) and the
     * four side booleans used by vines and multiface blocks. In the last case the flag for the side
     * that touches the support is set, which is how vanilla vines attach.
     *
     * @param state   the provider's state
     * @param outward the direction pointing away from the supporting block
     */
    static BlockState orient(BlockState state, Direction outward) {
        if (outward.getAxis().isHorizontal() && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, outward);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, outward);
        }
        if (outward.getAxis().isHorizontal()
                && state.hasProperty(BlockStateProperties.NORTH)
                && state.hasProperty(BlockStateProperties.SOUTH)
                && state.hasProperty(BlockStateProperties.EAST)
                && state.hasProperty(BlockStateProperties.WEST)) {
            return state.setValue(switch (outward.getOpposite()) {
                case NORTH -> BlockStateProperties.NORTH;
                case SOUTH -> BlockStateProperties.SOUTH;
                case EAST -> BlockStateProperties.EAST;
                case WEST -> BlockStateProperties.WEST;
                default -> throw new IllegalArgumentException("not horizontal: " + outward);
            }, true);
        }
        return state;
    }
}
