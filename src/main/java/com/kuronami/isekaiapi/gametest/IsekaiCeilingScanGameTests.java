package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.placementmodifier.DimensionGuardPlacementModifier;
import com.kuronami.isekaiapi.placementmodifier.SurfaceRelativeModifier;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Machine checks for the long-distance ceiling scan: "start at a fixed Y, walk down, stop at the
 * first air block below the ceiling, place nothing when the budget runs out".
 *
 * <p>This is the recipe {@code docs/DATAPACK_REFERENCE.md} documents for hanging features from a
 * roof more than 32 blocks up (a 192-tall nether: underside around Y 120, scan start Y 186). Every
 * part already exists — {@code isekai_api:surface_relative} carrying an
 * {@code isekai_api:world_floor} anchor with an explicit {@code start} and {@code max_scan}. The
 * tests pin the three properties that recipe leans on:
 *
 * <ul>
 *   <li>the budget is inclusive, so {@code max_scan} of {@code start - underside} reaches the
 *       underside and one step less does not ({@link #budgetIsInclusiveFromStartToUnderside},
 *       {@link #oneMoreBlockOfCeilingNeedsOneMoreStep})</li>
 *   <li>a start cell that is already air does <i>not</i> stop the scan — it keeps descending to
 *       the next body and reports that body's underside
 *       ({@link #airStartFallsThroughToLowerShelf}), which is why the documented chain gates on a
 *       block predicate first ({@link #predicateGateRejectsAirStart},
 *       {@link #documentedChainParsesAndRejectsAirStarts})</li>
 *   <li>only {@code isAir()} ends the scan, so lava and water count as body
 *       ({@link #lavaIsNotAirAndDoesNotEndTheScan})</li>
 * </ul>
 *
 * <p>Every column is cleared from the build floor to {@link #CLEAR_TO} before it is filled, so a
 * scan from {@link #START_Y} has exactly one body to find and an exact expected length.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiCeilingScanGameTests {

    /** Where the scan starts: the top of the ceiling body in the documented nether layout. */
    private static final int START_Y = 186;
    /** The air block under that ceiling — what the scan is supposed to report. */
    private static final int UNDERSIDE = 120;
    /** Exactly enough steps to walk from {@link #START_Y} down to {@link #UNDERSIDE}. */
    private static final int BUDGET = START_Y - UNDERSIDE;
    private static final int ONE_SHORT = BUDGET - 1;
    private static final int ONE_OVER = BUDGET + 1;

    private static final int CLEAR_TO = 300;
    private static final long SEED = 20261114L;

    /**
     * The placement array from the docs, verbatim. {@link #documentedChainParsesAndRejectsAirStarts}
     * decodes it so the example cannot drift away from the codecs.
     */
    private static final String DOCUMENTED_PLACEMENT = """
            [
              { "type": "minecraft:count", "count": 1 },
              { "type": "minecraft:in_square" },
              { "type": "minecraft:biome" },
              { "type": "isekai_api:dimension_guard", "dimension": "minecraft:the_nether" },
              {
                "type": "isekai_api:surface_relative",
                "anchor": { "type": "isekai_api:fixed_y", "y": 186 },
                "offset": 0
              },
              {
                "type": "minecraft:block_predicate_filter",
                "predicate": {
                  "type": "minecraft:matching_blocks",
                  "blocks": [
                    "minecraft:bedrock",
                    "minecraft:netherrack",
                    "minecraft:basalt",
                    "minecraft:blackstone"
                  ]
                }
              },
              {
                "type": "isekai_api:surface_relative",
                "anchor": {
                  "type": "isekai_api:world_floor",
                  "start": { "type": "isekai_api:fixed_y", "y": 186 },
                  "max_scan": 66
                },
                "offset": 0
              }
            ]
            """;

    private IsekaiCeilingScanGameTests() {}

    // =====================================================================
    // Budget counting
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void budgetIsInclusiveFromStartToUnderside(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = origin.getX() + 6;
        int z = origin.getZ() + 6;

        // Ceiling body 121..186, air from 120 down.
        buildColumn(level, x, z, UNDERSIDE + 1, START_Y, Blocks.NETHERRACK);

        Integer atBudget = floor(level, x, z, BUDGET);
        if (atBudget == null || atBudget != UNDERSIDE) {
            helper.fail("world_floor from " + START_Y + " with max_scan " + BUDGET
                    + ": expected " + UNDERSIDE + ", got " + atBudget);
            return;
        }
        Integer oneShort = floor(level, x, z, ONE_SHORT);
        if (oneShort != null) {
            helper.fail("max_scan " + ONE_SHORT + " must not reach " + UNDERSIDE + ", got " + oneShort);
            return;
        }

        // The modifier must hand that Y on unchanged under an offset of 0.
        SurfaceRelativeModifier modifier = new SurfaceRelativeModifier(
                new SurfaceAnchor.WorldFloor(new SurfaceAnchor.FixedY(START_Y), BUDGET),
                ConstantInt.of(0));
        List<BlockPos> placed = place(modifier, level, x, z);
        if (placed.size() != 1 || placed.get(0).getY() != UNDERSIDE
                || placed.get(0).getX() != x || placed.get(0).getZ() != z) {
            helper.fail("surface_relative(world_floor) should emit exactly (" + x + "," + UNDERSIDE
                    + "," + z + "), got " + placed);
            return;
        }

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void oneMoreBlockOfCeilingNeedsOneMoreStep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = origin.getX() + 6;
        int z = origin.getZ() + 6;

        // The body reaches one block lower: the air cell is at 119, so the documented budget misses.
        buildColumn(level, x, z, UNDERSIDE, START_Y, Blocks.NETHERRACK);

        Integer atBudget = floor(level, x, z, BUDGET);
        if (atBudget != null) {
            helper.fail("max_scan " + BUDGET + " must stop inside the body at Y " + UNDERSIDE
                    + ", got " + atBudget);
            return;
        }
        Integer oneOver = floor(level, x, z, ONE_OVER);
        if (oneOver == null || oneOver != UNDERSIDE - 1) {
            helper.fail("max_scan " + ONE_OVER + " should reach " + (UNDERSIDE - 1) + ", got " + oneOver);
            return;
        }

        helper.succeed();
    }

    // =====================================================================
    // The air-start pitfall
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void airStartFallsThroughToLowerShelf(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = origin.getX() + 6;
        int z = origin.getZ() + 6;

        // Nothing at the start height: a shelf sits 6 blocks lower, the floor far below it.
        clearColumn(level, x, z);
        fillColumn(level, x, z, 175, 180, Blocks.NETHERRACK);
        fillColumn(level, x, z, 60, 70, Blocks.NETHERRACK);

        Integer resolved = floor(level, x, z, BUDGET);
        if (resolved == null || resolved != 174) {
            helper.fail("an air start cell must fall through to the lower body's underside "
                    + "(expected 174), got " + resolved);
            return;
        }

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void predicateGateRejectsAirStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int ceilingX = origin.getX() + 6;
        int shortX = origin.getX() + 8;
        int shelfX = origin.getX() + 10;
        int z = origin.getZ() + 6;

        buildColumn(level, ceilingX, z, UNDERSIDE + 1, START_Y, Blocks.NETHERRACK);
        buildColumn(level, shortX, z, UNDERSIDE + 1, START_Y - 1, Blocks.NETHERRACK);
        buildColumn(level, shelfX, z, 175, 180, Blocks.NETHERRACK);

        BlockPredicate ceilingBlocks = BlockPredicate.matchesBlocks(
                Blocks.BEDROCK, Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE);

        List<BlockPos> gated = gateAndScan(level, ceilingX, z, ceilingBlocks);
        if (gated.size() != 1 || gated.get(0).getY() != UNDERSIDE) {
            helper.fail("a full ceiling should pass the gate and land on " + UNDERSIDE + ", got " + gated);
            return;
        }
        if (!gateAndScan(level, shortX, z, ceilingBlocks).isEmpty()) {
            helper.fail("a ceiling that stops below the start height must be rejected by the gate");
            return;
        }
        if (!gateAndScan(level, shelfX, z, ceilingBlocks).isEmpty()) {
            helper.fail("a shelf below the start height must be rejected by the gate");
            return;
        }

        helper.succeed();
    }

    // =====================================================================
    // What counts as "not air"
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void lavaIsNotAirAndDoesNotEndTheScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = origin.getX() + 6;
        int z = origin.getZ() + 6;

        // Ceiling 126..186, lava 120..125, air at 119.
        clearColumn(level, x, z);
        fillColumn(level, x, z, 126, START_Y, Blocks.NETHERRACK);
        fillColumn(level, x, z, UNDERSIDE, 125, Blocks.LAVA);

        Integer atBudget = floor(level, x, z, BUDGET);
        if (atBudget != null) {
            helper.fail("lava is not air, so max_scan " + BUDGET + " must not stop inside it; got "
                    + atBudget);
            return;
        }
        Integer oneOver = floor(level, x, z, ONE_OVER);
        if (oneOver == null || oneOver != UNDERSIDE - 1) {
            helper.fail("the scan should cross the lava and report " + (UNDERSIDE - 1) + ", got " + oneOver);
            return;
        }

        helper.succeed();
    }

    // =====================================================================
    // The documented chain, decoded from the documented JSON
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void documentedChainParsesAndRejectsAirStarts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int ceilingX = origin.getX() + 6;
        int shortX = origin.getX() + 8;
        int shelfX = origin.getX() + 10;
        int z = origin.getZ() + 6;

        List<PlacementModifier> chain = decodeChain(helper, level);
        if (chain == null) return;
        if (chain.size() != 7) {
            helper.fail("the documented placement array should decode to 7 modifiers, got " + chain.size());
            return;
        }
        if (!(chain.get(3) instanceof DimensionGuardPlacementModifier)) {
            helper.fail("index 3 should be dimension_guard, got " + chain.get(3).getClass().getName());
            return;
        }
        if (!(chain.get(4) instanceof SurfaceRelativeModifier)
                || !(chain.get(5) instanceof BlockPredicateFilter)
                || !(chain.get(6) instanceof SurfaceRelativeModifier)) {
            helper.fail("indices 4..6 should be surface_relative, block_predicate_filter, surface_relative");
            return;
        }

        buildColumn(level, ceilingX, z, UNDERSIDE + 1, START_Y, Blocks.NETHERRACK);
        buildColumn(level, shortX, z, UNDERSIDE + 1, START_Y - 1, Blocks.NETHERRACK);
        buildColumn(level, shelfX, z, 175, 180, Blocks.NETHERRACK);

        // Only the tail is run: `biome` refuses to run outside a real feature placement, and
        // `dimension_guard` points at the nether while the test level is the overworld.
        List<PlacementModifier> scanStage = chain.subList(4, chain.size());
        List<BlockPos> ceiling = runChain(scanStage, level, ceilingX, z);
        if (ceiling.size() != 1 || ceiling.get(0).getY() != UNDERSIDE) {
            helper.fail("the documented chain on a full ceiling should emit one position at "
                    + UNDERSIDE + ", got " + ceiling);
            return;
        }
        if (!runChain(scanStage, level, shortX, z).isEmpty()) {
            helper.fail("the documented chain must place nothing when the ceiling stops below " + START_Y);
            return;
        }
        if (!runChain(scanStage, level, shelfX, z).isEmpty()) {
            helper.fail("the documented chain must place nothing above a lower shelf");
            return;
        }

        helper.succeed();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static Integer floor(ServerLevel level, int x, int z, int maxScan) {
        SurfaceAnchor anchor = new SurfaceAnchor.WorldFloor(new SurfaceAnchor.FixedY(START_Y), maxScan);
        return anchor.resolveY(placementContext(level), new BlockPos(x, START_Y, z));
    }

    /** The documented gate: fixed_y at the start height, the block filter, then the scan. */
    private static List<BlockPos> gateAndScan(ServerLevel level, int x, int z,
                                              BlockPredicate ceilingBlocks) {
        List<PlacementModifier> chain = List.of(
                new SurfaceRelativeModifier(new SurfaceAnchor.FixedY(START_Y), ConstantInt.of(0)),
                BlockPredicateFilter.forPredicate(ceilingBlocks),
                new SurfaceRelativeModifier(
                        new SurfaceAnchor.WorldFloor(new SurfaceAnchor.FixedY(START_Y), BUDGET),
                        ConstantInt.of(0)));
        return runChain(chain, level, x, z);
    }

    private static List<BlockPos> runChain(List<PlacementModifier> chain, ServerLevel level,
                                           int x, int z) {
        PlacementContext ctx = placementContext(level);
        List<BlockPos> positions = List.of(new BlockPos(x, START_Y, z));
        for (PlacementModifier modifier : chain) {
            RandomSource random = RandomSource.create(SEED);
            positions = positions.stream()
                    .flatMap(pos -> modifier.getPositions(ctx, random, pos))
                    .toList();
            if (positions.isEmpty()) return positions;
        }
        return positions;
    }

    private static List<BlockPos> place(PlacementModifier modifier, ServerLevel level, int x, int z) {
        return modifier.getPositions(placementContext(level), RandomSource.create(SEED),
                new BlockPos(x, START_Y, z)).toList();
    }

    private static List<PlacementModifier> decodeChain(GameTestHelper helper, ServerLevel level) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        var parsed = PlacementModifier.CODEC.listOf()
                .parse(ops, JsonParser.parseString(DOCUMENTED_PLACEMENT));
        if (parsed.error().isPresent()) {
            helper.fail("the documented placement array did not decode: "
                    + parsed.error().get().message());
            return null;
        }
        return parsed.result().orElseThrow();
    }

    private static PlacementContext placementContext(ServerLevel level) {
        return new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.empty());
    }

    /** Empty the column from the build floor to {@link #CLEAR_TO}. */
    private static void clearColumn(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinBuildHeight(); y <= CLEAR_TO; y++) {
            cursor.set(x, y, z);
            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** Fill {@code [bottom, top]} of an already cleared column with {@code block}. */
    private static void fillColumn(ServerLevel level, int x, int z, int bottom, int top, Block block) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = bottom; y <= top; y++) {
            cursor.set(x, y, z);
            level.setBlock(cursor, block.defaultBlockState(), 2);
        }
    }

    /**
     * Empty the column, then fill {@code [bottom, top]} with {@code block}. Starting from an empty
     * column keeps the scan length exact.
     */
    private static void buildColumn(ServerLevel level, int x, int z, int bottom, int top, Block block) {
        clearColumn(level, x, z);
        fillColumn(level, x, z, bottom, top, block);
    }
}
