package com.kuronami.isekaiapi.gametest.tree;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.tree.CurvedTrunkPlacer;
import com.kuronami.isekaiapi.tree.FrondFoliagePlacer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Acceptance for the palm pair {@code isekai_api:curved} + {@code isekai_api:frond}: a stem that
 * bends while it climbs (not a vertical shaft plus a horizontal arm) and a crown made of eight
 * fronds (not four cardinal arms).
 *
 * <p>Every test grows a real tree in the flat test world instead of calling {@code placeTrunk}
 * against a fake level, because the two things that were wrong before are visible only in the
 * blocks: the pillar axis of a bent log and the leaf distances the ticker resolves after
 * generation. Slots 40-50 are reserved for this class.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class CurvedPalmGameTests {

    /** First slot owned by this class; one slot per test. */
    private static final int FIRST_SLOT = 40;

    /** How far the crown may spread; the box the tests scan around a tree. */
    private static final int SCAN_RADIUS = 10;

    /** Air cleared above the platform: trunk plus crown plus the drooping tips. */
    private static final int SCAN_HEIGHT = 20;

    /**
     * Blocks a feature's 3x3 chunk region always covers in each horizontal direction from an origin
     * in the middle chunk: the reach {@link CurvedTrunkPlacer#MAX_HORIZONTAL_TRAVEL} is built on.
     */
    private static final int REGION_REACH = 16;

    /** Box scanned for the tall stems: one block past the region edge, so an overflow is seen. */
    private static final int REGION_SCAN_RADIUS = REGION_REACH + 1;

    /** Air cleared for a 30-log stem and its crown. */
    private static final int TALL_SCENE_HEIGHT = 33;

    /**
     * Seeds the tall-stem tests grow. A short stem cannot spend an 11-block sideways budget, so the
     * region check only means something with a stem tall enough to try: these four are drawn so that
     * at least one of them fills the budget.
     */
    private static final long[] TALL_SEEDS = {0x5EEDL, 0x0BEEFL, 0x51D3L, 0x2E4BL};

    /** A crown with a fixed frond length, so the shape checks are reproducible. */
    private static final String FROND = """
            {
              "type": "isekai_api:frond",
              "radius": 0,
              "offset": 0,
              "length": {"type": "minecraft:constant", "value": 4},
              "droop": {"type": "minecraft:constant", "value": 1},
              "rise": 1
            }
            """;

    /** Flat, four-cell fronds at the tip plane, used to read the top view of the crown. */
    private static final String FROND_FLAT = """
            {
              "type": "isekai_api:frond",
              "radius": 0,
              "offset": 0,
              "length": {"type": "minecraft:constant", "value": 4},
              "droop": {"type": "minecraft:constant", "value": 0},
              "rise": 0
            }
            """;

    /** The same crown lifted one block above the trunk, with a droop the lift has to trim away. */
    private static final String FROND_LIFTED = """
            {
              "type": "isekai_api:frond",
              "radius": 0,
              "offset": 1,
              "length": {"type": "minecraft:constant", "value": 4},
              "droop": {"type": "minecraft:constant", "value": 1},
              "rise": 1
            }
            """;

    /** Two climbing steps per frond, so the six-step budget is tight against the droop. */
    private static final String FROND_RISE_TWO = """
            {
              "type": "isekai_api:frond",
              "radius": 0,
              "offset": 0,
              "length": {"type": "minecraft:constant", "value": 4},
              "droop": {"type": "minecraft:constant", "value": 1},
              "rise": 2
            }
            """;

    /** The widest frond the type allows, at the tip plane: the diagonal staircases at full reach. */
    private static final String FROND_LONG = """
            {
              "type": "isekai_api:frond",
              "radius": 0,
              "offset": 0,
              "length": {"type": "minecraft:constant", "value": 5},
              "droop": {"type": "minecraft:constant", "value": 2},
              "rise": 0
            }
            """;

    /** A trunk that bends hard and always reaches the same way for a fixed seed. */
    private static final String BENT_TRUNK = """
            {
              "type": "isekai_api:curved",
              "base_height": 8,
              "height_rand_a": 0,
              "height_rand_b": 0,
              "bend": 1.0,
              "convert_ground": false,
              "min_height": 4
            }
            """;

    /** A straight stem, so the crown is the only thing shaped in the test. */
    private static final String STRAIGHT_TRUNK = BENT_TRUNK.replace("\"bend\": 1.0", "\"bend\": 0.0");

    /**
     * The tallest stem the type allows, bending as hard as it can: 30 levels with a sideways chance
     * that reaches 1 in the upper third. This is the only shape that can spend the whole 11-block
     * budget, so it is what the region check grows.
     */
    private static final String TALL_BENT_TRUNK = """
            {
              "type": "isekai_api:curved",
              "base_height": 30,
              "height_rand_a": 0,
              "height_rand_b": 0,
              "bend": 2.0,
              "convert_ground": false,
              "min_height": 4
            }
            """;

    /**
     * The same stem with {@code space_check} off: a blocked sideways step is stepped back instead of
     * ending the trunk, which is the only way to see whether that blocked step spent budget.
     */
    private static final String TALL_OPEN_TRUNK = TALL_BENT_TRUNK
            .replace("\"convert_ground\": false,", "\"convert_ground\": false,\n  \"space_check\": false,");

    private CurvedPalmGameTests() {}

    /**
     * The new pair grows a tree: logs above the ground, a crown of leaves, and the crown sits above
     * the top log rather than draping up the stem.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void curvedTrunkGrowsAFrondCrownAtItsTip(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT);
        if (!TreeTestScenes.place(helper, tree(helper, BENT_TRUNK, FROND), origin)) {
            helper.fail("the curved palm reported no placement");
            return;
        }
        List<BlockPos> logs = scan(helper.getLevel(), origin, state -> state.is(BlockTags.LOGS));
        if (logs.size() < 5) {
            helper.fail("only " + logs.size() + " logs grew for a base_height 8 trunk: " + logs);
            return;
        }
        int leaves = leaves(helper.getLevel(), origin).size();
        if (leaves < 8) {
            helper.fail("only " + leaves + " leaves grew, so the crown did not fill");
            return;
        }

        BlockPos tip = highest(logs);
        if (!helper.getLevel().getBlockState(tip.above()).is(Blocks.OAK_LEAVES)) {
            helper.fail("no leaf above the top log " + tip + ", so the crown is not on the tip");
            return;
        }
        // The old palm draped leaves along the upper trunk; the curved one has one crown site.
        BlockPos second = logs.stream().filter(p -> p.getY() == origin.getY() + 1).findFirst()
                .orElse(null);
        if (second != null && helper.getLevel().getBlockState(second.above()).is(Blocks.OAK_LEAVES)) {
            helper.fail("a leaf sits above the second log " + second
                    + ", so the crown still follows the trunk");
            return;
        }
        helper.succeed();
    }

    /** Every log of the stem shares a face with another one: no diagonal-only step. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void everyLogIsFaceConnected(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 1);
        if (!TreeTestScenes.place(helper, tree(helper, BENT_TRUNK, FROND), origin)) {
            helper.fail("the curved palm reported no placement");
            return;
        }
        Set<BlockPos> logs = new LinkedHashSet<>(
                scan(helper.getLevel(), origin, state -> state.is(BlockTags.LOGS)));
        if (logs.size() < 5) {
            helper.fail("only " + logs.size() + " logs to connect: " + logs);
            return;
        }
        int reached = connectedLogs(helper.getLevel(), lowest(logs), logs);
        if (reached != logs.size()) {
            helper.fail((logs.size() - reached) + " of " + logs.size()
                    + " logs are not face-connected to the base: " + logs);
            return;
        }
        helper.succeed();
    }

    /**
     * The bend turns the wood: a sideways step writes a log with the lean axis and a vertical step
     * keeps {@code y}. Each horizontal log has a vertical log behind it, which is what makes the
     * staircase one piece of wood.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void bentLogsTakeTheLeanAxis(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 2);
        if (!TreeTestScenes.place(helper, tree(helper, BENT_TRUNK, FROND), origin)) {
            helper.fail("the curved palm reported no placement");
            return;
        }
        List<BlockPos> logs = scan(helper.getLevel(), origin, state -> state.is(BlockTags.LOGS));
        int bent = 0;
        int travelled = 0;
        for (BlockPos pos : logs) {
            BlockState state = helper.getLevel().getBlockState(pos);
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            if (axis == Direction.Axis.Y) {
                continue;
            }
            bent++;
            travelled = Math.max(travelled, Math.abs(pos.getX() - origin.getX())
                    + Math.abs(pos.getZ() - origin.getZ()));
            // The horizontal log is the one-block step of a bent level, so the vertical log of the
            // same level sits one block back along the same axis. Which side that is depends on the
            // direction the stem happened to lean, so both ends of the axis have to be looked at.
            BlockPos back = pos.relative(axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH);
            BlockPos forth = pos.relative(axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH);
            if (!isVerticalLog(helper.getLevel(), back) && !isVerticalLog(helper.getLevel(), forth)) {
                helper.fail("the bent log " + pos + " (axis " + axis + ") has no vertical log at "
                        + back + " or " + forth + ": " + helper.getLevel().getBlockState(back)
                        + " / " + helper.getLevel().getBlockState(forth));
                return;
            }
        }
        if (bent == 0) {
            helper.fail("no log took a horizontal axis, so the trunk never bends: " + logs);
            return;
        }
        if (travelled > CurvedTrunkPlacer.MAX_HORIZONTAL_TRAVEL) {
            helper.fail("the stem reached " + travelled + " blocks out, past the budget of "
                    + CurvedTrunkPlacer.MAX_HORIZONTAL_TRAVEL);
            return;
        }
        helper.succeed();
    }

    /**
     * Frond leaves all reach the trunk through face contact in six steps or fewer, and none of them
     * decays: what the ticker will resolve is decided before the tick happens.
     *
     * <p>The check has to walk the neighbour graph, not read {@code LeavesBlock.DISTANCE}: a freshly
     * grown leaf still holds the generation-time pin of 5 whatever the shape is, so a distance
     * read at twenty ticks says nothing.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 400)
    public static void frondLeavesStayWithinSixStepsAndSurvive(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 3);
        if (!TreeTestScenes.place(helper, tree(helper, BENT_TRUNK, FROND), origin)) {
            helper.fail("the curved palm reported no placement");
            return;
        }
        requireCrownOnWood(helper, origin, "bent palm");
        int grown = leaves(helper.getLevel(), origin).size();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        helper.getLevel().setChunkForced(chunkX, chunkZ, true);
        helper.runAfterDelay(300, () -> {
            helper.getLevel().setChunkForced(chunkX, chunkZ, false);
            int now = leaves(helper.getLevel(), origin).size();
            if (now < grown) {
                helper.fail((grown - now) + " of " + grown
                        + " frond leaves decayed within 300 ticks");
                return;
            }
            helper.succeed();
        });
    }

    /** Removing every log leaves the frond leaves at the decay distance, like a vanilla tree. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void breakingAllLogsPushesLeavesToDistanceSeven(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 4);
        if (!TreeTestScenes.place(helper, tree(helper, BENT_TRUNK, FROND), origin)) {
            helper.fail("the curved palm reported no placement");
            return;
        }
        List<BlockPos> logs = scan(helper.getLevel(), origin, state -> state.is(BlockTags.LOGS));
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        helper.getLevel().setChunkForced(chunkX, chunkZ, true);
        helper.runAfterDelay(20, () -> {
            ServerLevel level = helper.getLevel();
            for (BlockPos pos : logs) {
                level.removeBlock(pos, false);
            }
            helper.runAfterDelay(20, () -> {
                level.setChunkForced(chunkX, chunkZ, false);
                List<BlockPos> remaining = leaves(level, origin);
                if (remaining.isEmpty()) {
                    helper.fail("every leaf vanished with the logs, so the distance check is empty");
                    return;
                }
                for (BlockPos pos : remaining) {
                    int distance = level.getBlockState(pos).getValue(LeavesBlock.DISTANCE);
                    if (distance < 7) {
                        helper.fail("leaf " + pos + " kept distance " + distance
                                + " after its logs were removed");
                        return;
                    }
                }
                helper.succeed();
            });
        });
    }

    /**
     * The top view is eight-pointed. A cross would leave the diagonals empty; every one of the four
     * diagonal fronds reaches its corner cell.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void frondsFillTheDiagonalsSoTheCrownIsNotACross(GameTestHelper helper) {
        BlockPos stack = scene(helper, FIRST_SLOT + 5);
        if (!TreeTestScenes.place(helper, tree(helper, STRAIGHT_TRUNK, FROND_FLAT), stack)) {
            helper.fail("the straight palm reported no placement");
            return;
        }
        BlockPos tip = stack.above(7);
        int[][] corners = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
        for (int[] corner : corners) {
            BlockPos pos = tip.offset(corner[0], 0, corner[1]);
            if (!helper.getLevel().getBlockState(pos).is(Blocks.OAK_LEAVES)) {
                helper.fail("no leaf at the diagonal " + pos + ", so the crown reads as a cross");
                return;
            }
        }
        // The cardinals have to be there too, so the diagonals are fronds and not a 2x2 blob.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = tip.relative(direction, 4);
            if (!helper.getLevel().getBlockState(pos).is(Blocks.OAK_LEAVES)) {
                helper.fail("no leaf at the cardinal " + pos + ", so a frond is missing");
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A crown lifted by {@code offset} still touches the trunk.
     *
     * <p>Leaf distance follows face contact only, so a crown raised one block too far is reached
     * through a diagonal and every leaf of it is past the sixth step — invisible to a distance check
     * while the leaves keep their generation-time pin. This walks the real neighbour graph instead:
     * every leaf has to be face-connected to a log within {@link FrondFoliagePlacer#MAX_FROND_REACH}
     * steps.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void liftedCrownStaysFaceConnectedToTheTrunk(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 6);
        if (!TreeTestScenes.place(helper, tree(helper, BENT_TRUNK, FROND_LIFTED), origin)) {
            helper.fail("the lifted palm reported no placement");
            return;
        }
        List<BlockPos> logs = scan(helper.getLevel(), origin, state -> state.is(BlockTags.LOGS));
        List<BlockPos> grown = leaves(helper.getLevel(), origin);
        if (logs.size() < 5 || grown.size() < 8) {
            helper.fail("the lifted palm grew " + logs.size() + " logs and " + grown.size()
                    + " leaves, too little to check the crown");
            return;
        }
        BlockPos tip = highest(logs);
        // The top level of a bent stem is two logs wide, and the crown hangs on the one the trunk
        // ended on; either of them having leaves directly above is enough to show contact.
        boolean attached = logs.stream()
                .filter(p -> p.getY() == tip.getY())
                .anyMatch(p -> helper.getLevel().getBlockState(p.above()).is(Blocks.OAK_LEAVES));
        if (!attached) {
            helper.fail("no leaf above any top log (" + tip.getY()
                    + "), so the lift left the crown detached from the wood");
            return;
        }
        java.util.Map<BlockPos, Integer> steps = faceStepsFromLogs(helper.getLevel(), grown);
        for (BlockPos leaf : grown) {
            Integer distance = steps.get(leaf);
            if (distance == null) {
                helper.fail("leaf " + leaf + " shares no face with the trunk or with another "
                        + "reachable leaf, so the crown hangs on a diagonal");
                return;
            }
            if (distance > FrondFoliagePlacer.MAX_FROND_REACH) {
                helper.fail("leaf " + leaf + " is " + distance + " face steps from the trunk, past "
                        + FrondFoliagePlacer.MAX_FROND_REACH + ", so it will decay");
                return;
            }
        }
        helper.succeed();
    }

    // --- connected-crown checks for the cases that used to leave orphans -----------------

    /**
     * The six-step budget still holds when the climb comes out of it: {@code rise} 2 leaves four of
     * the six steps for the reach, so the droop the JSON asks for has to be trimmed away instead of
     * written past the sixth step.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void riseTwoCrownStaysOnWood(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 7);
        if (!TreeTestScenes.place(helper, tree(helper, STRAIGHT_TRUNK, FROND_RISE_TWO), origin)) {
            helper.fail("the rise 2 palm reported no placement");
            return;
        }
        requireCrownOnWood(helper, origin, "rise 2");
        helper.succeed();
    }

    /**
     * The diagonals at full reach: five steps out alternating two axes, which is where a placer that
     * skips a cell shows up as a gap between two staircases.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void fullLengthDiagonalFrondsStayOnWood(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 8);
        if (!TreeTestScenes.place(helper, tree(helper, STRAIGHT_TRUNK, FROND_LONG), origin)) {
            helper.fail("the long frond palm reported no placement");
            return;
        }
        int furthest = 0;
        for (BlockPos leaf : leaves(helper.getLevel(), origin)) {
            furthest = Math.max(furthest, Math.abs(leaf.getX() - origin.getX())
                    + Math.abs(leaf.getZ() - origin.getZ()));
        }
        if (furthest < 5) {
            helper.fail("the longest frond reached only " + furthest
                    + " blocks out, so the diagonal case is empty");
            return;
        }
        requireCrownOnWood(helper, origin, "full length fronds");
        helper.succeed();
    }

    /**
     * A block in the middle of one frond ends that frond instead of letting it jump the gap: leaves
     * written past the block touch the chain only on an edge, land past the sixth step, and decay on
     * the first tick.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void blockedFrondStopsBeforeTheGap(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 9);
        BlockPos tip = origin.above(7);
        // The +x frond climbs one block, then walks out; this stone is its third cell. It sits far
        // enough out that no diagonal frond reaches the cells behind it, so a frond that jumped the
        // gap would leave a genuinely unreachable island.
        BlockPos wall = tip.offset(2, 1, 0);
        helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
        if (!TreeTestScenes.place(helper, tree(helper, STRAIGHT_TRUNK, FROND), origin)) {
            helper.fail("the blocked frond palm reported no placement");
            return;
        }
        requireCrownOnWood(helper, origin, "blocked frond");
        if (!helper.getLevel().getBlockState(tip.offset(1, 1, 0)).is(Blocks.OAK_LEAVES)) {
            helper.fail("no leaf at " + tip.offset(1, 1, 0)
                    + ", so the frond stopped before the wall and the case is untested");
            return;
        }
        if (helper.getLevel().getBlockState(tip.offset(3, 1, 0)).is(Blocks.OAK_LEAVES)) {
            helper.fail("a leaf sits at " + tip.offset(3, 1, 0) + ", past the wall at " + wall
                    + ", so the frond jumped the gap");
            return;
        }
        helper.succeed();
    }

    /**
     * A stem that runs into a neighbour stops at the last log it placed, and the crown growing from
     * that shorter tip still has to hang on the wood — the walled-off step sits beside the tip, so a
     * frond of that side used to walk on past it.
     *
     * <p>The obstacle sits one block out, which the default {@code two_layers_feature_size} reads as
     * "no room to grow": the feature would return before the trunk placer ran. These scenes therefore
     * ask for {@link TreeTestScenes#OPEN_MINIMUM_SIZE}, so the trunk placer itself has to stop.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void blockedTrunkStopsWithItsCrownOnTheLastLog(GameTestHelper helper) {
        int slot = FIRST_SLOT + 10;
        // The lean direction and the level of the first sideways step both come out of the seed, so
        // the test grows the same palm once undisturbed and walls off the step it really took.
        BlockPos probeOrigin = scene(helper, slot);
        if (!TreeTestScenes.place(helper,
                treeBesideObstacles(helper, BENT_TRUNK, FROND_FLAT), probeOrigin)) {
            helper.fail("the probe palm reported no placement");
            return;
        }
        int probeLogs = scan(helper.getLevel(), probeOrigin, state -> state.is(BlockTags.LOGS)).size();
        BlockPos step = firstSidewaysLog(helper.getLevel(),
                scan(helper.getLevel(), probeOrigin, state -> state.is(BlockTags.LOGS)), probeOrigin);
        if (step == null) {
            helper.fail("the probe palm never stepped sideways, so there is no step to wall off");
            return;
        }

        BlockPos origin = scene(helper, slot);
        helper.getLevel().setBlock(step, Blocks.STONE.defaultBlockState(), 2);
        if (!TreeTestScenes.place(helper,
                treeBesideObstacles(helper, BENT_TRUNK, FROND_FLAT), origin)) {
            helper.fail("the blocked palm reported no placement");
            return;
        }
        int logs = scan(helper.getLevel(), origin, state -> state.is(BlockTags.LOGS)).size();
        if (logs >= probeLogs) {
            helper.fail("the wall at " + step + " did not stop the stem: " + logs
                    + " logs against the open palm's " + probeLogs);
            return;
        }
        requireCrownOnWood(helper, origin, "blocked trunk");
        helper.succeed();
    }

    /**
     * A palm grown from a real region-sized scene: the whole tree — wood and leaves — stays inside
     * the 16 blocks a feature's 3x3 chunk region covers in each direction, and at least one of the
     * seeds spends the stem's whole sideways budget, so the check reaches the edge it guards.
     *
     * <p>The short stems the other tests grow cannot travel 11 blocks: {@code BENT_TRUNK} has 8
     * levels, so a constant-only comparison here would stay true whatever the stem did. This test
     * grows the tallest stem the type allows, with the widest frond, and reads the blocks back.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 1200)
    public static void tallestStemAndCrownStayInsideTheFeatureRegion(GameTestHelper helper) {
        int spentTheBudget = 0;
        for (int i = 0; i < TALL_SEEDS.length; i++) {
            long seed = TALL_SEEDS[i];
            String scene = "seed " + Long.toHexString(seed);
            BlockPos origin = scene(helper, FIRST_SLOT + 20 + i, REGION_SCAN_RADIUS,
                    TALL_SCENE_HEIGHT);
            if (!TreeTestScenes.place(helper, tree(helper, TALL_BENT_TRUNK, FROND_LONG),
                    origin, seed)) {
                helper.fail(scene + ": the tall palm reported no placement");
                return;
            }
            List<BlockPos> logs = scan(helper.getLevel(), origin, REGION_SCAN_RADIUS,
                    TALL_SCENE_HEIGHT, state -> state.is(BlockTags.LOGS));
            List<BlockPos> grown = leaves(helper.getLevel(), origin, REGION_SCAN_RADIUS,
                    TALL_SCENE_HEIGHT);
            if (logs.size() < 10 || grown.size() < 16) {
                helper.fail(scene + ": " + logs.size() + " logs and " + grown.size()
                        + " leaves grew, too little to measure the reach");
                return;
            }

            int logReach = 0;
            int travel = 0;
            for (BlockPos log : logs) {
                logReach = Math.max(logReach, horizontalChebyshev(log, origin));
                travel = Math.max(travel, horizontalManhattan(log, origin));
            }
            int leafReach = 0;
            for (BlockPos leaf : grown) {
                leafReach = Math.max(leafReach, horizontalChebyshev(leaf, origin));
            }
            if (logReach > REGION_REACH || leafReach > REGION_REACH) {
                helper.fail(scene + ": the tree reached " + logReach + " blocks with its wood and "
                        + leafReach + " with its leaves, past the " + REGION_REACH
                        + " the 3x3 chunk region holds (logs " + logs.size() + ")");
                return;
            }
            if (travel == CurvedTrunkPlacer.MAX_HORIZONTAL_TRAVEL) {
                spentTheBudget++;
            }
        }
        if (spentTheBudget == 0) {
            helper.fail("none of the " + TALL_SEEDS.length + " tall palms travelled "
                    + CurvedTrunkPlacer.MAX_HORIZONTAL_TRAVEL + " blocks sideways, so no crown ever"
                    + " reached the region edge and this test checks nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * A sideways step that runs into a wall does not spend the stem's budget: the stem steps back,
     * climbs, and takes the step one level up, so it still walks the full
     * {@link CurvedTrunkPlacer#MAX_HORIZONTAL_TRAVEL} blocks.
     *
     * <p>Budgets that count a failed attempt would leave this tree one block short. The wall is put
     * on the first sideways cell of a stem grown once undisturbed with the same seed, which is the
     * only cell the stem is known to attempt; {@code space_check} is off so the wall redirects the
     * stem instead of ending it.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 1200)
    public static void blockedSidewaysStepKeepsTheTravelBudget(GameTestHelper helper) {
        for (int i = 0; i < TALL_SEEDS.length; i++) {
            long seed = TALL_SEEDS[i];
            String scene = "seed " + Long.toHexString(seed);
            int probeSlot = FIRST_SLOT + 30 + i * 2;
            BlockPos probeOrigin = scene(helper, probeSlot, REGION_SCAN_RADIUS, TALL_SCENE_HEIGHT);
            if (!TreeTestScenes.place(helper,
                    treeBesideObstacles(helper, TALL_OPEN_TRUNK, FROND_LONG), probeOrigin, seed)) {
                helper.fail(scene + ": the probe palm reported no placement");
                return;
            }
            List<BlockPos> probeLogs = scan(helper.getLevel(), probeOrigin, REGION_SCAN_RADIUS,
                    TALL_SCENE_HEIGHT, state -> state.is(BlockTags.LOGS));
            int probeTravel = 0;
            BlockPos firstStep = null;
            for (BlockPos log : probeLogs) {
                probeTravel = Math.max(probeTravel, horizontalManhattan(log, probeOrigin));
                if (horizontalManhattan(log, probeOrigin) == 1
                        && !isVerticalLog(helper.getLevel(), log)) {
                    firstStep = log;
                }
            }
            if (probeTravel < CurvedTrunkPlacer.MAX_HORIZONTAL_TRAVEL || firstStep == null) {
                // this seed never spends the budget, so it cannot show what a wall costs
                continue;
            }

            BlockPos origin = scene(helper, probeSlot + 1, REGION_SCAN_RADIUS, TALL_SCENE_HEIGHT);
            BlockPos relative = firstStep.subtract(probeOrigin);
            BlockPos wall = origin.offset(relative.getX(), relative.getY(), relative.getZ());
            helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 2);
            if (!TreeTestScenes.place(helper,
                    treeBesideObstacles(helper, TALL_OPEN_TRUNK, FROND_LONG), origin, seed)) {
                helper.fail(scene + ": the blocked palm reported no placement");
                return;
            }
            List<BlockPos> logs = scan(helper.getLevel(), origin, REGION_SCAN_RADIUS,
                    TALL_SCENE_HEIGHT, state -> state.is(BlockTags.LOGS));
            if (logs.stream().anyMatch(p -> p.equals(wall))) {
                helper.fail(scene + ": the stem put a log into the walled cell " + wall
                        + ", so the case is untested");
                return;
            }
            int travel = 0;
            for (BlockPos log : logs) {
                travel = Math.max(travel, horizontalManhattan(log, origin));
            }
            if (travel < CurvedTrunkPlacer.MAX_HORIZONTAL_TRAVEL) {
                helper.fail(scene + ": with one sideways step walled off the stem travelled only "
                        + travel + " of its " + CurvedTrunkPlacer.MAX_HORIZONTAL_TRAVEL
                        + " blocks, so the blocked step spent budget it did not use");
                return;
            }
            helper.succeed();
            return;
        }
        helper.fail("none of the " + TALL_SEEDS.length + " tall palms spent its sideways budget,"
                + " so no step could be walled off");
    }

    /**
     * A frond that runs into another tree's leaves takes the cells: vanilla's
     * {@code replaceable_by_trees} tag contains {@code #minecraft:leaves}, so an existing leaf is a
     * valid tree position and {@link com.kuronami.isekaiapi.tree.LeafPlacing#placePinned} writes the
     * palm's own leaf over it. The palm does not stop at foreign leaves the way it stops at stone.
     *
     * <p>The scene is a straight palm whose +x arm and whose +x+z diagonal are pre-filled with jungle
     * leaves. Every one of those cells has to end up as the palm's leaf, and the whole crown still
     * has to hang off the trunk through face contact within six steps: that is what the ticker
     * resolves once the generation-time pin is gone.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void frondTakesForeignLeavesAndKeepsTheCrownConnected(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 11);
        BlockPos tip = origin.above(7);
        BlockPos[] foreign = {
                tip.offset(2, 0, 0), tip.offset(3, 0, 0),   // on the +x arm
                tip.offset(1, 0, 1),                        // where the +x+z diagonal crosses
        };
        for (BlockPos pos : foreign) {
            helper.getLevel().setBlock(pos, Blocks.JUNGLE_LEAVES.defaultBlockState(), 2);
        }
        if (!TreeTestScenes.place(helper,
                treeBesideObstacles(helper, STRAIGHT_TRUNK, FROND_FLAT), origin)) {
            helper.fail("the palm beside the foreign leaves reported no placement");
            return;
        }
        for (BlockPos pos : foreign) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (!state.is(Blocks.OAK_LEAVES)) {
                helper.fail("the cell " + pos + " holds " + state
                        + " instead of the palm's leaf, so the frond did not take the foreign leaf");
                return;
            }
        }
        requireCrownOnWood(helper, origin, "foreign leaves");
        helper.succeed();
    }

    // --- scene helpers -------------------------------------------------------------

    /**
     * Every leaf in the scene must hang off the trunk through face contact within
     * {@link FrondFoliagePlacer#MAX_FROND_REACH} steps.
     *
     * <p>This walks the real neighbour graph instead of reading {@code LeavesBlock.DISTANCE}: a
     * leaf that has not ticked yet still holds the pinned 5, so a distance read on a freshly grown
     * tree passes whatever shape it is. The failure this catches is the one that shows up later,
     * when the ticker resolves the real distance and the leaf decays.
     */
    private static void requireCrownOnWood(GameTestHelper helper, BlockPos origin, String scene) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> logs = scan(level, origin, state -> state.is(BlockTags.LOGS));
        List<BlockPos> grown = leaves(level, origin);
        if (logs.isEmpty()) {
            helper.fail(scene + ": no log grew, so there is no wood to hang the crown on");
            return;
        }
        if (grown.size() < 8) {
            helper.fail(scene + ": only " + grown.size()
                    + " leaves grew, too few to check the crown");
            return;
        }
        java.util.Map<BlockPos, Integer> steps = faceStepsFromLogs(level, grown);
        for (BlockPos leaf : grown) {
            Integer distance = steps.get(leaf);
            if (distance == null) {
                helper.fail(scene + ": leaf " + leaf + " shares no face with the trunk or with"
                        + " another reachable leaf, so the crown hangs on a diagonal and will decay");
                return;
            }
            if (distance > FrondFoliagePlacer.MAX_FROND_REACH) {
                helper.fail(scene + ": leaf " + leaf + " is " + distance + " face steps from the"
                        + " trunk, past " + FrondFoliagePlacer.MAX_FROND_REACH + ", so it will decay");
                return;
            }
        }
    }

    /** The log of the lowest bent level: where the stem first left the vertical. */
    private static BlockPos firstSidewaysLog(ServerLevel level, List<BlockPos> logs, BlockPos origin) {
        BlockPos best = null;
        int bestReach = -1;
        for (BlockPos pos : logs) {
            if (level.getBlockState(pos).getValue(BlockStateProperties.AXIS) == Direction.Axis.Y) {
                continue;
            }
            int reach = Math.abs(pos.getX() - origin.getX())
                    + Math.abs(pos.getZ() - origin.getZ());
            if (best == null || pos.getY() < best.getY()
                    || pos.getY() == best.getY() && reach > bestReach) {
                best = pos;
                bestReach = reach;
            }
        }
        return best;
    }

    private static boolean isVerticalLog(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(BlockTags.LOGS)
                && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
    }

    /**
     * Face-step distance from the nearest log to every leaf reachable through logs and leaves.
     *
     * <p>A leaf touching a log is step 1, which is what {@code LeavesBlock.DISTANCE} counts, so the
     * numbers here are the distances the ticker would resolve.
     */
    private static java.util.Map<BlockPos, Integer> faceStepsFromLogs(
            ServerLevel level, List<BlockPos> leaves) {
        Set<BlockPos> leafSet = new LinkedHashSet<>(leaves);
        java.util.Map<BlockPos, Integer> seen = new java.util.HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos leaf : leaves) {
            for (Direction direction : Direction.values()) {
                if (level.getBlockState(leaf.relative(direction)).is(BlockTags.LOGS)) {
                    seen.put(leaf, 1);
                    queue.add(leaf);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            int next = seen.get(pos) + 1;
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                if (leafSet.contains(neighbour) && !seen.containsKey(neighbour)) {
                    seen.put(neighbour, next);
                    queue.add(neighbour);
                }
            }
        }
        return seen;
    }

    /** Builds the slot's platform and clears a wide, tall volume so a rerun starts equal. */
    private static BlockPos scene(GameTestHelper helper, int slot) {
        return scene(helper, slot, SCAN_RADIUS, SCAN_HEIGHT);
    }

    /** The same scene with an explicit box, for the stems that grow past the usual crown. */
    private static BlockPos scene(GameTestHelper helper, int slot, int radius, int height) {
        BlockPos origin = TreeTestScenes.prepare(helper, slot);
        ServerLevel level = helper.getLevel();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.setBlock(origin.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy <= height; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return origin;
    }

    private static ConfiguredFeature<?, ?> tree(GameTestHelper helper, String trunkJson,
                                                String foliageJson) {
        return TreeTestScenes.parseTree(helper, "", foliageJson, trunkJson);
    }

    /**
     * The same tree, parsed with a {@code minimum_size} that ignores the neighbours, so a test can
     * build an obstacle beside the trunk without the feature refusing to grow at all.
     */
    private static ConfiguredFeature<?, ?> treeBesideObstacles(GameTestHelper helper, String trunkJson,
                                                               String foliageJson) {
        return TreeTestScenes.parseTree(helper, "", foliageJson, trunkJson,
                TreeTestScenes.OPEN_MINIMUM_SIZE);
    }

    /** Every block around a tree that matches {@code match}, in the scan box. */
    private static List<BlockPos> scan(ServerLevel level, BlockPos origin,
                                       java.util.function.Predicate<BlockState> match) {
        return scan(level, origin, SCAN_RADIUS, SCAN_HEIGHT, match);
    }

    /** The same scan with an explicit box. */
    private static List<BlockPos> scan(ServerLevel level, BlockPos origin, int radius, int height,
                                       java.util.function.Predicate<BlockState> match) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, 0, -radius),
                origin.offset(radius, height, radius))) {
            if (match.test(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    private static List<BlockPos> leaves(ServerLevel level, BlockPos origin) {
        return scan(level, origin, state -> state.getBlock() instanceof LeavesBlock);
    }

    private static List<BlockPos> leaves(ServerLevel level, BlockPos origin, int radius, int height) {
        return scan(level, origin, radius, height, state -> state.getBlock() instanceof LeavesBlock);
    }

    /** Largest horizontal offset on either axis: what the 3x3 chunk region is measured in. */
    private static int horizontalChebyshev(BlockPos pos, BlockPos origin) {
        return Math.max(Math.abs(pos.getX() - origin.getX()), Math.abs(pos.getZ() - origin.getZ()));
    }

    /** Horizontal blocks walked from the origin: what the stem's sideways budget counts. */
    private static int horizontalManhattan(BlockPos pos, BlockPos origin) {
        return Math.abs(pos.getX() - origin.getX()) + Math.abs(pos.getZ() - origin.getZ());
    }

    private static BlockPos highest(List<BlockPos> positions) {
        BlockPos best = positions.get(0);
        for (BlockPos pos : positions) {
            if (pos.getY() > best.getY()) {
                best = pos;
            }
        }
        return best;
    }

    private static BlockPos lowest(Set<BlockPos> positions) {
        BlockPos best = null;
        for (BlockPos pos : positions) {
            if (best == null || pos.getY() < best.getY()) {
                best = pos;
            }
        }
        return best;
    }

    /** Six-connected flood fill over {@code logs} from {@code start}; returns how many it reached. */
    private static int connectedLogs(ServerLevel level, BlockPos start, Set<BlockPos> logs) {
        Set<BlockPos> seen = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (logs.contains(next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen.size();
    }
}
