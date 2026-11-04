package com.kuronami.isekaiapi.placementmodifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * Geometry behind {@code isekai_api:biome_falloff}, with no world attached.
 *
 * <p>"Place near this biome, thinning out with distance" is normally written by measuring the
 * distance to the nearest cell of that biome and comparing it against a drawn threshold. Measuring
 * is expensive: a biome sample is four blocks wide, so a 128-block radius is thousands of samples
 * per candidate position, every candidate, whether or not the answer is "no".
 *
 * <p>This does the same job with a ring search that stops at the first hit. One uniform draw
 * {@code u} becomes a radius {@code full + (max - full) * (1 - u^(1/exponent))}, and the position is
 * accepted when any target cell falls inside that radius. Since
 * {@code P(radius >= d) = t^exponent} with {@code t = (max - d) / (max - full)}, the acceptance rate
 * is the intended falloff, and a position deep inside the biome costs one sample instead of
 * thousands.
 *
 * <p>Radii are covered by whole Chebyshev rings of the sample lattice, so the ring that ends the
 * search is never the one nearest the origin: a target three blocks away is found when the ring
 * holding it is reached. Callers that want a hard cutoff rather than a falloff pass
 * {@code full == max} (see {@link BiomeFalloffModifier}), which asks for the full radius on every
 * draw.
 *
 * <p>Vertical sampling is always four blocks — one biome cell — whatever the horizontal
 * {@code step} is: coarser horizontal sampling is a cost trade, not a licence to miss the biome at
 * the candidate's own altitude.
 */
@ApiStatus.Internal
public final class BiomeFalloffSearch {

    /** Answers "is this cell inside the target biome?" at block coordinates. */
    @FunctionalInterface
    public interface TargetProbe {
        boolean isTarget(int x, int y, int z);
    }

    /** {@code found}: whether a target cell was reached. {@code probed}: how many cells were asked. */
    public record Result(boolean found, int probed) {}

    /** Vertical sampling interval, in blocks — one biome cell. */
    public static final int VERTICAL_STEP = 4;

    private BiomeFalloffSearch() {}

    /**
     * The radius one uniform draw {@code u} in {@code [0, 1)} asks the search to cover.
     *
     * <p>{@code full >= max} is the hard filter: every draw asks for the whole radius instead of
     * dividing by a zero-width falloff band.
     */
    public static double radius(double u, int fullDistance, int maxDistance, float exponent) {
        if (fullDistance >= maxDistance) {
            return maxDistance;
        }
        double spread = 1.0 - Math.pow(Math.min(u, 1.0), 1.0 / exponent);
        return fullDistance + (maxDistance - fullDistance) * spread;
    }

    /**
     * Look for one target cell within {@code radius} of {@code (originX, originY, originZ)}, nearest
     * ring first, and stop at the first hit.
     *
     * <p>Each ring is the square of lattice cells at Chebyshev distance {@code ring * step}; cells
     * whose Euclidean distance exceeds {@code radius} are skipped. The lattice is anchored on the
     * origin, so the origin cell itself is always the first one asked.
     */
    public static Result search(int originX, int originY, int originZ, double radius, int step,
                                int below, int above, TargetProbe probe) {
        if (radius < 0) {
            return new Result(false, 0);
        }
        Cursor cursor = new Cursor(probe);
        int rings = (int) Math.floor(radius / step);
        double radiusSquared = radius * radius;
        if (cursor.column(originX, originY, originZ, below, above)) {
            return new Result(true, cursor.probed);
        }
        for (int ring = 1; ring <= rings; ring++) {
            int offset = ring * step;
            // Near and far edges of the ring, corners included.
            for (int dx = -offset; dx <= offset; dx += step) {
                if (within(dx, offset, radiusSquared)
                        && cursor.column(originX + dx, originY, originZ + offset, below, above)) {
                    return new Result(true, cursor.probed);
                }
                if (within(dx, -offset, radiusSquared)
                        && cursor.column(originX + dx, originY, originZ - offset, below, above)) {
                    return new Result(true, cursor.probed);
                }
            }
            // Left and right edges; the corners are already covered above.
            for (int dz = -offset + step; dz <= offset - step; dz += step) {
                if (within(offset, dz, radiusSquared)
                        && cursor.column(originX + offset, originY, originZ + dz, below, above)) {
                    return new Result(true, cursor.probed);
                }
                if (within(-offset, dz, radiusSquared)
                        && cursor.column(originX - offset, originY, originZ + dz, below, above)) {
                    return new Result(true, cursor.probed);
                }
            }
        }
        return new Result(false, cursor.probed);
    }

    private static boolean within(int dx, int dz, double radiusSquared) {
        return (double) dx * dx + (double) dz * dz <= radiusSquared;
    }

    /** Walks one column outward from the origin Y, nearest level first, and counts every ask. */
    private static final class Cursor {
        private final TargetProbe probe;
        private int probed;

        private Cursor(TargetProbe probe) {
            this.probe = probe;
        }

        private boolean column(int x, int y, int z, int below, int above) {
            probed++;
            if (probe.isTarget(x, y, z)) {
                return true;
            }
            int reach = Math.max(below, above);
            for (int dy = VERTICAL_STEP; dy <= reach; dy += VERTICAL_STEP) {
                if (dy <= above) {
                    probed++;
                    if (probe.isTarget(x, y + dy, z)) {
                        return true;
                    }
                }
                if (dy <= below) {
                    probed++;
                    if (probe.isTarget(x, y - dy, z)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
