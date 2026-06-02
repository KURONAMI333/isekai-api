package com.kuronami.isekaiapi.api.biomesource;

import net.minecraft.core.QuartPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BiomeZone} geometry predicates.
 * quart coord → block coord via QuartPos.toBlock (= quartCoord * 4).
 * No game bootstrap needed — these are pure geometry computations.
 */
class BiomeZoneTest {

    // Helper: convert block coords to quart coords for test inputs
    private static int bq(int block) { return QuartPos.fromBlock(block); }

    // ===== Always =====

    @Test void always_typeId() {
        assertEquals("isekai:always", new BiomeZone.Always().typeId());
    }

    @Test void always_testReturnsTrue_everywhere() {
        BiomeZone.Always z = new BiomeZone.Always();
        assertTrue(z.test(0, 0, 0));
        assertTrue(z.test(-1000, 500, 1000));
    }

    // ===== YAbove =====

    @Test void yAbove_typeId() {
        assertEquals("isekai:y_above", new BiomeZone.YAbove(0).typeId());
    }

    @Test void yAbove_blockYAboveThreshold_returnsTrue() {
        BiomeZone.YAbove z = new BiomeZone.YAbove(64);
        // quart y=17 → blockY=68 >= 64
        assertTrue(z.test(0, bq(68), 0));
    }

    @Test void yAbove_blockYAtThreshold_returnsTrue() {
        BiomeZone.YAbove z = new BiomeZone.YAbove(64);
        // quart y such that toBlock(qy)=64
        assertTrue(z.test(0, bq(64), 0));
    }

    @Test void yAbove_blockYBelowThreshold_returnsFalse() {
        BiomeZone.YAbove z = new BiomeZone.YAbove(64);
        assertFalse(z.test(0, bq(60), 0));
    }

    // ===== YBelow =====

    @Test void yBelow_typeId() {
        assertEquals("isekai:y_below", new BiomeZone.YBelow(0).typeId());
    }

    @Test void yBelow_blockYBelowThreshold_returnsTrue() {
        BiomeZone.YBelow z = new BiomeZone.YBelow(64);
        assertTrue(z.test(0, bq(60), 0));
    }

    @Test void yBelow_blockYAtThreshold_returnsFalse() {
        BiomeZone.YBelow z = new BiomeZone.YBelow(64);
        // < 64 (not <=), so exactly 64 → false
        assertFalse(z.test(0, bq(64), 0));
    }

    @Test void yBelow_blockYAboveThreshold_returnsFalse() {
        BiomeZone.YBelow z = new BiomeZone.YBelow(64);
        assertFalse(z.test(0, bq(68), 0));
    }

    // ===== YBetween =====

    @Test void yBetween_typeId() {
        assertEquals("isekai:y_between", new BiomeZone.YBetween(0, 100).typeId());
    }

    @Test void yBetween_insideRange_returnsTrue() {
        BiomeZone.YBetween z = new BiomeZone.YBetween(0, 100);
        assertTrue(z.test(0, bq(0), 0));   // min boundary
        assertTrue(z.test(0, bq(50), 0));  // middle
        assertTrue(z.test(0, bq(96), 0));  // just below max (96 < 100)
    }

    @Test void yBetween_atMaxBoundary_returnsFalse() {
        // y_between is [min, max) exclusive on max
        BiomeZone.YBetween z = new BiomeZone.YBetween(0, 100);
        assertFalse(z.test(0, bq(100), 0));
    }

    @Test void yBetween_belowMin_returnsFalse() {
        BiomeZone.YBetween z = new BiomeZone.YBetween(0, 100);
        assertFalse(z.test(0, bq(-4), 0));
    }

    @Test void yBetween_minMustBeLessThanMax_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BiomeZone.YBetween(100, 100));
        assertThrows(IllegalArgumentException.class, () -> new BiomeZone.YBetween(200, 100));
    }

    // ===== WithinDistance =====

    @Test void withinDistance_typeId() {
        assertEquals("isekai:within_distance", new BiomeZone.WithinDistance(100.0, 0, 0).typeId());
    }

    @Test void withinDistance_atOrigin_isWithin() {
        BiomeZone.WithinDistance z = new BiomeZone.WithinDistance(100.0, 0, 0);
        assertTrue(z.test(bq(0), 0, bq(0)));
    }

    @Test void withinDistance_atExactRadius_isWithin() {
        // radius=100, point (100,0) → distance=100 (<=100)
        BiomeZone.WithinDistance z = new BiomeZone.WithinDistance(100.0, 0, 0);
        // qx such that blockX=100
        assertTrue(z.test(bq(100), 0, bq(0)));
    }

    @Test void withinDistance_beyondRadius_isFalse() {
        BiomeZone.WithinDistance z = new BiomeZone.WithinDistance(100.0, 0, 0);
        // (108, 0) → dist=108 > 100
        assertFalse(z.test(bq(108), 0, bq(0)));
    }

    @Test void withinDistance_negativeRadius_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BiomeZone.WithinDistance(-1.0, 0, 0));
    }

    // ===== BeyondDistance =====

    @Test void beyondDistance_typeId() {
        assertEquals("isekai:beyond_distance", new BiomeZone.BeyondDistance(100.0, 0, 0).typeId());
    }

    @Test void beyondDistance_beyondRadius_isTrue() {
        BiomeZone.BeyondDistance z = new BiomeZone.BeyondDistance(100.0, 0, 0);
        assertTrue(z.test(bq(108), 0, bq(0)));
    }

    @Test void beyondDistance_withinRadius_isFalse() {
        BiomeZone.BeyondDistance z = new BiomeZone.BeyondDistance(100.0, 0, 0);
        assertFalse(z.test(bq(0), 0, bq(0)));
        assertFalse(z.test(bq(100), 0, bq(0))); // exactly radius → not beyond
    }

    @Test void beyondDistance_negativeRadius_throws() {
        assertThrows(IllegalArgumentException.class, () -> new BiomeZone.BeyondDistance(-1.0, 0, 0));
    }

    // ===== And =====

    @Test void and_typeId() {
        assertEquals("isekai:and", new BiomeZone.And(List.of()).typeId());
    }

    @Test void and_emptyList_returnsTrue() {
        // vacuously true (all of nothing)
        assertTrue(new BiomeZone.And(List.of()).test(0, 0, 0));
    }

    @Test void and_allTrue_returnsTrue() {
        var z = new BiomeZone.And(List.of(new BiomeZone.Always(), new BiomeZone.Always()));
        assertTrue(z.test(0, 0, 0));
    }

    @Test void and_oneChildFails_returnsFalse() {
        // YAbove(1000) will be false at y=0
        var z = new BiomeZone.And(List.of(
                new BiomeZone.Always(),
                new BiomeZone.YAbove(1000)));
        assertFalse(z.test(0, bq(0), 0));
    }

    @Test void and_listIsCopied() {
        var list = new java.util.ArrayList<BiomeZone>(List.of(new BiomeZone.Always()));
        var and = new BiomeZone.And(list);
        list.clear();
        assertEquals(1, and.all().size());
    }

    // ===== Or =====

    @Test void or_typeId() {
        assertEquals("isekai:or", new BiomeZone.Or(List.of()).typeId());
    }

    @Test void or_emptyList_returnsFalse() {
        assertFalse(new BiomeZone.Or(List.of()).test(0, 0, 0));
    }

    @Test void or_oneChildTrue_returnsTrue() {
        var z = new BiomeZone.Or(List.of(
                new BiomeZone.YAbove(1000),  // false at y=0
                new BiomeZone.Always()));
        assertTrue(z.test(0, bq(0), 0));
    }

    @Test void or_allChildrenFalse_returnsFalse() {
        var z = new BiomeZone.Or(List.of(
                new BiomeZone.YAbove(1000),
                new BiomeZone.YBelow(-1000)));
        assertFalse(z.test(0, bq(0), 0));
    }

    // ===== Not =====

    @Test void not_typeId() {
        assertEquals("isekai:not", new BiomeZone.Not(new BiomeZone.Always()).typeId());
    }

    @Test void not_invertsTrueToFalse() {
        assertFalse(new BiomeZone.Not(new BiomeZone.Always()).test(0, 0, 0));
    }

    @Test void not_invertsFalseToTrue() {
        // YAbove(1000) is false at y=0 → Not returns true
        assertTrue(new BiomeZone.Not(new BiomeZone.YAbove(1000)).test(0, bq(0), 0));
    }

    @Test void not_doubleNegation_restoresOriginal() {
        var zone = new BiomeZone.YAbove(64);
        var doubleNot = new BiomeZone.Not(new BiomeZone.Not(zone));
        assertEquals(zone.test(0, bq(100), 0), doubleNot.test(0, bq(100), 0));
        assertEquals(zone.test(0, bq(0), 0), doubleNot.test(0, bq(0), 0));
    }

    // ===== WithinDistance vs BeyondDistance complement =====

    @Test void withinAndBeyond_areComplements() {
        double radius = 50.0;
        var within = new BiomeZone.WithinDistance(radius, 0, 0);
        var beyond = new BiomeZone.BeyondDistance(radius, 0, 0);
        // at distance > radius: within=false, beyond=true
        assertTrue(beyond.test(bq(60), 0, bq(0)));
        assertFalse(within.test(bq(60), 0, bq(0)));
        // at origin: within=true, beyond=false
        assertTrue(within.test(bq(0), 0, bq(0)));
        assertFalse(beyond.test(bq(0), 0, bq(0)));
    }

    // ===== Non-zero center =====

    @Test void withinDistance_nonZeroCenter_shiftedCheck() {
        // center (100,100), radius=10 → origin (0,0) is at dist≈141 → beyond
        var z = new BiomeZone.WithinDistance(10.0, 100, 100);
        assertFalse(z.test(bq(0), 0, bq(0)));
        // point (100,100) → dist=0 ≤ 10 → within
        assertTrue(z.test(bq(100), 0, bq(100)));
    }
}
