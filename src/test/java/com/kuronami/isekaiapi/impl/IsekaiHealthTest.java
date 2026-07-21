package com.kuronami.isekaiapi.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-state tests for {@link IsekaiHealth} — the degraded flag and lenient-drop tally that
 * turn the two previously-silent failure modes into {@code /isekai stats} output. No game
 * bootstrap needed; this is process-global mutable state.
 */
class IsekaiHealthTest {

    @AfterEach
    void clean() {
        IsekaiHealth.reset();
    }

    @Test
    void startsHealthy() {
        assertFalse(IsekaiHealth.isDegraded());
        assertTrue(IsekaiHealth.droppedEntries().isEmpty());
    }

    @Test
    void markDegradedRecordsReason() {
        IsekaiHealth.markDegraded("scan blew up: boom");
        assertTrue(IsekaiHealth.isDegraded());
        assertEquals("scan blew up: boom", IsekaiHealth.degradedReason());
    }

    @Test
    void resetClearsEverything() {
        IsekaiHealth.markDegraded("x");
        IsekaiHealth.setDroppedForDirectory("isekai/worldshape", List.of("ns:a"));
        IsekaiHealth.reset();
        assertFalse(IsekaiHealth.isDegraded());
        assertTrue(IsekaiHealth.droppedEntries().isEmpty());
    }

    @Test
    void dropsAggregateAcrossDirectories() {
        IsekaiHealth.setDroppedForDirectory("isekai/worldshape", List.of("ns:a", "ns:b"));
        IsekaiHealth.setDroppedForDirectory("isekai/layered_worldshape", List.of("ns:c"));
        assertEquals(3, IsekaiHealth.droppedEntries().size());
    }

    @Test
    void reloadReplacesDirectoryDropsRatherThanAccumulating() {
        IsekaiHealth.setDroppedForDirectory("isekai/worldshape", List.of("ns:a", "ns:b"));
        // A subsequent reload of the same directory with only one failure must not leave the
        // previous two behind — stats should reflect the latest reload, not a running total.
        IsekaiHealth.setDroppedForDirectory("isekai/worldshape", List.of("ns:a"));
        assertEquals(List.of("ns:a"), IsekaiHealth.droppedEntries());
    }

    @Test
    void emptyDropListForDirectoryClearsIt() {
        IsekaiHealth.setDroppedForDirectory("isekai/worldshape", List.of("ns:a"));
        IsekaiHealth.setDroppedForDirectory("isekai/worldshape", List.of());
        assertTrue(IsekaiHealth.droppedEntries().isEmpty());
    }
}
