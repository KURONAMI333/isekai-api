package com.kuronami.isekaiapi.survey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two pieces of arithmetic the biome survey report rests on, checked on arrays whose answers are
 * obvious by hand.
 *
 * <p>The percentile is interpolated rather than "nearest value": cluster diameters are a short list
 * (a handful of patches), and rounding to an actual patch would make the median jump by tens of
 * blocks when one patch appears. The diameter is the diameter of the circle with the same area, so a
 * radius-64 patch reads 128 and a long thin strip reads the width it would have at that area.
 */
class BiomeSurveyTest {

    @Test
    void percentileInterpolatesBetweenNeighbours() {
        double[] even = {1.0, 2.0, 3.0, 4.0};
        assertEquals(1.0, BiomeSurvey.percentile(even, 0), 1e-9);
        assertEquals(2.5, BiomeSurvey.percentile(even, 50), 1e-9);
        assertEquals(4.0, BiomeSurvey.percentile(even, 100), 1e-9);
        assertEquals(1.75, BiomeSurvey.percentile(even, 25), 1e-9);
        assertEquals(3.25, BiomeSurvey.percentile(even, 75), 1e-9);
        assertEquals(3.7, BiomeSurvey.percentile(even, 90), 1e-9);

        double[] single = {7.5};
        assertEquals(7.5, BiomeSurvey.percentile(single, 0), 1e-9);
        assertEquals(7.5, BiomeSurvey.percentile(single, 99), 1e-9);

        assertEquals(0.0, BiomeSurvey.percentile(new double[0], 50), 1e-9,
                "no patches yet must read 0, not NaN");
    }

    @Test
    void percentileOfATenElementArrayHitsTheRankedValues() {
        // 1..10 sorted: p10 is 1.9, p50 is 5.5, p90 is 9.1.
        double[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(1.9, BiomeSurvey.percentile(values, 10), 1e-9);
        assertEquals(5.5, BiomeSurvey.percentile(values, 50), 1e-9);
        assertEquals(9.1, BiomeSurvey.percentile(values, 90), 1e-9);
    }

    @Test
    void equivalentDiameterGivesTheCircleOfTheSameArea() {
        // A radius-64 circle has area 12868 blocks^2 and must read back as 128 blocks across.
        assertEquals(128.0, BiomeSurvey.equivalentDiameter(Math.PI * 64 * 64), 1e-9);
        // One 4x4 cell is a 4.51-block circle, not 4: the number is a diameter, not a side.
        assertEquals(2 * Math.sqrt(16 / Math.PI), BiomeSurvey.equivalentDiameter(16), 1e-9);
        // A 1-block-wide, 1000-block-long strip reads its width at equal area, not its length.
        assertEquals(2 * Math.sqrt(1000 / Math.PI), BiomeSurvey.equivalentDiameter(1000), 1e-9);
        assertEquals(0.0, BiomeSurvey.equivalentDiameter(0), 1e-9);
        assertEquals(0.0, BiomeSurvey.equivalentDiameter(-16), 1e-9,
                "a negative area is a bug upstream, but the report must not print NaN");
    }

    @Test
    void climateStrideKeepsTheArraysBounded() {
        assertEquals(1, BiomeSurvey.climateStride(BiomeSurvey.MIN_RADIUS_BLOCKS));
        assertEquals(1, BiomeSurvey.climateStride(256), "a radius-256 disc fits under the cap");
        for (int radius = BiomeSurvey.MIN_RADIUS_BLOCKS; radius <= BiomeSurvey.MAX_RADIUS_BLOCKS;
                radius += 16) {
            int stride = BiomeSurvey.climateStride(radius);
            double cells = Math.PI * Math.pow(radius / 4.0, 2);
            // The cap is 262,144 samples per axis; thinning by a square lattice of `stride` keeps
            // the kept count near or under it, and the stride must never drop below one.
            assertTrue(stride >= 1, "stride must be at least 1 at radius " + radius);
            assertTrue(cells / ((double) stride * stride) <= 262_144 * 1.1,
                    "radius " + radius + " keeps " + (cells / ((double) stride * stride))
                            + " climate samples, over the cap");
        }
    }
}