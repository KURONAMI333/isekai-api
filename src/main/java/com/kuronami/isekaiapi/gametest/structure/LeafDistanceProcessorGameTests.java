package com.kuronami.isekaiapi.gametest.structure;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.structure.LeafDistanceProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code isekai_api:leaf_distances} rewrites a pasted template's leaf decay data.
 *
 * <p>The scene is a tree as a structure editor saves one: a 4-log trunk with a three-layer crown,
 * every leaf written with {@code distance: 7} (the decay value) and non-persistent, plus a 3-block
 * column of leaves in the corner of the box that touches no log. One crown leaf is written
 * persistent so the two flags can be told apart. The scene is built in the test world and then
 * captured with {@link StructureTemplate#fillFromWorld}, so the template that gets placed holds
 * exactly those wrong values — no fixture file, and the states are visible in this source.
 *
 * <p>Distances quoted below are the six-neighbour steps from the trunk (vanilla's leaf rule), not
 * the processor's output: {@code (2,4,2)} sits on the trunk top, {@code (0,4,0)} is the corner of
 * the 5x5 bottom layer, and so on. A processor that wrote a flat value would fail one end or the
 * other.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class LeafDistanceProcessorGameTests {

    /** Patch for "the distances come from the template's logs". */
    private static final int SLOT_A_X = 6000;
    private static final int SLOT_A_Z = 4000;

    /** Patch for "orphan leaves can be made persistent". */
    private static final int SLOT_B_X = 6064;
    private static final int SLOT_B_Z = 4064;

    /** Patch for "keep_persistent leaves the template's flag alone". */
    private static final int SLOT_C_X = 6128;
    private static final int SLOT_C_Z = 4128;

    /** Air at the test world's surface; the scene is built here, well clear of the ground. */
    private static final int SCENE_Y = 100;

    /** The tree as saved: x, y, z. */
    private static final Vec3i SIZE = new Vec3i(5, 7, 5);

    private LeafDistanceProcessorGameTests() {}

    /** With no config, the crown's 7s become real distances and the orphans stay decayable. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void distancesComeFromTheTemplatesOwnLogs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = scene(helper, level, SLOT_A_X, SLOT_A_Z,
                new LeafDistanceProcessor(LeafDistanceProcessor.OrphanLeaves.DECAY, false));
        if (origin == null) {
            return;
        }

        // One step from the trunk top; three across the bottom layer; five in its corner; four in
        // the 3x3 layer; three at the tip.
        if (!expectLeaf(helper, level, origin, 2, 4, 2, 1, false)
                || !expectLeaf(helper, level, origin, 1, 4, 1, 3, false)
                || !expectLeaf(helper, level, origin, 0, 4, 0, 5, false)
                || !expectLeaf(helper, level, origin, 1, 5, 1, 4, false)
                || !expectLeaf(helper, level, origin, 2, 6, 2, 3, false)) {
            return;
        }
        // The template wrote this one as persistent; with keep_persistent off the flag must go.
        if (!expectLeaf(helper, level, origin, 2, 4, 2, 1, false)) {
            return;
        }
        if (!expectOrphans(helper, level, origin, false)) {
            return;
        }
        helper.succeed();
    }

    /** {@code orphan_leaves: persist} keeps a trunkless crown by marking the unreachable leaves. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void orphanLeavesCanBePersisted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = scene(helper, level, SLOT_B_X, SLOT_B_Z,
                new LeafDistanceProcessor(LeafDistanceProcessor.OrphanLeaves.PERSIST, false));
        if (origin == null) {
            return;
        }
        if (!expectLeaf(helper, level, origin, 2, 4, 2, 1, false)) {
            return;
        }
        if (!expectOrphans(helper, level, origin, true)) {
            return;
        }
        helper.succeed();
    }

    /** {@code keep_persistent: true} respects what the template already said. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void keepPersistentLeavesTheTemplatesFlag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = scene(helper, level, SLOT_C_X, SLOT_C_Z,
                new LeafDistanceProcessor(LeafDistanceProcessor.OrphanLeaves.DECAY, true));
        if (origin == null) {
            return;
        }
        // This leaf is adjacent to the trunk top (distance 1) and persistent in the template.
        if (!expectLeaf(helper, level, origin, 2, 4, 2, 1, true)) {
            return;
        }
        if (!expectLeaf(helper, level, origin, 0, 4, 2, 3, false)) {
            return;
        }
        if (!expectOrphans(helper, level, origin, false)) {
            return;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /**
     * Builds the "saved by an editor" tree, captures it as a template, wipes the area and places it
     * back through {@code processor}. Returns the template origin, or null after reporting why the
     * scene could not be built.
     */
    private static BlockPos scene(GameTestHelper helper, ServerLevel level, int x, int z,
                                  LeafDistanceProcessor processor) {
        level.getChunk(x >> 4, z >> 4);
        BlockPos origin = new BlockPos(x, SCENE_Y, z);
        clear(level, origin);
        saveEditorTree(level, origin);

        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, SIZE, false, null);
        if (!template.getSize().equals(SIZE)) {
            helper.fail("captured template is " + template.getSize() + "; expected " + SIZE);
            return null;
        }
        clear(level, origin);

        StructurePlaceSettings settings = new StructurePlaceSettings().addProcessor(processor);
        template.placeInWorld(level, origin, origin, settings, RandomSource.create(0L), 2);
        return origin;
    }

    /**
     * The exact states an editor leaves behind: the trunk, then leaves at distance 7 everywhere,
     * persistent on the single crown leaf the keep_persistent test reads.
     */
    private static void saveEditorTree(ServerLevel level, BlockPos origin) {
        for (int y = 0; y < 4; y++) {
            level.setBlock(origin.offset(2, y, 2), Blocks.OAK_LOG.defaultBlockState(), 2);
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                level.setBlock(origin.offset(x, 4, z), editorLeaf(x == 2 && z == 2), 2);
            }
        }
        for (int x = 1; x < 4; x++) {
            for (int z = 1; z < 4; z++) {
                level.setBlock(origin.offset(x, 5, z), editorLeaf(false), 2);
            }
        }
        level.setBlock(origin.offset(2, 6, 2), editorLeaf(false), 2);
        for (int y = 0; y < 3; y++) {
            level.setBlock(origin.offset(0, y, 0), editorLeaf(false), 2);
        }
    }

    private static BlockState editorLeaf(boolean persistent) {
        return Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 7)
                .setValue(LeavesBlock.PERSISTENT, persistent);
    }

    /** The fixture is small; wiping a slightly larger box keeps a previous run from confusing it. */
    private static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(6, 8, 6))) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** The three leaves of the log-less column in the corner of the box. */
    private static boolean expectOrphans(GameTestHelper helper, ServerLevel level, BlockPos origin,
                                         boolean persistent) {
        for (int y = 0; y < 3; y++) {
            if (!expectLeaf(helper, level, origin, 0, y, 0, 7, persistent)) {
                return false;
            }
        }
        return true;
    }

    private static boolean expectLeaf(GameTestHelper helper, ServerLevel level, BlockPos origin,
                                      int dx, int dy, int dz, int distance, boolean persistent) {
        BlockPos pos = origin.offset(dx, dy, dz);
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LeavesBlock)) {
            helper.fail("expected leaves at " + dx + "," + dy + "," + dz + " (world "
                    + pos + ") but found " + state);
            return false;
        }
        int actual = state.getValue(LeavesBlock.DISTANCE);
        boolean actualPersistent = state.getValue(LeavesBlock.PERSISTENT);
        if (actual != distance || actualPersistent != persistent) {
            helper.fail("leaf " + dx + "," + dy + "," + dz + " is distance " + actual
                    + " persistent " + actualPersistent + "; expected distance " + distance
                    + " persistent " + persistent);
            return false;
        }
        return true;
    }
}
