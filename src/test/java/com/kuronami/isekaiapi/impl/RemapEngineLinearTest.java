package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Band-projection math for {@link RemapEngine} Linear/Identity/Inverted — the transform the
 * GameTest ore-remap relies on. Pure logic, no bootstrap. Complements the GameTest, which
 * proves the wiring but leaves the exact Y math to these assertions.
 */
class RemapEngineLinearTest {

    private static final int WORLD_BOTTOM = -64;
    private static final int WORLD_TOP = 319;
    private static final VerticalRange BAND = new VerticalRange(100, 140, HeightDistribution.UNIFORM);

    @Test
    void identityLeavesRangeUnchanged() {
        VerticalRange original = new VerticalRange(-64, 16, HeightDistribution.UNIFORM);
        VerticalRange out = RemapEngine.apply(new RemapStrategy.Identity(), original, BAND, WORLD_BOTTOM, WORLD_TOP);
        assertEquals(original, out);
    }

    @Test
    void linearProjectsDeepOreToBandFloor() {
        // A deep ore spanning world-bottom..16 maps its bottom to the band floor and stays
        // in the lower part of the band (never escaping it).
        VerticalRange original = new VerticalRange(-64, 16, HeightDistribution.UNIFORM);
        VerticalRange out = RemapEngine.apply(new RemapStrategy.Linear(), original, BAND, WORLD_BOTTOM, WORLD_TOP);
        assertEquals(100, out.minY(), "world-bottom ore should map to the band floor");
        assertTrue(out.maxY() <= BAND.maxY() && out.maxY() >= out.minY(),
                "remapped max stays inside the band: " + out);
        assertTrue(out.maxY() < 120, "a shallow-spanning deep ore stays low in the band: " + out);
    }

    @Test
    void linearKeepsRangeInsidePlayableBand() {
        for (int min = WORLD_BOTTOM; min < WORLD_TOP - 20; min += 37) {
            VerticalRange original = new VerticalRange(min, min + 20, HeightDistribution.UNIFORM);
            VerticalRange out = RemapEngine.apply(new RemapStrategy.Linear(), original, BAND, WORLD_BOTTOM, WORLD_TOP);
            assertTrue(out.minY() >= BAND.minY() && out.maxY() <= BAND.maxY(),
                    "linear remap of " + original + " escaped band: " + out);
        }
    }

    @Test
    void invertedMirrorsWithinBand() {
        VerticalRange lowOriginal = new VerticalRange(-64, 0, HeightDistribution.UNIFORM);
        VerticalRange inverted = RemapEngine.apply(new RemapStrategy.Inverted(), lowOriginal, BAND, WORLD_BOTTOM, WORLD_TOP);
        // A low original inverts to the high side of the band.
        assertTrue(inverted.maxY() <= BAND.maxY() && inverted.minY() >= BAND.minY(),
                "inverted stays in band: " + inverted);
        assertTrue(inverted.maxY() > 130, "a low ore inverts high in the band: " + inverted);
    }
}
