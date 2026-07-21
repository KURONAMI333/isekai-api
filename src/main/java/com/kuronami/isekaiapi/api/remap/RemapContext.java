package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.VerticalRange;

/**
 * The envelope a {@link RemapStrategy} maps a feature's vanilla vertical range into: the
 * consumer's {@code playable} range plus the vanilla world's build bounds
 * ({@code worldBottom}..{@code worldTop}) that source ranges are interpreted against.
 *
 * @since 2.0.0
 */
public record RemapContext(VerticalRange playable, int worldBottom, int worldTop) {

    /**
     * Proportional scale: {@code original}'s [min, max] mapped into {@link #playable()} by
     * treating both ranges as fractions of their respective world envelopes. A feature at
     * vanilla Y=10..30 within world -64..320 keeps its proportional position and width when
     * placed into the playable range. Shared by {@code Linear}, {@code Inverted}, and the
     * {@code BandSplit} out-of-band fallback.
     * @since 2.0.0
     */
    public VerticalRange linearScale(VerticalRange original) {
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
}
