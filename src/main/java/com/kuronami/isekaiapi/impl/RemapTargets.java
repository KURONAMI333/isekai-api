package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * The single definition of "which placed features the strategy remap takes over in this
 * biome". Both halves of the remap read it: the ADD phase re-injects exactly this set with the
 * strategy applied to its vertical range and records it, and {@code DimensionFeatureScope}
 * drops exactly that recorded set at decoration time in this dimension. One function, so the
 * two halves cannot drift — and the drop is a recorded result rather than a second
 * computation, so a reload cannot desynchronize them.
 *
 * <p>A key is a target when all four hold:
 * <ul>
 *   <li>the biome originally contained it ({@link VanillaRuleSnapshot#featuresInBiome}),</li>
 *   <li>the snapshot tracks it (it is in {@link VanillaRuleSnapshot#placedFeatures()}),</li>
 *   <li>its range is not the fallback sentinel — a fallback means no usable
 *       {@code HeightRangePlacement}, which is exactly the condition under which
 *       {@link PlacedFeatureRebuilder} returns {@code null} and the ADD phase could not put
 *       it back,</li>
 *   <li>the descriptor's {@code exclusions.features} does not list it.</li>
 * </ul>
 *
 * <p>The exclusion term is what keeps {@code exclusions.features} meaning "this does not
 * generate". The re-injected feature is an anonymous {@link net.minecraft.core.Holder#direct}
 * holder with no {@link ResourceKey}, so nothing downstream — neither a later
 * {@code exclusions} pass nor {@code feature_predicates} — can find it again. Filtering has
 * to happen here or not at all.
 */
@ApiStatus.Internal
public final class RemapTargets {

    private RemapTargets() {}

    /**
     * Keys the remap takes over in {@code biomeKey}. Returns an empty set when the snapshot
     * is absent (no server context yet), when the biome has no key (a direct
     * {@code Holder<Biome>}), or when the biome was not scanned.
     *
     * @param snapshot the scanned worldgen snapshot, or {@code null} before the first scan
     * @param biomeKey the biome being modified, or {@code null} for an unkeyed holder
     * @param excluded the descriptor's {@code exclusions.features} set
     */
    public static Set<ResourceKey<PlacedFeature>> select(VanillaRuleSnapshot snapshot,
                                                          ResourceKey<Biome> biomeKey,
                                                          Set<ResourceKey<PlacedFeature>> excluded) {
        return select(snapshot, biomeKey, null, excluded);
    }

    /**
     * Dimension-aware variant. A feature whose anchors invert in {@code dimension} (for
     * example {@code above_bottom(300)} in the nether's {@code 0..255} column) is not a
     * target there even when it resolves fine in the overworld — re-injecting it would
     * produce a degenerate band instead of terrain.
     *
     * @param dimension the worldshape's dimension, or {@code null} to use overworld bounds
     */
    public static Set<ResourceKey<PlacedFeature>> select(VanillaRuleSnapshot snapshot,
                                                          ResourceKey<Biome> biomeKey,
                                                          ResourceKey<Level> dimension,
                                                          Set<ResourceKey<PlacedFeature>> excluded) {
        if (snapshot == null || biomeKey == null) return Set.of();
        Set<ResourceKey<PlacedFeature>> inBiome = snapshot.featuresInBiome(biomeKey);
        if (inBiome.isEmpty()) return Set.of();
        Set<ResourceKey<PlacedFeature>> targets = new HashSet<>();
        for (PlacedFeatureInfo info : snapshot.placedFeatures()) {
            if (dimension == null ? snapshot.isFallback(info)
                    : snapshot.isFallbackInDimension(info, dimension)) continue;
            if (!inBiome.contains(info.key())) continue;
            if (excluded.contains(info.key())) continue;
            targets.add(info.key());
        }
        return Set.copyOf(targets);
    }
}
