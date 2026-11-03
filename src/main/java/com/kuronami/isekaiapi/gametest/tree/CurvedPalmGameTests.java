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
 * generation. Slots 40-46 are reserved for this class.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class CurvedPalmGameTests {

    /** First slot owned by this class; one slot per test. */
    private static final int FIRST_SLOT = 40;

    /** How far the crown may spread; the box the tests scan around a tree. */
    private static final int SCAN_RADIUS = 10;

    /** Air cleared above the platform: trunk plus crown plus the drooping tips. */
    private static final int SCAN_HEIGHT = 20;

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
            // same level sits on the negative side of the same axis and keeps axis y.
            BlockPos back = pos.relative(axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH);
            BlockState behind = helper.getLevel().getBlockState(back);
            if (!behind.is(BlockTags.LOGS)
                    || behind.getValue(BlockStateProperties.AXIS) != Direction.Axis.Y) {
                helper.fail("the bent log " + pos + " (axis " + axis + ") has no vertical log at "
                        + back + ": " + behind);
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
     * Frond leaves all resolve to six or fewer steps from a log, and none of them decays: the
     * distance the ticker computes is the real one, not the pinned placeholder.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 400)
    public static void frondLeavesStayWithinSixStepsAndSurvive(GameTestHelper helper) {
        BlockPos origin = scene(helper, FIRST_SLOT + 3);
        if (!TreeTestScenes.place(helper, tree(helper, BENT_TRUNK, FROND), origin)) {
            helper.fail("the curved palm reported no placement");
            return;
        }
        int grown = leaves(helper.getLevel(), origin).size();
        if (grown < 8) {
            helper.fail("only " + grown + " leaves grew, so there is nothing to keep alive");
            return;
        }
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;
        helper.getLevel().setChunkForced(chunkX, chunkZ, true);
        helper.runAfterDelay(20, () -> {
            List<BlockPos> first = leaves(helper.getLevel(), origin);
            int tooFar = 0;
            int furthest = 0;
            for (BlockPos pos : first) {
                int distance = helper.getLevel().getBlockState(pos).getValue(LeavesBlock.DISTANCE);
                furthest = Math.max(furthest, distance);
                if (distance > FrondFoliagePlacer.MAX_FROND_REACH) {
                    tooFar++;
                }
            }
            if (tooFar > 0) {
                helper.fail(tooFar + " leaves resolved further than "
                        + FrondFoliagePlacer.MAX_FROND_REACH + " steps (furthest " + furthest
                        + "), so they will decay");
                return;
            }
            if (furthest < 2) {
                helper.fail("the farthest leaf is at distance " + furthest
                        + ", so the fronds barely leave the trunk");
                return;
            }
            helper.runAfterDelay(280, () -> {
                helper.getLevel().setChunkForced(chunkX, chunkZ, false);
                int now = leaves(helper.getLevel(), origin).size();
                if (now < first.size()) {
                    helper.fail((first.size() - now) + " of " + first.size()
                            + " frond leaves decayed within 300 ticks");
                    return;
                }
                helper.succeed();
            });
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

    // --- scene helpers -------------------------------------------------------------

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
        BlockPos origin = TreeTestScenes.prepare(helper, slot);
        ServerLevel level = helper.getLevel();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                level.setBlock(origin.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy <= SCAN_HEIGHT; dy++) {
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

    /** Every block around a tree that matches {@code match}, in the scan box. */
    private static List<BlockPos> scan(ServerLevel level, BlockPos origin,
                                       java.util.function.Predicate<BlockState> match) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SCAN_RADIUS, 0, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS))) {
            if (match.test(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    private static List<BlockPos> leaves(ServerLevel level, BlockPos origin) {
        return scan(level, origin, state -> state.getBlock() instanceof LeavesBlock);
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
