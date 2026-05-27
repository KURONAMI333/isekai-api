package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.MobSpawnInfo;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.query.StructurePlacementInfo;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable cached view of vanilla + modded worldgen rules taken at
 * {@code ServerAboutToStartEvent}. Backs all
 * {@link com.kuronami.isekaiapi.api.query.IsekaiQuery} methods in O(1).
 *
 * <p>v0.4: walks {@code PLACED_FEATURE} registry and extracts actual
 * {@link VerticalRange} from each feature's {@link HeightRangePlacement} via the
 * Access Transformer in {@code src/main/resources/META-INF/accesstransformer.cfg}
 * (which exposes the otherwise-private {@code height}, {@code minInclusive},
 * {@code maxInclusive} fields). Features without a {@link HeightRangePlacement}
 * modifier still get a {@link PlacedFeatureInfo} entry with a fallback range so
 * the full key list remains queryable.
 *
 * <p>{@link VerticalAnchor} resolution uses overworld defaults (-64..320) since
 * {@code WorldGenerationContext} isn't available at scan time. Features anchored
 * via {@link VerticalAnchor.AboveBottom} / {@link VerticalAnchor.BelowTop} in
 * non-overworld dimensions report overworld-relative values; per-dimension scan
 * lands in v0.5.
 *
 * <p>Structures and mob-spawn walks are still pending (v0.5).
 */
public final class VanillaRuleSnapshot {

    public static final VanillaRuleSnapshot EMPTY =
            new VanillaRuleSnapshot(List.of(), List.of(), Map.of());

    private static final int APPROX_WORLD_BOTTOM = -64;
    private static final int APPROX_WORLD_TOP = 320;
    private static final VerticalRange FALLBACK_RANGE =
            new VerticalRange(APPROX_WORLD_BOTTOM, APPROX_WORLD_TOP, HeightDistribution.UNIFORM);

    private final List<PlacedFeatureInfo> ores;
    private final List<StructurePlacementInfo> structures;
    private final Map<MobCategory, List<MobSpawnInfo>> mobsByCategory;

    public VanillaRuleSnapshot(List<PlacedFeatureInfo> ores,
                                List<StructurePlacementInfo> structures,
                                Map<MobCategory, List<MobSpawnInfo>> mobsByCategory) {
        this.ores = List.copyOf(ores);
        this.structures = List.copyOf(structures);
        this.mobsByCategory = Map.copyOf(mobsByCategory);
    }

    public static VanillaRuleSnapshot scan(MinecraftServer server) {
        IsekaiApi.LOGGER.info("[Isekai v0.4] VanillaRuleSnapshot.scan: walking PLACED_FEATURE registry");

        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);

        List<PlacedFeatureInfo> features = new ArrayList<>();
        lookup.listElements().forEach(ref -> {
            ResourceKey<PlacedFeature> key = ref.unwrapKey().orElseThrow();
            PlacedFeature pf = ref.value();
            VerticalRange range = extractVerticalRange(pf);
            features.add(new PlacedFeatureInfo(key, range != null ? range : FALLBACK_RANGE, 1, Set.of()));
        });
        long withRange = features.stream().filter(info -> info.range() != FALLBACK_RANGE).count();

        IsekaiApi.LOGGER.info(
                "[Isekai v0.4] Scanned {} placed features ({} with extracted VerticalRange, "
                        + "{} with fallback); structures + mob-spawns deferred to v0.5",
                features.size(), withRange, features.size() - withRange);

        return new VanillaRuleSnapshot(features, List.of(), Map.of());
    }

    private static VerticalRange extractVerticalRange(PlacedFeature pf) {
        for (PlacementModifier mod : pf.placement()) {
            if (mod instanceof HeightRangePlacement hrp) {
                return convertHeightProvider(hrp.height);
            }
        }
        return null;
    }

    private static VerticalRange convertHeightProvider(HeightProvider hp) {
        if (hp instanceof UniformHeight uh) {
            return new VerticalRange(
                    anchorToY(uh.minInclusive),
                    anchorToY(uh.maxInclusive),
                    HeightDistribution.UNIFORM);
        }
        if (hp instanceof TrapezoidHeight th) {
            return new VerticalRange(
                    anchorToY(th.minInclusive),
                    anchorToY(th.maxInclusive),
                    HeightDistribution.TRAPEZOID);
        }
        // ConstantHeight / BiasedToBottomHeight / VeryBiasedToBottomHeight / WeightedListHeight
        // are not yet supported; their fields aren't in the AT. v0.5 will extend the AT.
        return FALLBACK_RANGE;
    }

    private static int anchorToY(VerticalAnchor anchor) {
        if (anchor instanceof VerticalAnchor.Absolute a) return a.y();
        if (anchor instanceof VerticalAnchor.AboveBottom ab) return APPROX_WORLD_BOTTOM + ab.offset();
        if (anchor instanceof VerticalAnchor.BelowTop bt) return APPROX_WORLD_TOP - bt.offset();
        return 0;
    }

    public List<PlacedFeatureInfo> ores() { return ores; }
    public List<StructurePlacementInfo> structures() { return structures; }

    public List<MobSpawnInfo> mobsForCategory(MobCategory category) {
        return mobsByCategory.getOrDefault(category, List.of());
    }

    public boolean isEmpty() {
        return ores.isEmpty() && structures.isEmpty() && mobsByCategory.isEmpty();
    }
}
