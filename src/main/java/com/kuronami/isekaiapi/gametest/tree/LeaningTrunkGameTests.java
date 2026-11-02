package com.kuronami.isekaiapi.gametest.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.tree.LeaningTrunkPlacer;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Acceptance for {@code isekai_api:leaning}'s T11 fields: {@code space_check} (a blocked lean stops
 * instead of dangling a crown in mid-air), {@code slant_curve} (exponential sideways stride) and
 * {@code min_height} (skip trees shorter than the trunk).
 *
 * <p>Most checks call {@link TrunkPlacer#placeTrunk} against an in-memory {@link LevelSimulatedReader}
 * instead of growing a tree in the test world: the placer's output is a list of crown attachments,
 * and a fake level can mark one cell as occupied without that obstruction also changing leaf decay
 * or colliding with another test. Two tests grow real trees to cover the whole codec-to-blocks path.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class LeaningTrunkGameTests {

    /** Height handed to {@code placeTrunk}; the trunk configs below use {@code base_height} 6. */
    private static final int TREE_HEIGHT = 6;

    /** Fixed seed so every run bends the same way and visits the same cells. */
    private static final long SEED = 0xA11CE5EEDL;

    /** A vanilla blob crown, used because these tests only care about the trunk geometry. */
    private static final String BLOB_FOLIAGE = """
            {
              "type": "minecraft:blob_foliage_placer",
              "height": 3,
              "offset": 0,
              "radius": 2
            }
            """;

    /** The old JSON: no T11 fields at all. */
    private static final String OLD_SHAPE = trunk("");

    /** Old geometry spelled out: one block per step, blocked leans still crowned. */
    private static final String SPACE_CHECK_OFF = trunk("""
            , "slant_curve": 1.0, "space_check": false, "min_height": 4
            """);

    /** The defaults written out, to prove omitted fields mean the same thing. */
    private static final String EXPLICIT_DEFAULTS = trunk("""
            , "slant_curve": 1.0, "space_check": true, "min_height": 4
            """);

    /** Exponential bend: 2.0 doubles the stride each step. */
    private static final String STEEP_CURVE = trunk("""
            , "slant_curve": 2.0
            """);

    /**
     * The longest stalk the codec allows at the steepest curve: {@code 1+2+4+...+2^16} = 131,071
     * blocks sideways if the stride were followed without a budget.
     */
    private static final String STEEP_AND_LONG = """
            {
              "type": "isekai_api:leaning",
              "base_height": 6,
              "height_rand_a": 0,
              "height_rand_b": 0,
              "convert_ground": false,
              "lean_length": {"type": "minecraft:constant", "value": 16},
              "slant_curve": 2.0
            }
            """;

    private LeaningTrunkGameTests() {}

    /**
     * A leaning trunk JSON body with the shared shape and an extra field tail.
     *
     * <p>{@code convert_ground} is off so {@code placeTrunk} never reaches NeoForge's
     * {@code setDirtAt}, which casts the simulated level to a full {@code LevelReader}; the fake
     * level only needs the four {@code LevelSimulatedReader} methods, and ground conversion is not
     * what these tests are about.
     */
    private static String trunk(String extraFields) {
        return """
                {
                  "type": "isekai_api:leaning",
                  "base_height": 6,
                  "height_rand_a": 0,
                  "height_rand_b": 0,
                  "convert_ground": false,
                  "lean_length": {"type": "minecraft:constant", "value": 4}__EXTRA__
                }
                """.replace("__EXTRA__", extraFields);
    }

    private static RegistryOps<JsonElement> ops(GameTestHelper helper) {
        return RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
    }

    /** Parses a tree with no decorators and the given trunk, and returns its configuration. */
    private static TreeConfiguration config(GameTestHelper helper, String trunkJson) {
        ConfiguredFeature<?, ?> feature = TreeTestScenes.parseTree(helper, "", BLOB_FOLIAGE, trunkJson);
        return (TreeConfiguration) feature.config();
    }

    /**
     * Runs {@code placeTrunk} against {@code level} and returns the crown sites it emitted.
     *
     * <p>The setter copies the position: {@code TrunkPlacer} hands the placer's own mutable cursor to
     * the consumer, so a map keyed by it keeps one object that moves, and insertion order is lost.
     */
    private static List<BlockPos> attachments(TreeConfiguration config, LevelSimulatedReader level,
                                              BlockPos origin, Map<BlockPos, BlockState> writes) {
        List<FoliagePlacer.FoliageAttachment> placements = config.trunkPlacer.placeTrunk(
                level, (pos, state) -> writes.put(pos.immutable(), state),
                RandomSource.create(SEED), TREE_HEIGHT, origin, config);
        List<BlockPos> positions = new ArrayList<>(placements.size());
        for (FoliagePlacer.FoliageAttachment placement : placements) {
            positions.add(placement.pos());
        }
        return positions;
    }

    /** Horizontal (Manhattan) offset; the stalk only moves along one axis. */
    private static int sideways(BlockPos origin, BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) + Math.abs(pos.getZ() - origin.getZ());
    }

    private static int maxSideways(BlockPos origin, List<BlockPos> positions) {
        int max = 0;
        for (BlockPos pos : positions) {
            max = Math.max(max, sideways(origin, pos));
        }
        return max;
    }

    /**
     * With {@code space_check} on, an occupied cell ends the stalk: the crown stays on the part that
     * was actually logged. With it off, a crown is still attached to the occupied cell — the
     * floating-crown bug this field fixes.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void spaceCheckStopsTheStalkAtAnOccupiedCell(GameTestHelper helper) {
        TreeConfiguration free = config(helper, OLD_SHAPE);
        BlockPos origin = new BlockPos(0, 100, 0);
        List<BlockPos> freeSites = attachments(free, new FakeLevel(Set.of()), origin, new HashMap<>());
        if (maxSideways(origin, freeSites) < 3) {
            helper.fail("the free tree did not lean 3 blocks out: " + freeSites);
            return;
        }
        BlockPos blocked = null;
        for (BlockPos pos : freeSites) {
            if (sideways(origin, pos) == 3) {
                blocked = pos;
            }
        }
        if (blocked == null) {
            helper.fail("no crown site 3 blocks out: " + freeSites);
            return;
        }

        Map<BlockPos, BlockState> stoppedWrites = new HashMap<>();
        List<BlockPos> stopped = attachments(free, new FakeLevel(Set.of(blocked)), origin, stoppedWrites);
        if (stopped.contains(blocked)) {
            helper.fail("space_check left a crown site on the occupied cell " + blocked);
            return;
        }
        if (maxSideways(origin, stopped) != 2) {
            helper.fail("space_check should stop the crown 2 blocks out, got " + stopped);
            return;
        }
        if (stopped.size() >= freeSites.size()) {
            helper.fail("space_check did not shorten the crown: " + stopped.size()
                    + " sites vs " + freeSites.size() + " free");
            return;
        }

        TreeConfiguration legacy = config(helper, SPACE_CHECK_OFF);
        Map<BlockPos, BlockState> legacyWrites = new HashMap<>();
        List<BlockPos> legacySites = attachments(legacy,
                new FakeLevel(Set.of(blocked)), origin, legacyWrites);
        if (!legacySites.equals(freeSites)) {
            helper.fail("space_check:false changed the crown sites: " + legacySites
                    + " vs " + freeSites);
            return;
        }
        if (legacyWrites.containsKey(blocked)) {
            helper.fail("the occupied cell was logged, so its crown was never floating");
            return;
        }
        helper.succeed();
    }

    /**
     * {@code slant_curve} lengthens each step, so the same {@code lean_length} reaches further. The
     * horizontal budget bounds that reach, so the curved stalk cannot also keep the straight one's
     * crown count — it is cut one step before its last intended site. What must hold is that the
     * budget only ever removes sites.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void slantCurveReachesFurtherWithTheSameCrownCount(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 100, 0);
        List<BlockPos> straight = attachments(config(helper, EXPLICIT_DEFAULTS),
                new FakeLevel(Set.of()), origin, new HashMap<>());
        List<BlockPos> curved = attachments(config(helper, STEEP_CURVE),
                new FakeLevel(Set.of()), origin, new HashMap<>());

        int straightMax = maxSideways(origin, straight);
        int curvedMax = maxSideways(origin, curved);
        // How much further is bounded by the horizontal budget, not by the old "+8 blocks"
        // expectation: with the cap the step counts are 1, 2, 4, 8 and the stalk is cut at 12.
        if (curvedMax <= straightMax) {
            helper.fail("slant_curve 2.0 should reach further than 1.0: "
                    + curvedMax + " vs " + straightMax);
            return;
        }
        if (curvedMax > LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL) {
            helper.fail("slant_curve 2.0 reached " + curvedMax + " blocks, past the budget of "
                    + LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL);
            return;
        }
        if (curved.size() > straight.size()) {
            helper.fail("slant_curve added crown sites: " + curved.size() + " vs "
                    + straight.size() + "; the budget can only cut the stalk short");
            return;
        }
        helper.succeed();
    }

    /**
     * The stride doubles each step, so the steepest curve on the longest stem has to stop at
     * {@link LeaningTrunkPlacer#MAX_HORIZONTAL_TRAVEL}; without the budget this walk is over a
     * hundred thousand blocks and leaves the 3x3 chunk region the feature stage holds.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void slantCurveTravelStopsAtTheLimit(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 100, 0);
        Map<BlockPos, BlockState> writes = new LinkedHashMap<>();
        List<BlockPos> sites = attachments(config(helper, STEEP_AND_LONG),
                new FakeLevel(Set.of()), origin, writes);

        int furthest = 0;
        for (BlockPos pos : writes.keySet()) {
            int offset = sideways(origin, pos);
            furthest = Math.max(furthest, offset);
            if (offset > LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL) {
                helper.fail("a log sits " + offset + " blocks out, past the budget of "
                        + LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL);
                return;
            }
        }
        if (furthest < LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL) {
            helper.fail("the stalk stopped at " + furthest + " blocks, before spending the "
                    + LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL + "-block budget");
            return;
        }
        int sitesMax = maxSideways(origin, sites);
        if (sitesMax > LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL) {
            helper.fail("a crown site sits " + sitesMax + " blocks out, past the budget");
            return;
        }
        helper.succeed();
    }

    /**
     * A stride wider than one block logs the cells between two crown sites. When the last of those
     * is occupied, the stalk stops there — and the returned tip has to be the last log that exists,
     * not the last step that started, or the crown sits behind wood the tree did grow.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void blockedStrideKeepsTheTipOnTheLastLog(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 100, 0);
        TreeConfiguration config = config(helper, STEEP_CURVE);
        Map<BlockPos, BlockState> freeWrites = new LinkedHashMap<>();
        List<BlockPos> freeSites = attachments(config, new FakeLevel(Set.of()), origin, freeWrites);
        Set<BlockPos> sites = new HashSet<>(freeSites);

        // The last stride-internal log that follows another one: blocking it leaves wood behind the
        // last crown site, which is the case that used to end the stalk on the wrong cell.
        List<BlockPos> order = new ArrayList<>(freeWrites.keySet());
        BlockPos blocked = null;
        for (int i = 1; i < order.size(); i++) {
            if (!sites.contains(order.get(i)) && !sites.contains(order.get(i - 1))) {
                blocked = order.get(i);
            }
        }
        if (blocked == null) {
            helper.fail("the steep trunk placed no stride-internal log after another one: " + order);
            return;
        }

        Map<BlockPos, BlockState> blockedWrites = new LinkedHashMap<>();
        List<BlockPos> blockedSites = attachments(config, new FakeLevel(Set.of(blocked)), origin,
                blockedWrites);
        BlockPos lastLog = null;
        for (BlockPos pos : blockedWrites.keySet()) {
            lastLog = pos;
        }
        if (lastLog == null) {
            helper.fail("blocking " + blocked.subtract(origin) + " left no log at all");
            return;
        }
        if (blockedWrites.containsKey(blocked)) {
            helper.fail("the occupied cell " + blocked.subtract(origin) + " was logged");
            return;
        }
        BlockPos tip = blockedSites.get(blockedSites.size() - 1);
        if (!tip.equals(lastLog)) {
            helper.fail("the tip is at " + tip.subtract(origin) + " but the stalk ends at "
                    + lastLog.subtract(origin) + ", so wood is left past the crown");
            return;
        }
        int tipOffset = sideways(origin, lastLog);
        for (BlockPos pos : blockedWrites.keySet()) {
            if (sideways(origin, pos) > tipOffset) {
                helper.fail("a log at " + pos.subtract(origin) + " sits past the tip at "
                        + lastLog.subtract(origin));
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A datapack written before these fields existed must produce exactly the same tree: the new
     * fields are additive and their defaults are the old behaviour.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void omittedFieldsProduceTheSameTreeAsTheOldShape(GameTestHelper helper) {
        // Both trees grow at the same origin: vanilla's leaf-distance pass walks a HashSet of
        // absolute positions, so the same shape at a different x/z can end up with different
        // distance values even when every block type and position matches.
        BlockPos origin = reset(helper, 30);
        List<String> omitted = grow(helper, OLD_SHAPE, origin);
        if (omitted.isEmpty()) {
            helper.fail("the tree with omitted fields placed nothing");
            return;
        }
        origin = reset(helper, 30);
        List<String> explicit = grow(helper, EXPLICIT_DEFAULTS, origin);
        if (!omitted.equals(explicit)) {
            List<String> differences = new java.util.ArrayList<>();
            int shared = Math.min(omitted.size(), explicit.size());
            for (int i = 0; i < shared && differences.size() < 6; i++) {
                if (!omitted.get(i).equals(explicit.get(i))) {
                    differences.add(omitted.get(i) + " vs " + explicit.get(i));
                }
            }
            helper.fail("defaults differ from the explicit old shape (" + omitted.size() + " vs "
                    + explicit.size() + " blocks): " + differences);
            return;
        }
        helper.succeed();
    }

    /** {@code min_height} skips a tree whose trunk would be shorter, and leaves the ground alone. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void minHeightSkipsAShorterTrunk(GameTestHelper helper) {
        String shortTrunk = """
                {
                  "type": "isekai_api:leaning",
                  "base_height": 3,
                  "height_rand_a": 0,
                  "height_rand_b": 0,
                  "lean_length": {"type": "minecraft:constant", "value": 2},
                  "min_height": 4
                }
                """;
        String allowedTrunk = shortTrunk.replace("\"min_height\": 4", "\"min_height\": 3");

        BlockPos skipped = reset(helper, 32);
        if (TreeTestScenes.place(helper, tree(helper, shortTrunk), skipped)) {
            helper.fail("min_height 4 reported placing a 3-high trunk");
            return;
        }
        int skippedLogs = logsNear(helper, skipped);
        if (skippedLogs != 0) {
            helper.fail("min_height 4 still placed " + skippedLogs + " logs for a 3-high trunk");
            return;
        }

        BlockPos allowed = reset(helper, 33);
        if (!TreeTestScenes.place(helper, tree(helper, allowedTrunk), allowed)) {
            helper.fail("min_height 3 rejected a 3-high trunk");
            return;
        }
        int allowedLogs = logsNear(helper, allowed);
        if (allowedLogs < 3) {
            helper.fail("min_height 3 allowed the trunk but only " + allowedLogs + " logs exist");
            return;
        }
        helper.succeed();
    }

    /** Out-of-range values have to fail decoding, not be clamped. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void codecRejectsOutOfRangeFields(GameTestHelper helper) {
        String[] bad = {
                trunk("""
                        , "slant_curve": 0.5
                        """),
                trunk("""
                        , "slant_curve": 2.5
                        """),
                trunk("""
                        , "min_height": 0
                        """),
                trunk("""
                        , "min_height": 17
                        """)
        };
        for (String json : bad) {
            boolean rejected = TrunkPlacer.CODEC.parse(ops(helper), JsonParser.parseString(json))
                    .result().isEmpty();
            if (!rejected) {
                helper.fail("decoded an out-of-range leaning trunk: " + json);
                return;
            }
        }
        helper.succeed();
    }

    // --- world-level helpers -------------------------------------------------------

    private static ConfiguredFeature<?, ?> tree(GameTestHelper helper, String trunkJson) {
        return TreeTestScenes.parseTree(helper, "", BLOB_FOLIAGE, trunkJson);
    }

    /** Clears a wide, tall volume around slot {@code slot} so a rerun starts from the same state. */
    private static BlockPos reset(GameTestHelper helper, int slot) {
        BlockPos origin = TreeTestScenes.prepare(helper, slot);
        ServerLevel level = helper.getLevel();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlock(origin.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 0; dy <= 17; dy++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        return origin;
    }

    /** Grows {@code trunkJson} at {@code origin} and returns its relative block layout, sorted. */
    private static List<String> grow(GameTestHelper helper, String trunkJson, BlockPos origin) {
        if (!TreeTestScenes.place(helper, tree(helper, trunkJson), origin)) {
            return List.of();
        }
        List<String> blocks = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(
                TreeTestScenes.boxMin(origin, 8), TreeTestScenes.boxMax(origin, 8, 16))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            blocks.add((pos.getX() - origin.getX()) + "," + (pos.getY() - origin.getY()) + ","
                    + (pos.getZ() - origin.getZ()) + "=" + state);
        }
        blocks.sort(String::compareTo);
        return blocks;
    }

    /** Logs in the box a test tree can reach. */
    private static int logsNear(GameTestHelper helper, BlockPos origin) {
        return TreeTestScenes.count(helper.getLevel(), TreeTestScenes.boxMin(origin, 8),
                TreeTestScenes.boxMax(origin, 8, 16), state -> state.is(BlockTags.LOGS));
    }

    /**
     * An empty world that reports exactly the cells in {@code occupied} as stone. Enough for
     * {@code placeTrunk}, which only asks "is this position free?" and never reads a heightmap.
     */
    private static final class FakeLevel implements LevelSimulatedReader {

        private final Set<BlockPos> occupied;

        FakeLevel(Set<BlockPos> occupied) {
            this.occupied = new HashSet<>(occupied);
        }

        @Override
        public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
            BlockState state = this.occupied.contains(pos)
                    ? Blocks.STONE.defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            return predicate.test(state);
        }

        @Override
        public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
            return false;
        }

        @Override
        public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos,
                                                                  BlockEntityType<T> type) {
            return Optional.empty();
        }

        @Override
        public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
            return pos;
        }
    }
}
