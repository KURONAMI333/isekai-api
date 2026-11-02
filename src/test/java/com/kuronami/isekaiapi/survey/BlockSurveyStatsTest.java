package com.kuronami.isekaiapi.survey;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.survey.BlockSurvey.ChunkCounts;
import com.kuronami.isekaiapi.survey.BlockSurvey.Params;
import com.kuronami.isekaiapi.survey.BlockSurvey.Result;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The statistics and the report shape of {@code /isekai survey blocks}.
 *
 * <p>What a tree is, and whether the counter agrees, is a statement about blocks in a world, so that
 * half is checked in {@code BlockSurveyGameTests}. This half fixes the numbers an author tunes
 * against: mean, median, empty share, and how concentrated the busiest chunks are.
 */
class BlockSurveyStatsTest {

    private static Params params() {
        return new Params("minecraft:oak_log", state -> false, 2, new ChunkPos(0, 0), null);
    }

    private static ChunkCounts chunk(int x, int z, int trees) {
        return new ChunkCounts(x, z, trees, trees * 4);
    }

    @Test
    void distributionIsReadFromThePerChunkCounts() {
        // 0,0,0,1,2,3,10 -> seven chunks, sixteen trees.
        List<ChunkCounts> counts = List.of(
                chunk(0, 0, 0), chunk(1, 0, 0), chunk(2, 0, 0), chunk(3, 0, 1),
                chunk(4, 0, 2), chunk(5, 0, 3), chunk(6, 0, 10));
        Result result = BlockSurvey.summarize(params(), "minecraft:overworld", 3, 0, counts, 42L);

        assertEquals(7, result.countedChunks());
        assertEquals(16, result.totalTrees());
        assertEquals(64, result.totalBlocks());
        assertEquals(16.0 / 7, result.meanTrees(), 1.0E-9);
        assertEquals(0, result.minTrees());
        assertEquals(1.0, result.medianTrees(), 0.0);
        assertEquals(10, result.maxTrees());
        assertEquals(3, result.zeroChunks());
        assertEquals(3.0 / 7, result.zeroShare(), 1.0E-9);
        // 10% of seven chunks is 0.7 -> one chunk, and that chunk holds 10 of 16 trees.
        assertEquals(1, result.topTenChunks());
        assertEquals(10.0 / 16, result.topTenShare(), 1.0E-9);
        assertEquals(42L, result.elapsedMillis());
    }

    @Test
    void evenChunkCountTakesTheMiddleTwoAsMedian() {
        Result result = BlockSurvey.summarize(params(), "minecraft:overworld", 0, 0,
                List.of(chunk(0, 0, 0), chunk(1, 0, 5)), 1L);
        assertEquals(2.5, result.medianTrees(), 0.0);
    }

    @Test
    void noCountedChunksDoesNotDivideByZero() {
        Result result = BlockSurvey.summarize(params(), "minecraft:overworld", 9, 1, List.of(), 5L);
        assertEquals(0, result.countedChunks());
        assertEquals(0, result.totalTrees());
        assertEquals(0.0, result.meanTrees(), 0.0);
        assertEquals(0.0, result.medianTrees(), 0.0);
        assertEquals(0.0, result.zeroShare(), 0.0);
        assertEquals(0, result.topTenChunks());
        assertEquals(0.0, result.topTenShare(), 0.0);
        assertEquals(9, result.skippedChunks());
        assertEquals(1, result.failedChunks());
    }

    @Test
    void jsonCarriesTheDistributionAndTheSpatialMap() {
        List<ChunkCounts> counts = List.of(chunk(-3, 5, 4), chunk(-2, 5, 0));
        Result result = BlockSurvey.summarize(params(), "minecraft:overworld", 0, 0, counts, 7L);

        JsonObject json = JsonParser.parseString(result.toJsonString()).getAsJsonObject();
        assertEquals("minecraft:oak_log", json.get("target").getAsString());
        assertEquals(2, json.get("counted_chunks").getAsInt());
        assertTrue(json.get("tree_definition").getAsString().contains("six-connected"));

        JsonObject trees = json.getAsJsonObject("trees");
        assertEquals(4, trees.get("total").getAsInt());
        assertEquals(1, trees.get("zero_chunks").getAsInt());
        assertEquals(1, trees.getAsJsonObject("histogram").get("0").getAsInt());
        assertEquals(1, trees.getAsJsonObject("histogram").get("3-5").getAsInt());

        JsonArray chunks = json.getAsJsonArray("chunks");
        assertEquals(2, chunks.size());
        JsonObject first = chunks.get(0).getAsJsonObject();
        assertEquals(-3, first.get("x").getAsInt());
        assertEquals(5, first.get("z").getAsInt());
        assertEquals(4, first.get("trees").getAsInt());
        assertEquals(16, first.get("blocks").getAsInt());
    }

    @Test
    void fileNameNamesTheTargetAndRadius() {
        assertEquals("blocks-minecraft_oak_log-r2.json", BlockSurvey.fileName(params()));
        assertEquals("blocks-minecraft_logs-r2.json", BlockSurvey.fileName(new Params(
                "#minecraft:logs", state -> false, 2, new ChunkPos(0, 0), null)));
    }
}
