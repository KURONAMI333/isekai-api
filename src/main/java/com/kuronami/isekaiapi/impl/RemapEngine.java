package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;

import java.util.List;

/**
 * Pure-logic transformer that applies a {@link RemapStrategy} to an original
 * {@link VerticalRange}, producing the remapped range that a generated feature
 * should occupy under a consumer's worldshape.
 *
 * <p>v0.7 supports the data-driven strategies: {@code Identity}, {@code Linear},
 * {@code Inverted}, {@code FixedRange}, {@code CountScale} (no-op on Y), and
 * {@code Pipe} (fold over chain). {@code BandSplit} requires per-band metadata
 * the engine doesn't yet receive and falls back to {@code Linear} with a debug
 * note. The Java-only variants ({@code NonLinear}, {@code Custom}) are applied
 * directly via their captured functions.
 *
 * <p>The source range for {@code Linear} / {@code Inverted} is the original feature's
 * {@link VerticalRange} interpreted as a sub-range of {@code [worldBottom, worldTop]};
 * the destination is the descriptor's {@code playableRange}. This preserves the
 * feature's proportional position and width within the new vertical envelope.
 */
public final class RemapEngine {

    private RemapEngine() {}

    /**
     * Apply {@code strategy} to {@code original}, producing a new range that fits inside
     * (or coincides with) {@code playable}. Returns {@code original} unchanged for any
     * strategy whose semantics don't affect Y placement (e.g. {@code CountScale}).
     *
     * @param strategy    the remap strategy to apply (must not be null)
     * @param original    the original (vanilla) feature's vertical range
     * @param playable    the consumer's target playable range
     * @param worldBottom vanilla world bottom build height (sourced from the snapshot)
     * @param worldTop    vanilla world top build height
     */
    public static VerticalRange apply(RemapStrategy strategy,
                                       VerticalRange original,
                                       VerticalRange playable,
                                       int worldBottom,
                                       int worldTop) {
        if (strategy instanceof RemapStrategy.Identity) {
            return original;
        }
        if (strategy instanceof RemapStrategy.CountScale) {
            // Count strategy affects feature density (handled elsewhere), not Y.
            return original;
        }
        if (strategy instanceof RemapStrategy.FixedRange fr) {
            return new VerticalRange(fr.min(), fr.max(), fr.dist());
        }
        if (strategy instanceof RemapStrategy.Linear) {
            return linearScale(original, playable, worldBottom, worldTop);
        }
        if (strategy instanceof RemapStrategy.Inverted) {
            VerticalRange linear = linearScale(original, playable, worldBottom, worldTop);
            int span = playable.maxY() - playable.minY();
            int newMin = playable.minY() + (playable.maxY() - linear.maxY());
            int newMax = playable.minY() + (playable.maxY() - linear.minY());
            return new VerticalRange(newMin, newMax, linear.distribution());
        }
        if (strategy instanceof RemapStrategy.Pipe pipe) {
            VerticalRange acc = original;
            for (RemapStrategy child : pipe.chain()) {
                acc = apply(child, acc, playable, worldBottom, worldTop);
            }
            return acc;
        }
        if (strategy instanceof RemapStrategy.BandSplit bs) {
            return bandSplit(bs, original, playable);
        }
        // Unknown / future variant — return original unchanged so downstream layers stay safe.
        return original;
    }

    /**
     * Proportional scale: original's [min, max] mapped into playable by treating both
     * ranges as fractions of their respective world envelopes. A feature at vanilla
     * Y=10..30 within world -64..320 keeps its (10-(-64))/(320-(-64))..(30-(-64))/(320-(-64))
     * = 0.193..0.245 proportion when placed into the playable range.
     */
    private static VerticalRange linearScale(VerticalRange original, VerticalRange playable,
                                              int worldBottom, int worldTop) {
        int worldSpan = worldTop - worldBottom;
        if (worldSpan <= 0) {
            // Degenerate world; fall back to playable bounds.
            return playable;
        }
        double tMin = (original.minY() - worldBottom) / (double) worldSpan;
        double tMax = (original.maxY() - worldBottom) / (double) worldSpan;
        int playSpan = playable.maxY() - playable.minY();
        int newMin = playable.minY() + (int) Math.round(tMin * playSpan);
        int newMax = playable.minY() + (int) Math.round(tMax * playSpan);
        // Clamp + guard against degenerate output where rounding collapses the range.
        newMin = Math.max(playable.minY(), Math.min(newMin, playable.maxY()));
        newMax = Math.max(newMin, Math.min(newMax, playable.maxY()));
        return new VerticalRange(newMin, newMax, original.distribution());
    }

    /**
     * Dispatch the feature's original range to the matching band, then project the feature
     * proportionally into that band's slice of the playable range. The matching band is the
     * one whose {@code vanillaSource} contains the feature's midpoint; ties (e.g. midpoint
     * exactly on a boundary) resolve to the lower-Y band. If no band matches (feature
     * outside every declared source range), fall back to {@link #linearScale}.
     *
     * <p>Each band's slice of the playable range starts at the cumulative sum of preceding
     * {@code targetRatio}s and spans this band's own ratio. The feature's proportional
     * position within its source band carries over into the same proportion within the
     * target slice.
     */
    private static VerticalRange bandSplit(RemapStrategy.BandSplit bs, VerticalRange original,
                                            VerticalRange playable) {
        double midpoint = (original.minY() + original.maxY()) / 2.0;
        int matchIndex = -1;
        for (int i = 0; i < bs.bands().size(); i++) {
            var band = bs.bands().get(i);
            if (midpoint >= band.vanillaSource().minY() && midpoint <= band.vanillaSource().maxY()) {
                matchIndex = i;
                break;
            }
        }
        if (matchIndex < 0) {
            // Feature lies outside every declared band; degrade gracefully to full-range linear.
            int playSpan = playable.maxY() - playable.minY();
            return new VerticalRange(playable.minY(), playable.minY() + playSpan, original.distribution());
        }
        // Compute this band's slice [sliceMin, sliceMax] in the playable range.
        float cumulativeBefore = 0f;
        for (int i = 0; i < matchIndex; i++) {
            cumulativeBefore += bs.bands().get(i).targetRatio();
        }
        var matched = bs.bands().get(matchIndex);
        int playSpan = playable.maxY() - playable.minY();
        int sliceMin = playable.minY() + Math.round(cumulativeBefore * playSpan);
        int sliceMax = playable.minY() + Math.round((cumulativeBefore + matched.targetRatio()) * playSpan);
        // Now scale the feature's range proportionally within this slice.
        var source = matched.vanillaSource();
        int sourceSpan = source.maxY() - source.minY();
        if (sourceSpan <= 0) {
            return new VerticalRange(sliceMin, sliceMax, original.distribution());
        }
        double tMin = (original.minY() - source.minY()) / (double) sourceSpan;
        double tMax = (original.maxY() - source.minY()) / (double) sourceSpan;
        int sliceSpan = sliceMax - sliceMin;
        int newMin = sliceMin + (int) Math.round(tMin * sliceSpan);
        int newMax = sliceMin + (int) Math.round(tMax * sliceSpan);
        newMin = Math.max(sliceMin, Math.min(newMin, sliceMax));
        newMax = Math.max(newMin, Math.min(newMax, sliceMax));
        return new VerticalRange(newMin, newMax, original.distribution());
    }

    /**
     * Walk a strategy tree and return the product of every {@link RemapStrategy.CountScale}
     * factor inside it (with {@code Pipe} chains folding multiplicatively). Other variants
     * contribute {@code 1.0}. Used by the biome modifier MODIFY phase to scale mob spawn
     * weights without separately re-implementing strategy traversal there.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code Identity} -> 1.0</li>
     *   <li>{@code CountScale(0.5)} -> 0.5</li>
     *   <li>{@code Pipe(Linear, CountScale(2.0))} -> 2.0 (Linear contributes 1.0)</li>
     *   <li>{@code Pipe(CountScale(2.0), CountScale(1.5))} -> 3.0</li>
     * </ul>
     */
    public static double effectiveCountFactor(RemapStrategy strategy) {
        if (strategy instanceof RemapStrategy.CountScale cs) {
            return cs.factor();
        }
        if (strategy instanceof RemapStrategy.Pipe pipe) {
            double product = 1.0;
            for (RemapStrategy child : pipe.chain()) {
                product *= effectiveCountFactor(child);
            }
            return product;
        }
        return 1.0;
    }
}
