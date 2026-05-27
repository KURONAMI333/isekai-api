package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Constructs a remapped {@link PlacedFeature} by swapping its {@link HeightRangePlacement}
 * for one matching a new {@link VerticalRange} while preserving every other placement
 * modifier (count, biome filters, fluid filters, etc.) and the underlying configured
 * feature reference.
 *
 * <p>Used by the v0.7+ biome modifier ADD phase: the modifier removes a feature in REMOVE
 * phase and re-adds the rebuilt variant in ADD phase, giving the consumer's
 * {@link com.kuronami.isekaiapi.api.remap.RemapStrategy} effect over actual chunk
 * generation. The rebuilt feature is wrapped in {@code Holder.direct(...)} since it
 * doesn't live in the registry.
 *
 * <p>Distribution preservation: a {@link HeightDistribution#TRAPEZOID} original keeps the
 * trapezoid shape; everything else becomes a uniform {@link HeightRangePlacement}. The
 * BIASED_LOW variants don't have a corresponding standalone HeightProvider constructor in
 * vanilla, so they round-trip as uniform (their density bias is lost). This is acceptable
 * for v0.7 — the playable range is correct, only the distribution within it is uniform.
 */
public final class PlacedFeatureRebuilder {

    private PlacedFeatureRebuilder() {}

    /**
     * Build a new {@link PlacedFeature} whose Y-range placement matches {@code newRange}.
     * Returns {@code null} if {@code original} has no {@link HeightRangePlacement} to
     * replace — callers should fall back to the original holder in that case.
     */
    public static PlacedFeature withNewRange(PlacedFeature original, VerticalRange newRange) {
        List<PlacementModifier> oldMods = original.placement();
        boolean swapped = false;
        List<PlacementModifier> newMods = new ArrayList<>(oldMods.size());
        for (PlacementModifier mod : oldMods) {
            if (!swapped && mod instanceof HeightRangePlacement) {
                newMods.add(buildHRP(newRange));
                swapped = true;
            } else {
                newMods.add(mod);
            }
        }
        if (!swapped) {
            // The feature uses some other vertical placement (CountPlacement-only, surface
            // relative, etc.); we can't meaningfully remap its Y range. Caller should keep
            // the original instead.
            return null;
        }
        return new PlacedFeature(original.feature(), List.copyOf(newMods));
    }

    private static HeightRangePlacement buildHRP(VerticalRange range) {
        VerticalAnchor min = VerticalAnchor.absolute(range.minY());
        VerticalAnchor max = VerticalAnchor.absolute(range.maxY());
        if (range.distribution() == HeightDistribution.TRAPEZOID) {
            return HeightRangePlacement.of(TrapezoidHeight.of(min, max));
        }
        return HeightRangePlacement.of(UniformHeight.of(min, max));
    }
}
