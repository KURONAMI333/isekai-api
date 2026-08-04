package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.ColumnBand;
import com.kuronami.isekaiapi.api.remap.RemapContext;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;

import org.jetbrains.annotations.ApiStatus;

/**
 * Thin orchestration facade over {@link RemapStrategy}'s own logic. Each strategy projects a
 * range through {@link RemapStrategy#remap(VerticalRange, RemapContext)} and reports its density
 * multiplier through {@link RemapStrategy#countFactor()}; this class only bundles the world
 * envelope into a {@link RemapContext} and folds count factors, so callers keep a stable
 * entry point with no per-variant dispatch here.
 */
@ApiStatus.Internal
public final class RemapEngine {

    private RemapEngine() {}

    /**
     * Apply {@code strategy} to {@code original}, producing a new range that fits inside
     * (or coincides with) {@code playable}.
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
        return strategy.remap(original, new RemapContext(playable, worldBottom, worldTop));
    }

    /**
     * Ask {@code strategy} for a terrain-relative projection of {@code original}. Present only
     * for strategies that cannot express themselves as one absolute Y range (see
     * {@link RemapStrategy#remapToColumn}); callers fall back to {@link #apply} when empty.
     */
    public static java.util.Optional<ColumnBand> applyColumn(RemapStrategy strategy,
                                                             VerticalRange original,
                                                             VerticalRange playable,
                                                             int worldBottom,
                                                             int worldTop) {
        return strategy.remapToColumn(original, new RemapContext(playable, worldBottom, worldTop));
    }

    /**
     * The effective feature/mob density multiplier for a strategy tree — the product of every
     * {@link RemapStrategy.CountScale} factor inside it ({@code Pipe} chains fold
     * multiplicatively; other variants contribute 1.0). Used by the biome modifier MODIFY phase
     * to scale mob spawn weights.
     */
    public static double effectiveCountFactor(RemapStrategy strategy) {
        return strategy.countFactor();
    }
}
