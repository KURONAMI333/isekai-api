package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural lock for {@link RemapTargets} — the one set that the REMOVE phase deletes and
 * the ADD phase re-injects.
 *
 * <p>The property that matters to a save is {@link #excludedFeatureIsNeverARemapTarget}: a
 * key listed in {@code exclusions.features} must not come back. The ADD phase re-injects
 * through {@code Holder.direct}, which carries no {@link ResourceKey}, so a feature that
 * slips back in here is unremovable by any later pass — it generates forever. Measured
 * damage before this filter existed, in a Sky World save over 9,409 chunks:
 * {@code spring_water} and {@code spring_lava} were both excluded and both generated
 * (437 water sources, 87,988 flowing water, 18,247 lava sources).
 *
 * <p>{@link #unrebuildableFeaturesAreNeverTargets} locks the other direction of the pair
 * invariant: a feature whose range is the fallback sentinel is one
 * {@link PlacedFeatureRebuilder} cannot rebuild, so REMOVE must not delete it either.
 */
class RemapTargetsTest {

    private static final ResourceKey<PlacedFeature> SPRING_WATER = pf("spring_water");
    private static final ResourceKey<PlacedFeature> SPRING_LAVA = pf("spring_lava");
    private static final ResourceKey<PlacedFeature> ORE_IRON = pf("ore_iron_upper");
    private static final ResourceKey<PlacedFeature> ORE_DIAMOND = pf("ore_diamond");

    private static final ResourceKey<Biome> BIOME = Biomes.PLAINS;
    private static final ResourceKey<Biome> OTHER_BIOME = Biomes.DESERT;

    private static ResourceKey<PlacedFeature> pf(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath("minecraft", path));
    }

    private static PlacedFeatureInfo tracked(ResourceKey<PlacedFeature> key) {
        return new PlacedFeatureInfo(key, new VerticalRange(-64, 64, HeightDistribution.UNIFORM), 1, Set.of());
    }

    /** A feature the rebuilder cannot rebuild — no usable HeightRangePlacement. */
    private static PlacedFeatureInfo unrebuildable(ResourceKey<PlacedFeature> key) {
        return new PlacedFeatureInfo(key, HeightProviderExtraction.FALLBACK_RANGE, 1, Set.of());
    }

    /**
     * Snapshot carrying only what {@link RemapTargets} reads: the scanned feature list and
     * the per-biome membership index.
     */
    private static VanillaRuleSnapshot snapshot(List<PlacedFeatureInfo> features,
                                                Map<ResourceKey<Biome>, Set<ResourceKey<PlacedFeature>>> byBiome) {
        return new VanillaRuleSnapshot(features, List.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), byBiome, Map.of(), -64, 319);
    }

    private static VanillaRuleSnapshot plainsWith(PlacedFeatureInfo... features) {
        Set<ResourceKey<PlacedFeature>> keys = Set.copyOf(
                java.util.Arrays.stream(features).map(PlacedFeatureInfo::key).toList());
        return snapshot(List.of(features), Map.of(BIOME, keys));
    }

    @Test
    void excludedFeatureIsNeverARemapTarget() {
        VanillaRuleSnapshot snapshot = plainsWith(
                tracked(SPRING_WATER), tracked(SPRING_LAVA), tracked(ORE_IRON));

        var targets = RemapTargets.select(snapshot, BIOME, Set.of(SPRING_WATER, SPRING_LAVA));

        assertFalse(targets.contains(SPRING_WATER),
                "an excluded feature must not be re-injected — the ADD phase adds it as an "
                        + "anonymous Holder.direct that no later pass can remove");
        assertFalse(targets.contains(SPRING_LAVA), "same for the second excluded key");
        assertEquals(Set.of(ORE_IRON), targets, "everything else still gets remapped");
    }

    @Test
    void withoutExclusionsEveryTrackedFeatureIsATarget() {
        VanillaRuleSnapshot snapshot = plainsWith(
                tracked(SPRING_WATER), tracked(SPRING_LAVA), tracked(ORE_IRON));

        assertEquals(Set.of(SPRING_WATER, SPRING_LAVA, ORE_IRON),
                RemapTargets.select(snapshot, BIOME, Set.of()));
    }

    @Test
    void excludingEverythingLeavesNothingToRemap() {
        VanillaRuleSnapshot snapshot = plainsWith(tracked(SPRING_WATER), tracked(ORE_IRON));

        assertTrue(RemapTargets.select(snapshot, BIOME, Set.of(SPRING_WATER, ORE_IRON)).isEmpty());
    }

    @Test
    void unrebuildableFeaturesAreNeverTargets() {
        // The other half of the pair invariant: PlacedFeatureRebuilder returns null for these,
        // so if REMOVE deleted them ADD could not put them back and they would vanish silently.
        VanillaRuleSnapshot snapshot = plainsWith(unrebuildable(SPRING_WATER), tracked(ORE_IRON));

        assertEquals(Set.of(ORE_IRON), RemapTargets.select(snapshot, BIOME, Set.of()));
    }

    @Test
    void featuresFromAnotherBiomeAreNotTargets() {
        VanillaRuleSnapshot snapshot = snapshot(
                List.of(tracked(ORE_IRON), tracked(ORE_DIAMOND)),
                Map.of(BIOME, Set.of(ORE_IRON), OTHER_BIOME, Set.of(ORE_DIAMOND)));

        assertEquals(Set.of(ORE_IRON), RemapTargets.select(snapshot, BIOME, Set.of()));
        assertEquals(Set.of(ORE_DIAMOND), RemapTargets.select(snapshot, OTHER_BIOME, Set.of()));
    }

    @Test
    void missingSnapshotOrBiomeKeySelectsNothing() {
        VanillaRuleSnapshot snapshot = plainsWith(tracked(ORE_IRON));

        assertTrue(RemapTargets.select(null, BIOME, Set.of()).isEmpty());
        assertTrue(RemapTargets.select(snapshot, null, Set.of()).isEmpty());
        assertTrue(RemapTargets.select(snapshot, OTHER_BIOME, Set.of()).isEmpty(),
                "an unscanned biome has no originals, so there is nothing to take over");
    }
}
