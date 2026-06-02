package com.kuronami.isekaiapi.api.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure data-logic tests for {@link VerticalRange} and {@link HeightDistribution}.
 * No game bootstrap needed.
 */
class VerticalRangeTest {

    // ===== VerticalRange construction =====

    @Test void verticalRange_storesFields() {
        var vr = new VerticalRange(-64, 320, HeightDistribution.UNIFORM);
        assertEquals(-64, vr.minY());
        assertEquals(320, vr.maxY());
        assertEquals(HeightDistribution.UNIFORM, vr.distribution());
    }

    @Test void verticalRange_span_isMaxMinusMin() {
        var vr = new VerticalRange(-64, 320, HeightDistribution.UNIFORM);
        assertEquals(384, vr.span());
    }

    @Test void verticalRange_span_zeroForSameMinMax() {
        var vr = new VerticalRange(64, 64, HeightDistribution.UNIFORM);
        assertEquals(0, vr.span());
    }

    @Test void verticalRange_minYEqualsMaxY_isAllowed() {
        assertDoesNotThrow(() -> new VerticalRange(100, 100, HeightDistribution.TRAPEZOID));
    }

    @Test void verticalRange_minYGreaterThanMaxY_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerticalRange(100, 50, HeightDistribution.UNIFORM));
    }

    @Test void verticalRange_span_negativeRange_throws() {
        // constructor should throw before span() can be called
        assertThrows(IllegalArgumentException.class,
                () -> new VerticalRange(10, 5, HeightDistribution.UNIFORM));
    }

    @Test void verticalRange_equality_sameFields() {
        var a = new VerticalRange(0, 100, HeightDistribution.TRIANGLE);
        var b = new VerticalRange(0, 100, HeightDistribution.TRIANGLE);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test void verticalRange_equality_differentDistribution() {
        var a = new VerticalRange(0, 100, HeightDistribution.UNIFORM);
        var b = new VerticalRange(0, 100, HeightDistribution.TRIANGLE);
        assertNotEquals(a, b);
    }

    // ===== HeightDistribution =====

    @Test void heightDistribution_allVariantsHaveSerializedName() {
        for (var hd : HeightDistribution.values()) {
            assertNotNull(hd.getSerializedName());
            assertFalse(hd.getSerializedName().isBlank());
        }
    }

    @Test void heightDistribution_uniformSerializedName() {
        assertEquals("uniform", HeightDistribution.UNIFORM.getSerializedName());
    }

    @Test void heightDistribution_trapezoidSerializedName() {
        assertEquals("trapezoid", HeightDistribution.TRAPEZOID.getSerializedName());
    }

    @Test void heightDistribution_triangleSerializedName() {
        assertEquals("triangle", HeightDistribution.TRIANGLE.getSerializedName());
    }

    @Test void heightDistribution_biasedLowSerializedName() {
        assertEquals("biased_low", HeightDistribution.BIASED_LOW.getSerializedName());
    }

    @Test void heightDistribution_biasedHighSerializedName() {
        assertEquals("biased_high", HeightDistribution.BIASED_HIGH.getSerializedName());
    }

    @Test void heightDistribution_fiveVariantsTotal() {
        assertEquals(5, HeightDistribution.values().length);
    }

    // ===== VerticalRange span edge cases =====

    @Test void verticalRange_largePositiveRange_span() {
        var vr = new VerticalRange(0, Integer.MAX_VALUE, HeightDistribution.UNIFORM);
        assertEquals(Integer.MAX_VALUE, vr.span());
    }

    @Test void verticalRange_negativeRange_span() {
        var vr = new VerticalRange(-100, -10, HeightDistribution.BIASED_HIGH);
        assertEquals(90, vr.span());
    }
}
