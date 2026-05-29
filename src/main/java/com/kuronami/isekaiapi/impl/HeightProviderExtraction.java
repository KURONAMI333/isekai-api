package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.WeightedListHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Package-internal utilities for pulling a {@link VerticalRange} out of a
 * {@link PlacedFeature}'s {@link HeightRangePlacement} and resolving Mojang's
 * {@link VerticalAnchor} variants against a dimension's build-height bounds.
 *
 * <p>Supports six vanilla {@link HeightProvider} variants — UniformHeight, TrapezoidHeight,
 * ConstantHeight, BiasedToBottomHeight, VeryBiasedToBottomHeight, and WeightedListHeight.
 * Providers whose internals the Access Transformer does not expose return
 * {@link #FALLBACK_RANGE}.
 */
final class HeightProviderExtraction {

    private HeightProviderExtraction() {}

    /**
     * Identity-comparable sentinel returned when a feature has no extractable range —
     * either no {@link HeightRangePlacement} at all, or a provider variant the AT does not
     * expose. Callers may use {@code range == FALLBACK_RANGE} for a cheap presence check.
     */
    static final VerticalRange FALLBACK_RANGE =
            new VerticalRange(VanillaRuleSnapshot.OVERWORLD_BOTTOM, VanillaRuleSnapshot.OVERWORLD_TOP,
                    HeightDistribution.UNIFORM);

    /**
     * Extract the {@link VerticalRange} from a PlacedFeature against the given build-height
     * bounds. Returns {@code null} when the feature has no {@link HeightRangePlacement}.
     */
    static VerticalRange extractVerticalRange(PlacedFeature pf, int worldBottom, int worldTop) {
        HeightProvider hp = extractHeightProvider(pf);
        return hp == null ? null : convertHeightProvider(hp, worldBottom, worldTop);
    }

    /** Pull the {@link HeightProvider} out of a PlacedFeature's HeightRangePlacement, or null. */
    static HeightProvider extractHeightProvider(PlacedFeature pf) {
        for (PlacementModifier mod : pf.placement()) {
            if (mod instanceof HeightRangePlacement hrp) {
                return hrp.height;
            }
        }
        return null;
    }

    /**
     * Resolve a {@link HeightProvider} against {@code (worldBottom, worldTop)} (both
     * inclusive) into a {@link VerticalRange} the query API can expose. Unknown variants
     * fall back to {@link #FALLBACK_RANGE}. Resolved ranges where {@code min > max} (a
     * vanilla feature uses an anchor offset that exceeds this dimension's height — e.g.
     * {@code BelowTop(135)} in the nether's 128-tall column) also collapse to
     * {@link #FALLBACK_RANGE} — the feature isn't representable in this dim, but we
     * mustn't crash the scan over one bad entry.
     */
    static VerticalRange convertHeightProvider(HeightProvider hp, int worldBottom, int worldTop) {
        if (hp instanceof UniformHeight uh) {
            return safeRange(
                    anchorToY(uh.minInclusive, worldBottom, worldTop),
                    anchorToY(uh.maxInclusive, worldBottom, worldTop),
                    HeightDistribution.UNIFORM);
        }
        if (hp instanceof TrapezoidHeight th) {
            return safeRange(
                    anchorToY(th.minInclusive, worldBottom, worldTop),
                    anchorToY(th.maxInclusive, worldBottom, worldTop),
                    HeightDistribution.TRAPEZOID);
        }
        if (hp instanceof ConstantHeight ch) {
            // ConstantHeight is a single Y — represent as a degenerate range [y, y].
            int y = anchorToY(ch.value, worldBottom, worldTop);
            return new VerticalRange(y, y, HeightDistribution.UNIFORM);
        }
        if (hp instanceof BiasedToBottomHeight bbh) {
            return safeRange(
                    anchorToY(bbh.minInclusive, worldBottom, worldTop),
                    anchorToY(bbh.maxInclusive, worldBottom, worldTop),
                    HeightDistribution.BIASED_LOW);
        }
        if (hp instanceof VeryBiasedToBottomHeight vbbh) {
            return safeRange(
                    anchorToY(vbbh.minInclusive, worldBottom, worldTop),
                    anchorToY(vbbh.maxInclusive, worldBottom, worldTop),
                    HeightDistribution.BIASED_LOW);
        }
        if (hp instanceof WeightedListHeight wlh) {
            return weightedListEnvelope(wlh, worldBottom, worldTop);
        }
        return FALLBACK_RANGE;
    }

    /**
     * Construct a {@link VerticalRange} only when {@code min <= max}. When the anchor pair
     * resolves to an inverted range (the feature's max-anchor lands below its min-anchor in
     * this dimension's height column), return {@link #FALLBACK_RANGE} instead of throwing.
     */
    private static VerticalRange safeRange(int min, int max, HeightDistribution dist) {
        if (min > max) return FALLBACK_RANGE;
        return new VerticalRange(min, max, dist);
    }

    /**
     * Compute the union (min-of-mins, max-of-maxes) of every nested HeightProvider in a
     * {@link WeightedListHeight}'s distribution. Returns {@link #FALLBACK_RANGE} when the
     * distribution is empty or every nested provider falls back.
     *
     * <p>Per-piece distribution kinds may differ; the envelope reports
     * {@link HeightDistribution#UNIFORM} as a neutral default.
     */
    private static VerticalRange weightedListEnvelope(WeightedListHeight wlh, int worldBottom, int worldTop) {
        var entries = wlh.distribution.unwrap();
        if (entries.isEmpty()) return FALLBACK_RANGE;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (var entry : entries) {
            VerticalRange r = convertHeightProvider(entry.data(), worldBottom, worldTop);
            if (r == FALLBACK_RANGE) continue;
            if (r.minY() < min) min = r.minY();
            if (r.maxY() > max) max = r.maxY();
        }
        if (min == Integer.MAX_VALUE) return FALLBACK_RANGE;
        return new VerticalRange(min, max, HeightDistribution.UNIFORM);
    }

    /**
     * Resolve a {@link VerticalAnchor} to a concrete Y, treating {@code worldTop} as
     * inclusive (mirroring {@link VanillaRuleSnapshot#worldTop()}).
     */
    static int anchorToY(VerticalAnchor anchor, int worldBottom, int worldTop) {
        if (anchor instanceof VerticalAnchor.Absolute a) return a.y();
        if (anchor instanceof VerticalAnchor.AboveBottom ab) return worldBottom + ab.offset();
        if (anchor instanceof VerticalAnchor.BelowTop bt) return worldTop - bt.offset();
        return 0;
    }
}
