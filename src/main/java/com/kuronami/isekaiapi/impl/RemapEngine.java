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
        if (strategy instanceof RemapStrategy.NonLinear nl) {
            return nonLinearScale(nl, original, playable, worldBottom, worldTop);
        }
        if (strategy instanceof RemapStrategy.Custom custom) {
            return custom.fn().apply(original, playable);
        }
        if (strategy instanceof RemapStrategy.BandSplit) {
            // BandSplit needs per-band metadata (which band this feature belongs to). The
            // engine doesn't yet receive that — fall back to Linear so the result is at
            // least sensible. Per-band dispatching is a v0.8 deliverable.
            return linearScale(original, playable, worldBottom, worldTop);
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
     * Apply a NonLinear's {@code Function<Float, Float>} mapping at both endpoints of the
     * original range (interpreted as proportions of {@code [worldBottom, worldTop]}) and
     * project the mapped fractions into the playable range.
     */
    private static VerticalRange nonLinearScale(RemapStrategy.NonLinear nl,
                                                 VerticalRange original, VerticalRange playable,
                                                 int worldBottom, int worldTop) {
        int worldSpan = worldTop - worldBottom;
        if (worldSpan <= 0) return playable;
        float tMin = (original.minY() - worldBottom) / (float) worldSpan;
        float tMax = (original.maxY() - worldBottom) / (float) worldSpan;
        float mappedMin = nl.mapping().apply(Math.max(0f, Math.min(1f, tMin)));
        float mappedMax = nl.mapping().apply(Math.max(0f, Math.min(1f, tMax)));
        if (mappedMin > mappedMax) {
            float tmp = mappedMin; mappedMin = mappedMax; mappedMax = tmp;
        }
        int playSpan = playable.maxY() - playable.minY();
        int newMin = playable.minY() + Math.round(mappedMin * playSpan);
        int newMax = playable.minY() + Math.round(mappedMax * playSpan);
        return new VerticalRange(newMin, newMax, original.distribution());
    }

    /**
     * Convenience for tests / commands: apply with a fixed source envelope.
     * Equivalent to {@code apply(strategy, original, playable, -64, 320)}.
     */
    public static VerticalRange applyVanillaOverworld(RemapStrategy strategy,
                                                       VerticalRange original,
                                                       VerticalRange playable) {
        return apply(strategy, original, playable, -64, 320);
    }
}
