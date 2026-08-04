package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure arithmetic and sampling behaviour of {@link ColumnBand} — no world, no registries.
 * Covers the two {@link ColumnBand.DepthScale} modes, the anchor-end decision (including the
 * exact 0.5 tie), and the direction each {@link HeightDistribution} biases toward.
 */
class ColumnBandTest {

    private static ColumnBand blocks(double from, double to, int thickness) {
        return new ColumnBand(SurfaceAnchor.WorldSurface.INSTANCE, SurfaceAnchor.WorldFloor.DEFAULT,
                from, to, ColumnBand.DepthScale.BLOCKS, thickness, HeightDistribution.UNIFORM);
    }

    private static ColumnBand proportional(double from, double to) {
        return new ColumnBand(SurfaceAnchor.WorldSurface.INSTANCE, SurfaceAnchor.WorldFloor.DEFAULT,
                from, to, ColumnBand.DepthScale.PROPORTIONAL, ColumnBand.VANILLA_THICKNESS,
                HeightDistribution.UNIFORM);
    }

    @Test void blocksMode_measuresFromTopForShallowBands() {
        ColumnBand band = blocks(2 / 128.0, 13 / 128.0, 128);
        assertTrue(band.anchoredToTop());
        // Same block offsets whichever altitude the body sits at.
        assertEquals(300 - 2, band.resolveY(300, 180, band.fromDepth()));
        assertEquals(300 - 13, band.resolveY(300, 180, band.toDepth()));
        assertEquals(-10 - 2, band.resolveY(-10, -50, band.fromDepth()));
        assertEquals(-10 - 13, band.resolveY(-10, -50, band.toDepth()));
    }

    @Test void blocksMode_measuresFromBottomForDeepBands() {
        ColumnBand band = blocks(112 / 128.0, 127 / 128.0, 128);
        assertFalse(band.anchoredToTop());
        assertEquals(180 + 16, band.resolveY(300, 180, band.fromDepth()));
        assertEquals(180 + 1, band.resolveY(300, 180, band.toDepth()));
    }

    @Test void blocksMode_tieAtExactlyHalfMeasuresFromTop() {
        ColumnBand band = blocks(0.5, 0.5, 128);
        assertTrue(band.anchoredToTop());
        assertEquals(300 - 64, band.resolveY(300, 180, 0.5));
    }

    @Test void blocksMode_thicknessCompressesTheWholeColumn() {
        // Same normalized band, reference thickness 48 instead of 128: depths shrink 2.67x.
        ColumnBand band = blocks(0.25, 0.25, 48);
        assertEquals(300 - 12, band.resolveY(300, 180, 0.25));
    }

    @Test void proportionalMode_scalesWithTheBodyThickness() {
        ColumnBand band = proportional(0.75, 0.75);
        // 120-thick body -> 90 down; 40-thick body -> 30 down. Same relative position.
        assertEquals(300 - 90, band.resolveY(300, 180, 0.75));
        assertEquals(300 - 30, band.resolveY(300, 260, 0.75));
    }

    @Test void sampleDepthStaysInsideTheBand() {
        ColumnBand band = blocks(0.2, 0.8, 128);
        RandomSource random = RandomSource.create(1234L);
        for (int i = 0; i < 500; i++) {
            double d = band.sampleDepth(random);
            assertTrue(d >= 0.2 && d <= 0.8, "sample outside band: " + d);
        }
    }

    @Test void biasedLowLeansDeep_biasedHighLeansShallow() {
        // Distributions are stated in terms of Y, so "biased_low" (low Y) = deeper = larger depth.
        assertTrue(meanDepth(HeightDistribution.BIASED_LOW) > 0.55);
        assertTrue(meanDepth(HeightDistribution.BIASED_HIGH) < 0.45);
        double uniform = meanDepth(HeightDistribution.UNIFORM);
        assertTrue(uniform > 0.45 && uniform < 0.55, "uniform mean drifted: " + uniform);
        double triangular = meanDepth(HeightDistribution.TRAPEZOID);
        assertTrue(triangular > 0.45 && triangular < 0.55, "trapezoid mean drifted: " + triangular);
    }

    private static double meanDepth(HeightDistribution dist) {
        ColumnBand band = new ColumnBand(SurfaceAnchor.WorldSurface.INSTANCE,
                SurfaceAnchor.WorldFloor.DEFAULT, 0.0, 1.0,
                ColumnBand.DepthScale.BLOCKS, 128, dist);
        RandomSource random = RandomSource.create(99L);
        double sum = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) sum += band.sampleDepth(random);
        return sum / n;
    }

    @Test void trapezoidIsMoreCentredThanUniform() {
        // Triangular sampling must actually concentrate; a mean test alone would not catch it.
        assertTrue(fractionInMiddleThird(HeightDistribution.TRAPEZOID)
                > fractionInMiddleThird(HeightDistribution.UNIFORM) + 0.1);
    }

    private static double fractionInMiddleThird(HeightDistribution dist) {
        ColumnBand band = new ColumnBand(SurfaceAnchor.WorldSurface.INSTANCE,
                SurfaceAnchor.WorldFloor.DEFAULT, 0.0, 1.0,
                ColumnBand.DepthScale.BLOCKS, 128, dist);
        RandomSource random = RandomSource.create(7L);
        int n = 20_000;
        int hits = 0;
        for (int i = 0; i < n; i++) {
            double d = band.sampleDepth(random);
            if (d >= 1 / 3.0 && d <= 2 / 3.0) hits++;
        }
        return hits / (double) n;
    }

    @Test void rejectsInvertedBandAndNonPositiveThickness() {
        assertThrows(IllegalArgumentException.class, () -> blocks(0.8, 0.2, 128));
        assertThrows(IllegalArgumentException.class, () -> blocks(0.2, 0.8, 0));
    }
}
