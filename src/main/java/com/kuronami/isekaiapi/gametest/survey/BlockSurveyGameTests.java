package com.kuronami.isekaiapi.gametest.survey;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.survey.BlockSurvey;
import com.kuronami.isekaiapi.survey.BlockSurvey.ChunkCounts;
import com.kuronami.isekaiapi.survey.BlockSurvey.Params;
import com.kuronami.isekaiapi.survey.BlockSurveyRun;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code /isekai survey blocks} — the counter has to call a 2x2 trunk one tree, and a leaning trunk
 * one tree, before its averages mean anything.
 *
 * <p>The shapes are built by hand in a chunk that no other test uses, so the expected numbers are
 * exact: five trees and forty-two log blocks. A "lowest block of each column" rule would report the
 * 2x2 trunk as four and add a phantom at every step of the lean; that is the mistake these assertions
 * exist to catch.
 *
 * <p>The end-to-end test drives the real {@link BlockSurveyRun} tick job over one chunk, so the
 * command path (queue, generate, count, summarise) is exercised rather than only
 * {@link BlockSurvey#count}.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class BlockSurveyGameTests {

    /** A chunk near the test world's build floor, above the surface and used by no other test. */
    private static final int ORIGIN_X = 14000;

    private static final int ORIGIN_Z = 8000;

    /** Air in the test world (surface -60). */
    private static final int BASE_Y = -40;

    private BlockSurveyGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 200)
    public static void countsEachTrunkShapeAsOneTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelChunk chunk = level.getChunk(ORIGIN_X >> 4, ORIGIN_Z >> 4);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        ChunkCounts before = BlockSurvey.count(chunk, state -> state.is(Blocks.OAK_LOG));

        int x = ORIGIN_X + 1;
        int z = ORIGIN_Z + 1;
        // One column, five tall.
        for (int y = 0; y < 5; y++) {
            level.setBlock(new BlockPos(x, BASE_Y + y, z), log, 2);
        }
        // A 2x2 trunk, six tall: four columns on the same layer must stay one tree.
        for (int y = 0; y < 6; y++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    level.setBlock(new BlockPos(x + 4 + dx, BASE_Y + y, z + dz), log, 2);
                }
            }
        }
        // A leaning trunk: two columns reach sideways through face-adjacent logs.
        int[][] lean = {{9, 0}, {9, 1}, {9, 2}, {10, 2}, {10, 3}, {11, 3}, {11, 4}};
        for (int[] step : lean) {
            level.setBlock(new BlockPos(x + step[0], BASE_Y + step[1], z), log, 2);
        }
        // Two separate trunks, one block apart horizontally.
        for (int y = 0; y < 3; y++) {
            level.setBlock(new BlockPos(x, BASE_Y + y, z + 5), log, 2);
            level.setBlock(new BlockPos(x + 2, BASE_Y + y, z + 5), log, 2);
        }

        ChunkCounts after = BlockSurvey.count(chunk, state -> state.is(Blocks.OAK_LOG));
        int trees = after.trees() - before.trees();
        int blocks = after.blocks() - before.blocks();
        if (trees != 5 || blocks != 42) {
            helper.fail("expected 5 trees / 42 blocks, counted " + trees + " / " + blocks
                    + " (chunk had " + before.trees() + " / " + before.blocks() + " before)");
            return;
        }

        for (int y = 0; y < 6; y++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    level.setBlock(new BlockPos(x + 4 + dx, BASE_Y + y, z + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int y = 0; y < 5; y++) {
            level.setBlock(new BlockPos(x, BASE_Y + y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        for (int[] step : lean) {
            level.setBlock(new BlockPos(x + step[0], BASE_Y + step[1], z),
                    Blocks.AIR.defaultBlockState(), 2);
        }
        for (int y = 0; y < 3; y++) {
            level.setBlock(new BlockPos(x, BASE_Y + y, z + 5), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(new BlockPos(x + 2, BASE_Y + y, z + 5),
                    Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    /**
     * The tick job over one already-loaded chunk: the result must describe exactly the blocks
     * {@link BlockSurvey#count} sees in that chunk, and the JSON must carry the chunk entry.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 400)
    public static void surveyRunCountsTheChunkItWasGiven(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int chunkX = (ORIGIN_X >> 4) + 1;
        int chunkZ = ORIGIN_Z >> 4;
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        ChunkCounts before = BlockSurvey.count(chunk, state -> state.is(Blocks.OAK_LOG));

        int x = chunk.getPos().getMinBlockX() + 1;
        int z = chunk.getPos().getMinBlockZ() + 1;
        for (int y = 0; y < 5; y++) {
            level.setBlock(new BlockPos(x, BASE_Y + y, z), log, 2);
        }
        ChunkCounts after = BlockSurvey.count(chunk, state -> state.is(Blocks.OAK_LOG));

        AtomicReference<BlockSurvey.Result> done = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Params params = new Params("minecraft:oak_log", state -> state.is(Blocks.OAK_LOG), 0,
                new ChunkPos(chunkX, chunkZ), null);
        BlockSurveyRun.start(level, params, done::set, error::set);

        helper.succeedWhen(() -> {
            if (error.get() != null) {
                helper.fail("survey failed: " + error.get());
            }
            BlockSurvey.Result result = done.get();
            if (result == null) {
                helper.fail("survey did not finish");
            }
            if (result.countedChunks() != 1 || result.skippedChunks() != 0) {
                helper.fail("expected 1 counted / 0 skipped, got " + result.countedChunks()
                        + " / " + result.skippedChunks());
            }
            if (result.totalTrees() != after.trees() || result.totalBlocks() != after.blocks()) {
                helper.fail("expected " + after.trees() + " trees / " + after.blocks()
                        + " blocks, got " + result.totalTrees() + " / " + result.totalBlocks());
            }
            if (after.trees() != before.trees() + 1 || after.blocks() != before.blocks() + 5) {
                helper.fail("the placed trunk changed the count by "
                        + (after.trees() - before.trees()) + " trees / "
                        + (after.blocks() - before.blocks()) + " blocks, expected 1 / 5");
            }
            var json = JsonParser.parseString(result.toJsonString()).getAsJsonObject();
            if (json.getAsJsonArray("chunks").size() != 1) {
                helper.fail("json has " + json.getAsJsonArray("chunks").size() + " chunk entries");
            }
            if (json.getAsJsonObject("trees").get("total").getAsInt() != after.trees()) {
                helper.fail("json tree total does not match the counted chunk");
            }
            for (int y = 0; y < 5; y++) {
                level.setBlock(new BlockPos(x, BASE_Y + y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        });
    }

    /** A biome filter is answered from the seed; a biome the world never uses generates nothing. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 200)
    public static void biomeFilterSkipsChunksWithoutGeneratingThem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HolderSet<Biome> forest = HolderSet.direct(
                level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.FOREST));
        AtomicReference<BlockSurvey.Result> done = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Params params = new Params("minecraft:oak_log", state -> state.is(Blocks.OAK_LOG), 1,
                new ChunkPos(ORIGIN_X >> 4, ORIGIN_Z >> 4), forest);
        BlockSurveyRun.start(level, params, done::set, error::set);

        helper.succeedWhen(() -> {
            if (error.get() != null) {
                helper.fail("survey failed: " + error.get());
            }
            BlockSurvey.Result result = done.get();
            if (result == null) {
                helper.fail("survey did not finish");
            }
            if (result.countedChunks() != 0 || result.skippedChunks() != 9) {
                helper.fail("expected 0 counted / 9 skipped, got " + result.countedChunks()
                        + " / " + result.skippedChunks());
            }
            if (result.biomeFilter() == null || !result.biomeFilter().contains("forest")) {
                helper.fail("biome filter not reported: " + result.biomeFilter());
            }
        });
    }

    /**
     * The command tree accepts every documented argument order. A Brigadier path that compiles but
     * cannot be typed would only surface when a player runs it, so the five shapes are parsed here
     * against the real dispatcher. {@code biome} after the block (without a radius) is rejected on
     * purpose: one path per optional argument keeps {@code biome} from being read as a radius.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void commandAcceptsEveryDocumentedShape(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();

        String[][] accepted = {
                {"isekai survey blocks minecraft:oak_log", "block"},
                {"isekai survey blocks #minecraft:logs 16", "radius"},
                {"isekai survey blocks #minecraft:logs 16 biome minecraft:forest", "biome"},
                {"isekai survey blocks #minecraft:logs 16 center 0 64 0", "center"},
                {"isekai survey blocks #minecraft:logs 16 center 0 64 0 biome #minecraft:is_forest",
                        "biome"},
        };
        for (String[] shape : accepted) {
            var parsed = dispatcher.parse(shape[0], source);
            if (!parsed.getExceptions().isEmpty()) {
                helper.fail("\"" + shape[0] + "\" did not parse: "
                        + parsed.getExceptions().values().iterator().next().getMessage());
                return;
            }
            if (parsed.getReader().canRead()) {
                helper.fail("\"" + shape[0] + "\" left \"" + parsed.getReader().getRemaining()
                        + "\" unread");
                return;
            }
            var nodes = parsed.getContext().getNodes();
            String last = nodes.get(nodes.size() - 1).getNode().getName();
            if (!shape[1].equals(last)) {
                helper.fail("\"" + shape[0] + "\" ended at \"" + last + "\", expected \""
                        + shape[1] + "\"");
                return;
            }
        }

        var withoutRadius =
                dispatcher.parse("isekai survey blocks minecraft:oak_log biome minecraft:forest",
                        source);
        if (withoutRadius.getExceptions().isEmpty() && !withoutRadius.getReader().canRead()) {
            helper.fail("biome without a radius parsed, but the documented shape needs a radius");
            return;
        }
        helper.succeed();
    }
}
