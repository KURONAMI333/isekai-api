package com.kuronami.isekaiapi.validation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Start-point rule: the first solid block below the spawn is the footing; the two blocks above
 * it must be neither solid nor fluid; water is a warning, lava is an error. The column is a
 * stub, so every branch is exercised without a world.
 */
class SpawnSafetyTest {

    private static SpawnSafety.Column column(Map<Integer, SpawnSafety.Sample> blocks) {
        return y -> blocks.getOrDefault(y, SpawnSafety.Sample.AIR);
    }

    private static Map<Integer, SpawnSafety.Sample> blocks(Object... yAndSamples) {
        Map<Integer, SpawnSafety.Sample> map = new HashMap<>();
        for (int i = 0; i < yAndSamples.length; i += 2) {
            map.put((Integer) yAndSamples[i], (SpawnSafety.Sample) yAndSamples[i + 1]);
        }
        return map;
    }

    @Test
    void okWhenFootingIsBelowAndTwoBlocksAreClear() {
        var result = SpawnSafety.evaluate(
                column(blocks(63, SpawnSafety.Sample.SOLID)), 64, -64);
        assertEquals(SpawnSafety.Status.OK, result.status());
        assertTrue(result.detail().contains("y=63"), result.detail());
    }

    @Test
    void warnWhenFootingIsUnderWater() {
        var result = SpawnSafety.evaluate(
                column(blocks(63, SpawnSafety.Sample.SOLID,
                        64, new SpawnSafety.Sample(false, true, false))), 64, -64);
        assertEquals(SpawnSafety.Status.WARN, result.status());
        assertTrue(result.detail().contains("y=63"), result.detail());
    }

    @Test
    void warnWhenFootingIsWaterlogged() {
        var result = SpawnSafety.evaluate(
                column(blocks(63, new SpawnSafety.Sample(true, true, false))), 64, -64);
        assertEquals(SpawnSafety.Status.WARN, result.status());
    }

    @Test
    void errorWhenFootingIsLava() {
        var result = SpawnSafety.evaluate(
                column(blocks(63, new SpawnSafety.Sample(true, true, true))), 64, -64);
        assertEquals(SpawnSafety.Status.ERROR, result.status());
        assertTrue(result.detail().contains("lava"), result.detail());
    }

    @Test
    void errorWhenLavaFillsTheStandingSpace() {
        var result = SpawnSafety.evaluate(
                column(blocks(62, SpawnSafety.Sample.SOLID,
                        63, new SpawnSafety.Sample(false, true, true))), 64, -64);
        assertEquals(SpawnSafety.Status.ERROR, result.status());
        assertTrue(result.detail().contains("lava"), result.detail());
    }

    @Test
    void errorWhenASolidBlockFillsTheStandingSpace() {
        var result = SpawnSafety.evaluate(
                column(blocks(63, SpawnSafety.Sample.SOLID,
                        64, SpawnSafety.Sample.SOLID)), 64, -64);
        assertEquals(SpawnSafety.Status.ERROR, result.status());
        assertTrue(result.detail().contains("solid"), result.detail());
    }

    @Test
    void errorWhenTheStartPointItselfIsSolid() {
        var result = SpawnSafety.evaluate(
                column(blocks(64, SpawnSafety.Sample.SOLID)), 64, -64);
        assertEquals(SpawnSafety.Status.ERROR, result.status());
        assertTrue(result.detail().contains("inside a solid block"), result.detail());
    }

    @Test
    void errorWhenNoSolidBlockIsWithinTheSearchDepth() {
        var result = SpawnSafety.evaluate(column(blocks()), 64, -64);
        assertEquals(SpawnSafety.Status.ERROR, result.status());
        assertTrue(result.detail().contains("no solid block"), result.detail());
    }

    @Test
    void footingAtExactlyTheSearchDepthIsStillFound() {
        int start = 64;
        var found = SpawnSafety.evaluate(
                column(blocks(start - SpawnSafety.SEARCH_DEPTH, SpawnSafety.Sample.SOLID)),
                start, -64);
        assertEquals(SpawnSafety.Status.OK, found.status());

        var tooDeep = SpawnSafety.evaluate(
                column(blocks(start - SpawnSafety.SEARCH_DEPTH - 1, SpawnSafety.Sample.SOLID)),
                start, -64);
        assertEquals(SpawnSafety.Status.ERROR, tooDeep.status());
    }

    @Test
    void searchStopsAtTheDimensionsMinimumBuildHeight() {
        var result = SpawnSafety.evaluate(
                column(blocks(59, SpawnSafety.Sample.SOLID)), 64, 60);
        assertEquals(SpawnSafety.Status.ERROR, result.status());
    }

    @Test
    void skipWhenTheDimensionHasNoLoadedLevel() {
        var result = SpawnSafety.skipped("dimension minecraft:test is not loaded");
        assertEquals(SpawnSafety.Status.SKIP, result.status());
        assertTrue(result.detail().contains("not loaded"), result.detail());
    }
}
