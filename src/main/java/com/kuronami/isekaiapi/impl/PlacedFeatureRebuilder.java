package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.VerticalRange;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;

/**
 * Constructs a remapped {@link PlacedFeature} by swapping its {@link HeightRangePlacement}
 * for one matching a new {@link VerticalRange} while preserving every other placement
 * modifier (count, biome filters, fluid filters, etc.) and the underlying configured
 * feature reference.
 *
 * <p>Distribution preservation: the rebuilder inspects the original {@link HeightProvider}
 * type and constructs the same type with the new anchors. {@link BiasedToBottomHeight} and
 * {@link VeryBiasedToBottomHeight} additionally preserve their {@code inner} (bias
 * intensity) field via Access Transformer. Distributions that map cleanly:
 *
 * <ul>
 *   <li>UniformHeight -> UniformHeight</li>
 *   <li>TrapezoidHeight -> TrapezoidHeight</li>
 *   <li>BiasedToBottomHeight -> BiasedToBottomHeight (inner preserved)</li>
 *   <li>VeryBiasedToBottomHeight -> VeryBiasedToBottomHeight (inner preserved)</li>
 *   <li>Other / unknown -> UniformHeight (safe fallback)</li>
 * </ul>
 */
@ApiStatus.Internal
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
            if (!swapped && mod instanceof HeightRangePlacement hrp) {
                newMods.add(rebuildHRP(hrp.height, newRange));
                swapped = true;
            } else {
                newMods.add(mod);
            }
        }
        if (!swapped) return null;
        return new PlacedFeature(original.feature(), List.copyOf(newMods));
    }

    /**
     * Rebuild a HeightRangePlacement preserving the original {@link HeightProvider}'s type
     * (and {@code inner} bias intensity for the BIASED variants). Falls back to uniform when
     * the original is a type we don't know how to clone, or when the rebuilt range is too
     * narrow for a BIASED provider to sample.
     *
     * <p>Critical for remap: a deep ore remapped into a thin worldshape band (e.g. a 26-block
     * island band) can end up with {@code span < inner}. {@code BiasedToBottomHeight.sample}
     * does NOT validate at construction — it logs {@code "Empty height range"} and returns the
     * floor on EVERY placement attempt (per chunk) when {@code max - min - inner + 1 <= 0}.
     * We pre-check that condition and collapse to UniformHeight instead, preserving the band
     * without the per-chunk log spam.
     */
    private static HeightRangePlacement rebuildHRP(HeightProvider original, VerticalRange newRange) {
        VerticalAnchor min = VerticalAnchor.absolute(newRange.minY());
        VerticalAnchor max = VerticalAnchor.absolute(newRange.maxY());
        if (original instanceof TrapezoidHeight) {
            return HeightRangePlacement.of(TrapezoidHeight.of(min, max));
        }
        // span+1 is the inclusive block count; a BIASED provider needs span+1 > inner.
        int usable = newRange.maxY() - newRange.minY() + 1;
        if (original instanceof BiasedToBottomHeight orig && usable > orig.inner) {
            return HeightRangePlacement.of(BiasedToBottomHeight.of(min, max, orig.inner));
        }
        if (original instanceof VeryBiasedToBottomHeight orig && usable > orig.inner) {
            return HeightRangePlacement.of(VeryBiasedToBottomHeight.of(min, max, orig.inner));
        }
        // UniformHeight, ConstantHeight (degenerate range), WeightedListHeight, a BIASED
        // variant whose bias no longer fits the remapped range, or unknown: collapse to
        // uniform. UniformHeight is the most permissive distribution and stays well-defined
        // even when min == max.
        return HeightRangePlacement.of(UniformHeight.of(min, max));
    }
}
