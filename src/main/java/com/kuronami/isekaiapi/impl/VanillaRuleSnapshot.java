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
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable cached view of vanilla + modded worldgen rules taken at
 * {@code ServerAboutToStartEvent}. Backs all
 * {@link com.kuronami.isekaiapi.api.query.IsekaiQuery} methods in O(1).
 *
 * <p>v0.3: walks the PLACED_FEATURE registry and enumerates every key. Each entry
 * gets a {@link VerticalRange} placeholder of (-64..320). The actual
 * VerticalRange extraction is blocked until v0.4 because the necessary fields are
 * Mojang-private with no public accessor:
 * <ul>
 *   <li>{@code HeightRangePlacement.height} (HeightProvider) — private final, no getter</li>
 *   <li>{@code UniformHeight.minInclusive} / {@code maxInclusive} — private final, no getter</li>
 *   <li>{@code TrapezoidHeight.minInclusive} / {@code maxInclusive} — private final, no getter</li>
 * </ul>
 *
 * <p>v0.4 will ship a NeoForge Access Transformer that exposes these fields, then this
 * scanner will inspect each {@code PlacementModifier} for {@code HeightRangePlacement},
 * decode its {@code HeightProvider}, and resolve {@code VerticalAnchor.Absolute.y()} /
 * {@code AboveBottom.offset()} / {@code BelowTop.offset()} into an actual range.
 *
 * <p>API references verified by reading the extracted Mojang source under
 * {@code .gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge*.jar}
 * — the third verification pass after two reference-search attempts that failed due to
 * mapping mismatches.
 */
public final class VanillaRuleSnapshot {

    public static final VanillaRuleSnapshot EMPTY =
            new VanillaRuleSnapshot(List.of(), List.of(), Map.of());

    private static final VerticalRange FALLBACK_RANGE =
            new VerticalRange(-64, 320, HeightDistribution.UNIFORM);

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

    /**
     * Walks {@code PLACED_FEATURE} and produces a {@link PlacedFeatureInfo} entry per
     * key with the fallback range. Consumers see the full key list (e.g. which ores /
     * features exist in this server's loaded data) but not their actual Y ranges
     * until v0.4 lands the Access Transformer.
     */
    public static VanillaRuleSnapshot scan(MinecraftServer server) {
        IsekaiApi.LOGGER.info("[Isekai v0.3] VanillaRuleSnapshot.scan: walking PLACED_FEATURE registry");

        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);

        List<PlacedFeatureInfo> features = new ArrayList<>();
        lookup.listElements().forEach(ref -> {
            ResourceKey<PlacedFeature> key = ref.unwrapKey().orElseThrow();
            features.add(new PlacedFeatureInfo(key, FALLBACK_RANGE, 1, Set.of()));
        });

        IsekaiApi.LOGGER.info(
                "[Isekai v0.3] Scanned {} placed feature keys "
                        + "(actual VerticalRange extraction deferred to v0.4 pending Access Transformer)",
                features.size());

        return new VanillaRuleSnapshot(features, List.of(), Map.of());
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
