package com.kuronami.isekaiapi.gametest.wood;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scene building for the wood-set GameTests that need ground under a tree.
 *
 * <p>GameTest's {@code empty3x3x3} template is a 3x3x3 hole, too small for a trunk and a crown.
 * Rather than shipping another structure template, these tests build a grass platform in the air
 * far above the flat test world's surface (y=100 is air there).
 *
 * <p>The slots sit on their own Z row, 400 blocks off the tree GameTests' row, so a crown or a
 * decorator's ground patch cannot reach into another suite's platform even if both run in the same
 * batch.
 */
final class WoodTestScenes {

    /** X of slot 0's platform centre. */
    private static final int BASE_X = 3000;

    /** Z of every platform centre; the tree suite uses 3000. */
    private static final int BASE_Z = 3400;

    /** Y of the ground block; the tree's lowest log sits one above. */
    static final int GROUND_Y = 100;

    /** Distance between two slots. */
    private static final int SLOT_SPACING = 64;

    /** Half-width of the grass platform, so a radius-2 crown and its ground patch fit. */
    private static final int PLATFORM_RADIUS = 4;

    /** Air cleared above the platform, enough for the tallest example tree. */
    private static final int CLEAR_HEIGHT = 24;

    private WoodTestScenes() {}

    /**
     * Builds slot {@code slot}'s platform and returns the position a sapling would occupy — the top
     * of the centre grass block.
     */
    static BlockPos prepare(GameTestHelper helper, int slot) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = new BlockPos(BASE_X + slot * SLOT_SPACING, GROUND_Y, BASE_Z);
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
                level.setBlock(ground.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    level.setBlock(ground.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return ground.above();
    }

    /** How many blocks matching {@code match} sit in the inclusive box. */
    static int count(ServerLevel level, BlockPos from, BlockPos to, Predicate<BlockState> match) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (match.test(level.getBlockState(pos))) {
                count++;
            }
        }
        return count;
    }

    /** The inclusive box around a tree: {@code radius} blocks out, {@code height} blocks up. */
    static BlockPos boxMin(BlockPos origin, int radius) {
        return origin.offset(-radius, 0, -radius);
    }

    static BlockPos boxMax(BlockPos origin, int radius, int height) {
        return origin.offset(radius, height, radius);
    }
}
