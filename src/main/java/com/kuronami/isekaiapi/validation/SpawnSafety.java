package com.kuronami.isekaiapi.validation;

import org.jetbrains.annotations.ApiStatus;

/**
 * Pure start-point safety rule for {@code /isekai validate dimension <id>}: can a player stand
 * at the dimension's start point without being buried, floating over nothing, or standing in
 * lava?
 *
 * <p>The rule is deliberately block-ID-free. A column is sampled through {@link Column}, which
 * the runtime adapter fills from {@code BlockState#isSolidRender} and
 * {@code BlockState#getFluidState()}; that keeps the decision unit-testable with stubs.
 *
 * <ol>
 *   <li>walk down at most {@value #SEARCH_DEPTH} blocks from the start point; the first solid
 *       block is the footing</li>
 *   <li>the two blocks above the footing must be neither solid nor fluid</li>
 *   <li>a water footing (or fluid in the standing space) is {@link Status#WARN}; lava is
 *       {@link Status#ERROR}</li>
 * </ol>
 *
 * <p>{@link Status#SKIP} is not produced here — the caller uses {@link #skipped(String)} when
 * the dimension has no loaded {@code ServerLevel} to sample.
 *
 * @since 3.0.0
 */
@ApiStatus.Internal
public final class SpawnSafety {

    /** How far below the start point the footing may be. */
    public static final int SEARCH_DEPTH = 32;

    /** The four states the diagnostic reports. */
    public enum Status { OK, WARN, ERROR, SKIP }

    /** One sampled block. {@code lava} is only meaningful when {@code fluid} is true. */
    public record Sample(boolean solid, boolean fluid, boolean lava) {
        public static final Sample AIR = new Sample(false, false, false);
        public static final Sample SOLID = new Sample(true, false, false);
    }

    /** One vertical column of samples, addressed by absolute Y. */
    @FunctionalInterface
    public interface Column {
        Sample sample(int y);
    }

    /** The verdict plus a one-line English detail that names the Y coordinates involved. */
    public record Result(Status status, String detail) {}

    private SpawnSafety() {}

    /** Verdict for a dimension that has no loaded {@code ServerLevel}. */
    public static Result skipped(String detail) {
        return new Result(Status.SKIP, detail);
    }

    /**
     * @param column  the column at the start point's X/Z
     * @param startY  Y of the start point (the player's feet)
     * @param minY    the dimension's minimum build height; the search never goes below it
     */
    public static Result evaluate(Column column, int startY, int minY) {
        int lowest = Math.max(minY, startY - SEARCH_DEPTH);
        int footingY = Integer.MIN_VALUE;
        for (int y = startY; y >= lowest; y--) {
            if (column.sample(y).solid()) {
                footingY = y;
                break;
            }
        }
        if (footingY == Integer.MIN_VALUE) {
            return new Result(Status.ERROR,
                    "no solid block within " + SEARCH_DEPTH + " blocks below y=" + startY);
        }

        Sample footing = column.sample(footingY);
        if (footing.lava()) {
            return new Result(Status.ERROR, "footing at y=" + footingY + " is lava");
        }
        if (footingY >= startY) {
            return new Result(Status.ERROR,
                    "start point is inside a solid block at y=" + startY);
        }

        boolean wet = footing.fluid();
        for (int y = footingY + 1; y <= footingY + 2; y++) {
            Sample above = column.sample(y);
            if (above.lava()) {
                return new Result(Status.ERROR, "standing space at y=" + y + " is lava");
            }
            if (above.solid()) {
                return new Result(Status.ERROR, "standing space at y=" + y + " is a solid block");
            }
            if (above.fluid()) {
                wet = true;
            }
        }
        if (wet) {
            return new Result(Status.WARN,
                    "footing at y=" + footingY + " is under fluid; 2 blocks above are not solid"
                    + " but wet");
        }
        return new Result(Status.OK, "footing at y=" + footingY + "; 2 blocks above are clear");
    }
}
