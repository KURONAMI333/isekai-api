package com.kuronami.isekaiapi.biomemodifier;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural lock for {@link ForeignFeatureReconciler#foreignFeatureKeys}: the reconcile must
 * pick up exactly the keyed features another mod added on top of the vanilla baseline, and
 * nothing else.
 *
 * <p>The dangerous direction is a false positive — a vanilla feature (in the snapshot) or one
 * of our own guarded copies ({@code Holder.direct}, no registry key) being treated as foreign,
 * because the reconcile removes whatever it classifies. Hence the three cases below, including
 * a key-less copy in the input list.
 */
class ForeignFeatureReconcilerTest {

    private static final HolderOwner<PlacedFeature> OWNER = new HolderOwner<>() {};

    private static final ResourceKey<PlacedFeature> VANILLA = key("vanilla_ore");
    private static final ResourceKey<PlacedFeature> FOREIGN = key("other_mod_feature");

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    @Test
    void foreignFeatureKeysAreOnlyTheNonVanillaOnes() {
        Holder<PlacedFeature> vanilla = keyed(VANILLA);
        Holder<PlacedFeature> foreign = keyed(FOREIGN);
        Holder<PlacedFeature> injectedCopy = Holder.direct(new PlacedFeature(
                Holder.direct(new ConfiguredFeature<>(Feature.NO_OP, NoneFeatureConfiguration.INSTANCE)),
                List.of()));

        Set<ResourceKey<PlacedFeature>> result = ForeignFeatureReconciler.foreignFeatureKeys(
                Set.of(VANILLA), List.of(vanilla, foreign, injectedCopy));

        assertFalse(result.contains(VANILLA), "a vanilla feature is part of the baseline");
        assertTrue(result.contains(FOREIGN), "a keyed feature outside the baseline is foreign");
        assertEquals(Set.of(FOREIGN), result, "a key-less injected copy is not foreign");
    }

    private static Holder<PlacedFeature> keyed(ResourceKey<PlacedFeature> key) {
        return Holder.Reference.createStandAlone(OWNER, key);
    }

    private static ResourceKey<PlacedFeature> key(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath("isekai_api_test", path));
    }
}
