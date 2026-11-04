package com.kuronami.isekaiapi.placementmodifier;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The acceptance curve of {@code biome_falloff}, checked against geometry rather than against a
 * world.
 *
 * <p>The target biome is the half-plane {@code x <= 0} and the candidate position sits {@code d}
 * blocks to its right, on the sample lattice. The nearest target cell is then exactly {@code d}
 * away, so the measured acceptance rate at {@code d} is comparable with the formula
 * {@code t^exponent}, {@code t = (max - d) / (max - full)}. Every distance used here is a multiple
 * of {@code step} for that reason; between lattice planes the nearest cell is the next one out, and
 * the rate steps down by four blocks instead of sliding.
 */
class BiomeFalloffSearchTest {

    private static final int STEP = 4;
    private static final int TRIALS = 10_000;

    /** Target biome is everything at or left of x = 0. */
    private static final BiomeFalloffSearch.TargetProbe LEFT_HALF_PLANE = (x, y, z) -> x <= 0;

    @Test
    void acceptanceMatchesTheFalloffFormula() {
        int full = 8;
        int max = 48;
        int[] distances = {0, 8, 16, 24, 32, 40, 48, 56};
        for (float exponent : new float[] {1.0f, 2.0f}) {
            List<Double> rates = new ArrayList<>();
            for (int d : distances) {
                rates.add(passRate(d, full, max, exponent));
            }
            assertEquals(1.0, rates.get(0), 1e-9,
                    "the candidate inside the biome must always pass");
            assertEquals(1.0, rates.get(1), 1e-9,
                    "full_distance must always pass (exponent " + exponent + ")");
            assertEquals(0.0, rates.get(rates.size() - 1), 1e-9,
                    "past max_distance nothing may pass (exponent " + exponent + ")");
            for (int i = 1; i < distances.length; i++) {
                int d = distances[i];
                double expected = d <= full ? 1.0 : d > max ? 0.0 : expectedRate(d, full, max, exponent);
                assertEquals(expected, rates.get(i), 0.03,
                        "distance " + d + " / exponent " + exponent);
                assertTrue(rates.get(i) <= rates.get(i - 1) + 0.02,
                        "acceptance must not rise with distance at " + d
                                + " (exponent " + exponent + ")");
            }
        }
    }

    @Test
    void aLargerExponentThinsFaster() {
        // exponent > 1 keeps the biome's own edge dense and drops away sooner, so at the midpoint of
        // the band a steeper curve passes less often. This is the whole point of the field.
        double linear = passRate(28, 8, 48, 1.0f);
        double steep = passRate(28, 8, 48, 2.0f);
        assertTrue(steep < linear - 0.05,
                "exponent 2 (" + steep + ") should pass less than exponent 1 (" + linear + ")");
    }

    @Test
    void fullEqualToMaxIsAHardRadius() {
        // The documented fallback for "within N blocks, no falloff". Lattice distance 16 is inside
        // 16 and 20 is outside, at every draw.
        assertEquals(1.0, passRate(16, 16, 16, 1.0f), 1e-9);
        assertEquals(0.0, passRate(20, 16, 16, 1.0f), 1e-9);
        // ... and the radius never shrinks below its own maximum, whatever the draw.
        RandomSource rand = RandomSource.create(7L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(16.0, BiomeFalloffSearch.radius(rand.nextDouble(), 16, 16, 1.0f));
        }
    }

    @Test
    void searchStopsAtTheFirstRingHoldingTheTarget() {
        // A target one lattice step away costs the origin cell plus that ring, not the whole disc.
        BiomeFalloffSearch.Result hit = BiomeFalloffSearch.search(4, 64, 0, 32, STEP, 0, 0,
                LEFT_HALF_PLANE);
        assertTrue(hit.found());
        assertTrue(hit.probed() <= 1 + 8 * 1,
                "a target at one step should cost one ring, probed " + hit.probed());

        BiomeFalloffSearch.Result miss = BiomeFalloffSearch.search(64, 64, 0, 32, STEP, 0, 0,
                (x, y, z) -> false);
        assertFalse(miss.found());
        assertTrue(miss.probed() > 100,
                "a full 32-block search should cover the disc, probed " + miss.probed());
    }

    @Test
    void verticalSpanIsSearchedNearestLevelFirst() {
        // The biome exists only four blocks above the candidate: it is found, and the search costs
        // the origin level plus the one above rather than the levels below.
        boolean found = BiomeFalloffSearch.search(0, 64, 0, 0, STEP, 24, 16,
                (x, y, z) -> y == 68).found();
        assertTrue(found, "the level four blocks up must be reached");

        int probed = BiomeFalloffSearch.search(0, 64, 0, 0, STEP, 24, 16,
                (x, y, z) -> y == 68).probed();
        assertEquals(2, probed, "nearest-first: origin, then the level above");

        // A biome below the band and a band that does not reach it: nothing.
        assertFalse(BiomeFalloffSearch.search(0, 64, 0, 0, STEP, 0, 0,
                (x, y, z) -> y == 60).found(),
                "a single-level search must not reach six blocks down");
    }

    @Test
    void coarserStepSamplesFewerCells() {
        int fine = BiomeFalloffSearch.search(0, 64, 0, 32, 4, 0, 0, (x, y, z) -> false).probed();
        int coarse = BiomeFalloffSearch.search(0, 64, 0, 32, 16, 0, 0, (x, y, z) -> false).probed();
        assertTrue(coarse * 4 < fine,
                "step 16 should sample far fewer cells than step 4 (" + coarse + " vs " + fine + ")");
    }

    private static double expectedRate(int distance, int full, int max, float exponent) {
        double t = (double) (max - distance) / (max - full);
        return Math.pow(t, exponent);
    }

    private static double passRate(int distance, int full, int max, float exponent) {
        RandomSource rand = RandomSource.create(20261104L + distance);
        int passed = 0;
        for (int i = 0; i < TRIALS; i++) {
            double radius = BiomeFalloffSearch.radius(rand.nextDouble(), full, max, exponent);
            if (BiomeFalloffSearch.search(distance, 64, 0, radius, STEP, 0, 0, LEFT_HALF_PLANE)
                    .found()) {
                passed++;
            }
        }
        return (double) passed / TRIALS;
    }
}
