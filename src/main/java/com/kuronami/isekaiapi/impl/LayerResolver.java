package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.TransitionRule;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

/**
 * Resolves which layer of a multi-layer worldshape owns a given block position, applying the
 * layer's {@link TransitionRule} at the seam it owns.
 *
 * <p>A {@link LayeredDescriptor}'s transition governs the seam to the layer directly above it
 * (the layer whose {@code minY} equals this layer's {@code maxY}):
 *
 * <ul>
 *   <li>{@link TransitionRule.Hard} — butt join. {@code y} belongs to whichever layer's
 *       half-open {@code [minY, maxY)} contains it.</li>
 *   <li>{@link TransitionRule.Blend} — the two descriptors interleave across a band of
 *       {@code blendHeight} blocks centred on the seam. Within the band each position picks
 *       the upper or the lower descriptor, with the odds shifting linearly from
 *       "almost always lower" at the bottom of the band to "almost always upper" at the top.
 *       Because the pick is per-position, the visible result is a dithered gradient rather
 *       than a straight line — which is what the descriptor's per-Y effects (surface blocks,
 *       default block, client atmosphere, structure predicates) can express.</li>
 *   <li>{@link TransitionRule.Gap} — the layer gives up its top {@code gapHeight} blocks:
 *       no descriptor applies there, exactly as if the two {@code y_range}s had been
 *       authored that far apart.</li>
 * </ul>
 *
 * <p><b>Determinism.</b> The per-position pick is a pure hash of {@code (x, y, z, seam)} —
 * no world seed, no RNG state, no call ordering. The same coordinates always resolve to the
 * same layer, in any world, on any run, so chunk generation stays reproducible and a
 * regenerated chunk is identical to the original.
 *
 * <p>A Blend whose seam has no adjacent layer above it (the {@code y_range}s are not
 * touching) has nothing to blend into and degrades to {@code Hard}.
 */
@ApiStatus.Internal
public final class LayerResolver {

    private LayerResolver() {}

    /**
     * The descriptor active at {@code (x, y, z)} within a layered stack, or empty when the
     * position falls in a gap (authored or {@link TransitionRule.Gap}-derived).
     */
    public static Optional<WorldshapeDescriptor> resolve(List<LayeredDescriptor> layers,
                                                          int x, int y, int z) {
        return resolve(layers, x, y, z, true);
    }

    /**
     * Y-only resolution for callers that have no X/Z — the client fog hook, and the
     * {@code getDescriptorAt(dimension, y)} overload it goes through.
     *
     * <p>{@link TransitionRule.Blend} is deliberately <em>not</em> applied here. A blend is a
     * spatial interleave; resolving it from Y alone would make a caller that moves through the
     * band flip between the two descriptors at random, which for fog reads as flicker. Y-only
     * queries therefore see the seam as a hard boundary. {@link TransitionRule.Gap} still
     * applies — a gap is a Y-interval, fully expressible without X/Z.
     */
    public static Optional<WorldshapeDescriptor> resolveByY(List<LayeredDescriptor> layers, int y) {
        return resolve(layers, 0, y, 0, false);
    }

    private static Optional<WorldshapeDescriptor> resolve(List<LayeredDescriptor> layers,
                                                           int x, int y, int z, boolean blended) {
        if (layers.isEmpty()) return Optional.empty();

        LayeredDescriptor containing = null;
        for (LayeredDescriptor l : layers) {
            if (y >= l.yRange().minY() && y < l.yRange().maxY()) {
                containing = l;
                break;
            }
        }

        // Gap is carved out of the top of the layer that owns the seam, so it is decided
        // before any blend: a layer cannot both end in empty space and interleave upward.
        if (containing != null
                && containing.transition() instanceof TransitionRule.Gap gap
                && gap.gapHeight() > 0
                && y >= containing.yRange().maxY() - gap.gapHeight()) {
            return Optional.empty();
        }

        for (LayeredDescriptor lower : layers) {
            if (!blended) break;
            if (!(lower.transition() instanceof TransitionRule.Blend blend)) continue;
            int height = blend.blendHeight();
            if (height <= 0) continue;
            int seam = lower.yRange().maxY();
            LayeredDescriptor upper = adjacentAbove(layers, seam);
            if (upper == null) continue;  // nothing to blend into — behaves as Hard

            // Band centred on the seam, clamped so it never reaches past either layer's own
            // extent (a blend_height taller than the layers would otherwise leak into a third).
            int bandMin = Math.max(seam - height / 2, lower.yRange().minY());
            int bandMax = Math.min(bandMin + height, upper.yRange().maxY());
            if (bandMax <= bandMin || y < bandMin || y >= bandMax) continue;

            double upperOdds = (y - bandMin + 0.5) / (bandMax - bandMin);
            return Optional.of(sample(x, y, z, seam) < upperOdds
                    ? upper.descriptor()
                    : lower.descriptor());
        }

        return containing == null ? Optional.empty() : Optional.of(containing.descriptor());
    }

    /** The layer that starts exactly where {@code seam} ends, or {@code null} if none does. */
    private static LayeredDescriptor adjacentAbove(List<LayeredDescriptor> layers, int seam) {
        for (LayeredDescriptor l : layers) {
            if (l.yRange().minY() == seam) return l;
        }
        return null;
    }

    /**
     * Uniform value in {@code [0, 1)} from a position and a salt. SplitMix64 finalizer over
     * odd-multiplier-mixed coordinates: no state, no allocation, and decorrelated between
     * neighbouring blocks so the blend band reads as noise rather than as stripes.
     */
    static double sample(int x, int y, int z, int salt) {
        long h = (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) z * 0x165667B19E3779F9L;
        h ^= (long) salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
