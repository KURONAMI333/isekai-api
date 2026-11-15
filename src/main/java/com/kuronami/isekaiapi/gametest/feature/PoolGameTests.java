package com.kuronami.isekaiapi.gametest.feature;

import com.kuronami.isekaiapi.IsekaiApi;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code isekai_api:pool}'s {@code shore_width} widens the rim outward, and its default must be
 * the pool that already existed.
 *
 * <p>"Unchanged" is checked the only way that means anything for a generator: two identical
 * patches of terrain, the same seed, one feature with {@code shore_width} absent and one with it
 * written as {@code 0}, compared block for block. A widened rim is checked by measuring how far
 * the sand actually reaches — beyond the single shell ring, and never past
 * {@code xz_radius + 1 + shore_width}.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class PoolGameTests {

    /** Flat stone platform, one slot per test scenario (they run concurrently in one world). */
    private static final int GROUND_Y = 100;
    private static final int HALF = 20;
    private static final int THICK = 6;

    private static final int SLOT_Z = 4000;
    private static final int SLOT_DEFAULT_X = 5000;
    private static final int SLOT_ZERO_X = 5100;
    private static final int SLOT_SHORE_X = 5200;
    /** A pool whose sampled radius plus shore would reach past the feature stage's 3x3 chunks. */
    private static final int SLOT_WIDE_X = 5300;
    /** A pool that grows into a stand of logs sitting on its rim. */
    private static final int SLOT_LOGS_X = 5400;

    /** The configured features all use this radius/depth; the radius is a bare int, not a provider. */
    private static final int RADIUS = 4;
    private static final int DEPTH = 2;
    private static final long SEED = 0xB0A7L;

    private PoolGameTests() {}

    /** Absent {@code shore_width} generates exactly the same pool as an explicit {@code 0}. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void defaultIsTheOneBlockRim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = prepare(level, SLOT_DEFAULT_X, SLOT_Z);
        BlockPos b = prepare(level, SLOT_ZERO_X, SLOT_Z);
        if (!place(helper, level, "pool_default", a) || !place(helper, level, "pool_zero", b)) {
            helper.fail("the pool did not place on flat stone with a 6-block-thick plate");
            return;
        }

        Map<Long, BlockState> first = snapshot(level, a, RELATIVE_RADIUS);
        Map<Long, BlockState> second = snapshot(level, b, RELATIVE_RADIUS);
        if (!first.equals(second)) {
            helper.fail("default config and shore_width:0 produced different blocks ("
                    + first.size() + " vs " + second.size() + " positions compared)");
            return;
        }

        int widest = widestOffset(level, a, Blocks.SAND);
        if (widest > RADIUS + 1) {
            helper.fail("default rim reaches " + widest + " blocks out; the single shell ring is "
                    + "limited to " + (RADIUS + 1));
            return;
        }
        helper.succeed();
    }

    /** {@code shore_width} grows the sand ring outward, bounded by radius + 1 + shore_width. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void shoreWidthWidensTheRim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = prepare(level, SLOT_SHORE_X, SLOT_Z);
        if (!place(helper, level, "pool_shore3", origin)) {
            helper.fail("the pool did not place on flat stone with a 6-block-thick plate");
            return;
        }

        int widest = widestOffset(level, origin, Blocks.SAND);
        if (widest <= RADIUS + 1) {
            helper.fail("shore_width:3 left the rim inside " + (RADIUS + 1) + " blocks (widest "
                    + widest + "); the ring did not widen");
            return;
        }
        if (widest > RADIUS + 1 + 3) {
            helper.fail("shore_width:3 reached " + widest + " blocks out; expected at most "
                    + (RADIUS + 1 + 3));
            return;
        }
        if (count(level, origin, Blocks.WATER) == 0) {
            helper.fail("the widened rim replaced the pool's own water");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /**
     * The beach comes out of the radius, not out of the chunk region. A pool's reach is
     * {@code radius + 1 + shore_width}; a full 14 with a 6-wide shore would ask for a chunk the
     * feature stage does not hold, and the sand would be cut at the border without a word.
     * {@code xz_radius: 12, shore_width: 6} therefore builds radius 8, whose widest write is 15.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void wideShoreShrinksTheRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = prepare(level, SLOT_WIDE_X, SLOT_Z);
        if (!place(helper, level, "pool_wide", origin)) {
            helper.fail("the pool did not place on flat stone with a 6-block-thick plate");
            return;
        }

        int widest = widestOffset(level, origin, Blocks.SAND, 20);
        if (widest > 15) {
            helper.fail("xz_radius 12 with shore_width 6 reached " + widest + " blocks out; the"
                    + " feature stage only holds 15, so the rim was cut");
            return;
        }
        if (widest <= RADIUS + 1) {
            helper.fail("the wide shore only reached " + widest + " blocks out, no further than a"
                    + " plain rim; the beach did not grow into the radius left for it");
            return;
        }
        if (count(level, origin, Blocks.WATER) == 0) {
            helper.fail("the shrunk radius left no water");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /**
     * The rim lines around wood instead of burying it. Vanilla's {@code LakeFeature} only overwrites
     * cells that are solid <em>and</em> outside {@code lava_pool_stone_cannot_replace}, which holds
     * logs and leaves, so a pool that reaches a tree grows its bank around the trunk. This places a
     * log ceiling on the pool's top shell layer — the layer the rim pass writes as often as any
     * other — and asks that not one block of it turned into {@code rim_block}.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void rimLeavesLogsAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = prepare(level, SLOT_LOGS_X, SLOT_Z);

        int logs = 0;
        for (int dx = -(RADIUS + 1); dx <= RADIUS + 1; dx++) {
            for (int dz = -(RADIUS + 1); dz <= RADIUS + 1; dz++) {
                level.setBlock(origin.offset(dx, DEPTH, dz), Blocks.OAK_LOG.defaultBlockState(), 2);
                logs++;
            }
        }
        if (!place(helper, level, "pool_default", origin)) {
            helper.fail("the pool did not place under a log ceiling on a 6-block-thick plate");
            return;
        }

        int buried = 0;
        int kept = 0;
        for (int dx = -(RADIUS + 1); dx <= RADIUS + 1; dx++) {
            for (int dz = -(RADIUS + 1); dz <= RADIUS + 1; dz++) {
                BlockState state = level.getBlockState(origin.offset(dx, DEPTH, dz));
                if (state.is(Blocks.OAK_LOG)) {
                    kept++;
                } else if (!state.isAir()) {
                    buried++;
                }
            }
        }
        if (buried > 0) {
            helper.fail("the rim replaced " + buried + " of " + logs + " logs with "
                    + level.getBlockState(origin.offset(0, DEPTH, 0)));
            return;
        }
        if (kept != logs) {
            helper.fail("only " + kept + " of " + logs + " logs survived the pool");
            return;
        }
        if (count(level, origin, Blocks.SAND) == 0) {
            helper.fail("no rim was written at all, so nothing tested the log rule");
            return;
        }
        if (count(level, origin, Blocks.WATER) == 0) {
            helper.fail("the log ceiling stopped the pool from filling");
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /** Flat stone plate with air above; returns the air cell over its centre (the pool origin). */
    private static BlockPos prepare(ServerLevel level, int x, int z) {
        level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                for (int dy = -THICK + 1; dy <= 0; dy++) {
                    level.setBlock(new BlockPos(x + dx, GROUND_Y + dy, z + dz),
                            Blocks.STONE.defaultBlockState(), 2);
                }
                for (int dy = 1; dy <= 16; dy++) {
                    level.setBlock(new BlockPos(x + dx, GROUND_Y + dy, z + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return new BlockPos(x, GROUND_Y + 1, z);
    }

    private static boolean place(GameTestHelper helper, ServerLevel level, String name, BlockPos origin) {
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath("isekai_api_test", name));
        Holder.Reference<PlacedFeature> holder = level.registryAccess()
                .lookupOrThrow(Registries.PLACED_FEATURE).get(key).orElse(null);
        if (holder == null) {
            helper.fail("isekai_api_test:" + name + " is not in the placed_feature registry — the "
                    + "GameTest fixture data/isekai_api_test/worldgen did not load");
            return false;
        }
        return holder.value().place(level, level.getChunkSource().getGenerator(),
                RandomSource.create(SEED), origin);
    }

    private static final int RELATIVE_RADIUS = RADIUS + 1 + 3 + 2;

    /** Blocks in a box around {@code origin}, keyed by offset so two slots can be compared. */
    private static Map<Long, BlockState> snapshot(ServerLevel level, BlockPos origin, int radius) {
        Map<Long, BlockState> states = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -DEPTH - 2, -radius),
                origin.offset(radius, DEPTH + 2, radius))) {
            states.put(pos.subtract(origin).asLong(), level.getBlockState(pos));
        }
        return states;
    }

    /** Largest Chebyshev horizontal distance from the origin at which {@code block} appears. */
    private static int widestOffset(ServerLevel level, BlockPos origin, net.minecraft.world.level.block.Block block) {
        return widestOffset(level, origin, block, RELATIVE_RADIUS);
    }

    /** As above, scanning {@code reach} blocks out (a wide shore reaches further than a plain rim). */
    private static int widestOffset(ServerLevel level, BlockPos origin,
                                    net.minecraft.world.level.block.Block block, int reach) {
        int widest = -1;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-reach, -DEPTH - 2, -reach),
                origin.offset(reach, DEPTH + 2, reach))) {
            if (!level.getBlockState(pos).is(block)) {
                continue;
            }
            BlockPos d = pos.subtract(origin);
            widest = Math.max(widest, Math.max(Math.abs(d.getX()), Math.abs(d.getZ())));
        }
        return widest;
    }

    private static int count(ServerLevel level, BlockPos origin, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-RELATIVE_RADIUS, -DEPTH - 2, -RELATIVE_RADIUS),
                origin.offset(RELATIVE_RADIUS, DEPTH + 2, RELATIVE_RADIUS))) {
            if (level.getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }
}
