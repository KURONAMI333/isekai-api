package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure data-logic tests for {@link RemapStrategy} variants.
 * No game bootstrap needed — all variants are pure Java records.
 */
class RemapStrategyTest {

    // ===== Linear =====

    @Test void linear_typeId() {
        assertEquals("isekai:linear", RemapStrategy.Linear.INSTANCE.typeId());
    }

    @Test void linear_instanceEquals() {
        // records: two instances with same fields are equal (even if not same reference)
        assertEquals(RemapStrategy.Linear.INSTANCE, new RemapStrategy.Linear());
    }

    // ===== Inverted =====

    @Test void inverted_typeId() {
        assertEquals("isekai:inverted", RemapStrategy.Inverted.INSTANCE.typeId());
    }

    @Test void inverted_instanceEquals() {
        assertEquals(RemapStrategy.Inverted.INSTANCE, new RemapStrategy.Inverted());
    }

    // ===== Identity =====

    @Test void identity_typeId() {
        assertEquals("isekai:identity", RemapStrategy.Identity.INSTANCE.typeId());
    }

    @Test void identity_instanceEquals() {
        assertEquals(RemapStrategy.Identity.INSTANCE, new RemapStrategy.Identity());
    }

    // ===== CountScale =====

    @Test void countScale_typeId() {
        assertEquals("isekai:count_scale", new RemapStrategy.CountScale(1.0).typeId());
    }

    @Test void countScale_storesFactor() {
        var cs = new RemapStrategy.CountScale(2.5);
        assertEquals(2.5, cs.factor(), 1e-9);
    }

    @Test void countScale_zeroFactor_isAllowed() {
        var cs = new RemapStrategy.CountScale(0.0);
        assertEquals(0.0, cs.factor(), 1e-9);
    }

    @Test void countScale_negativeFactor_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemapStrategy.CountScale(-0.1));
    }

    // ===== FixedRange =====

    @Test void fixedRange_typeId() {
        assertEquals("isekai:fixed_range",
                new RemapStrategy.FixedRange(0, 100, HeightDistribution.UNIFORM).typeId());
    }

    @Test void fixedRange_storesFields() {
        var fr = new RemapStrategy.FixedRange(-64, 320, HeightDistribution.TRAPEZOID);
        assertEquals(-64, fr.min());
        assertEquals(320, fr.max());
        assertEquals(HeightDistribution.TRAPEZOID, fr.dist());
    }

    @Test void fixedRange_minEqualsMax_isAllowed() {
        // min==max should not throw (only min>max is illegal)
        assertDoesNotThrow(() -> new RemapStrategy.FixedRange(50, 50, HeightDistribution.UNIFORM));
    }

    @Test void fixedRange_minGreaterThanMax_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemapStrategy.FixedRange(100, 50, HeightDistribution.UNIFORM));
    }

    // ===== BandSplit =====

    @Test void bandSplit_typeId() {
        var vr = new VerticalRange(-64, 320, HeightDistribution.UNIFORM);
        var band = new RemapStrategy.BandSplit.Band(vr, 1.0f);
        var bs = new RemapStrategy.BandSplit(List.of(band));
        assertEquals("isekai:band_split", bs.typeId());
    }

    @Test void bandSplit_emptyBands_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemapStrategy.BandSplit(List.of()));
    }

    @Test void bandSplit_bandsAreImmutableCopy() {
        var vr = new VerticalRange(0, 100, HeightDistribution.UNIFORM);
        var band = new RemapStrategy.BandSplit.Band(vr, 1.0f);
        var list = new java.util.ArrayList<>(List.of(band));
        var bs = new RemapStrategy.BandSplit(list);
        list.clear(); // mutate original
        assertEquals(1, bs.bands().size()); // copy should be unaffected
    }

    @Test void band_negativeTargetRatio_throws() {
        var vr = new VerticalRange(0, 100, HeightDistribution.UNIFORM);
        assertThrows(IllegalArgumentException.class,
                () -> new RemapStrategy.BandSplit.Band(vr, -0.1f));
    }

    // ===== Pipe =====

    @Test void pipe_typeId() {
        assertEquals("isekai:pipe",
                new RemapStrategy.Pipe(List.of(RemapStrategy.Identity.INSTANCE)).typeId());
    }

    @Test void pipe_emptyChain_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new RemapStrategy.Pipe(List.of()));
    }

    @Test void pipe_chainIsImmutableCopy() {
        var list = new java.util.ArrayList<RemapStrategy>(List.of(RemapStrategy.Identity.INSTANCE));
        var pipe = new RemapStrategy.Pipe(list);
        list.clear();
        assertEquals(1, pipe.chain().size());
    }

    @Test void pipe_multipleStrategies_storesAll() {
        var pipe = new RemapStrategy.Pipe(List.of(
                RemapStrategy.Identity.INSTANCE,
                new RemapStrategy.CountScale(2.0),
                RemapStrategy.Inverted.INSTANCE));
        assertEquals(3, pipe.chain().size());
    }
}
