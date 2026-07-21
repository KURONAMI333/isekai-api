package com.kuronami.isekaiapi.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;

/**
 * Process-wide health signals for the worldgen pipeline. Turns previously silent failure
 * modes into observable state that {@code /isekai stats} reports:
 * <ul>
 *   <li>a swallowed snapshot-scan exception — {@link #isDegraded()} + {@link #degradedReason()};</li>
 *   <li>datapack entries the lenient reload path skipped — {@link #droppedEntries()},
 *       tracked per source directory so each reload replaces (not accumulates) its own set.</li>
 * </ul>
 *
 * <p>Reset per world at {@code onServerStopping} so a fresh world doesn't inherit the
 * previous one's degraded flag or drop list.
 */
@ApiStatus.Internal
public final class IsekaiHealth {

    private IsekaiHealth() {}

    /**
     * Strict-mode toggle, read once at class load from {@code -Disekai.strict=true}. When on,
     * a swallowed snapshot-scan failure rethrows instead of degrading silently, and the reload
     * pipeline aborts on any decode failure. Single canonical definition for both call sites.
     */
    public static final boolean STRICT_MODE = Boolean.getBoolean("isekai.strict");

    private static volatile boolean degraded = false;
    private static volatile String degradedReason = null;
    private static final Map<String, List<String>> droppedByDirectory = new ConcurrentHashMap<>();

    /** Flag the snapshot pipeline as degraded (remap inactive) with a human-readable reason. */
    public static void markDegraded(String reason) {
        degraded = true;
        degradedReason = reason;
    }

    public static boolean isDegraded() {
        return degraded;
    }

    public static String degradedReason() {
        return degradedReason;
    }

    /**
     * Replace the recorded drop list for one reload-source directory. Called once per reload
     * per directory so stats reflect the most recent reload, not a running total across reloads.
     */
    public static void setDroppedForDirectory(String directory, List<String> ids) {
        if (ids.isEmpty()) {
            droppedByDirectory.remove(directory);
        } else {
            droppedByDirectory.put(directory, List.copyOf(ids));
        }
    }

    /** Every datapack entry the lenient reload path skipped in the most recent reload. */
    public static List<String> droppedEntries() {
        List<String> all = new ArrayList<>();
        droppedByDirectory.values().forEach(all::addAll);
        return List.copyOf(all);
    }

    /** Clear all health signals — called on server stop so the next world starts clean. */
    public static void reset() {
        degraded = false;
        degradedReason = null;
        droppedByDirectory.clear();
    }
}
