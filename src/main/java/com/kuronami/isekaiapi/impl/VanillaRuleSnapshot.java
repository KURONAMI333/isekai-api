package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.query.MobSpawnInfo;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.query.StructurePlacementInfo;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * Immutable cached view of vanilla + modded worldgen rules taken at
 * {@code ServerAboutToStartEvent}. Backs all
 * {@link com.kuronami.isekaiapi.api.query.IsekaiQuery} methods in O(1).
 *
 * <p>Population goes through {@link VanillaRuleScanner#scan(MinecraftServer)};
 * height-provider resolution lives in {@link HeightProviderExtraction}. This class is
 * purely a data holder with typed accessors — no scanning, no extraction.
 *
 * <p>Sentinel values:
 * <ul>
 *   <li>{@link #EMPTY} — placeholder used before the first scan completes.</li>
 *   <li>{@link HeightProviderExtraction#FALLBACK_RANGE} — VerticalRange returned for
 *       features whose HeightProvider variant the AT does not expose; identity-compared
 *       via {@link #isFallback}.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class VanillaRuleSnapshot {

    /**
     * Vanilla 1.21.1 overworld build-height bounds — inclusive on both ends, since
     * Mojang's {@code getMaxBuildHeight()} is exclusive and the scanner subtracts 1
     * before storing. Used as the fallback when the overworld level is not yet loaded
     * at scan time, and as the source range for {@link HeightProviderExtraction#FALLBACK_RANGE}.
     */
    static final int OVERWORLD_BOTTOM = -64;
    static final int OVERWORLD_TOP = 319;

    public static final VanillaRuleSnapshot EMPTY = new VanillaRuleSnapshot(
            List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
            OVERWORLD_BOTTOM, OVERWORLD_TOP);

    private final List<PlacedFeatureInfo> placedFeatures;
    private final List<StructurePlacementInfo> structures;
    private final Map<MobCategory, List<MobSpawnInfo>> mobsByCategory;
    private final Map<ResourceKey<Biome>, List<MobSpawnInfo>> mobsByBiome;
    /**
     * Reverse index: decoration step(s) each PlacedFeature appears in across all biomes.
     * A feature can sit in multiple steps (rare but possible). Used by the strategy remap
     * to inject the rebuilt variant into the same step the original lived in.
     */
    private final Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> stepsByFeature;
    /**
     * Reverse tag index built at scan time. A PlacedFeature appearing in multiple tags is
     * added once per tag.
     */
    private final Map<TagKey<PlacedFeature>, List<PlacedFeatureInfo>> placedFeaturesByTag;
    /** Reverse tag index for Structure registry, mirroring {@link #placedFeaturesByTag}. */
    private final Map<TagKey<Structure>, List<StructurePlacementInfo>> structuresByTag;
    /**
     * Per-biome membership: which PlacedFeatures originally existed in each biome's
     * pre-modifier generation settings. Used by the ADD phase to scope ore re-injection
     * to features that were actually in this biome — without this, every matched biome
     * would receive every PlacedFeature in the game.
     */
    private final Map<ResourceKey<Biome>, Set<ResourceKey<PlacedFeature>>> featuresByBiome;
    /**
     * Per-dimension VerticalRange overrides for features that use relative VerticalAnchors
     * (AboveBottom / BelowTop). Outer key = dimension, inner key = feature. Features with
     * pure absolute anchors are absent from the inner maps (their {@link #placedFeatures} entry is
     * already correct everywhere).
     */
    private final Map<ResourceKey<Level>, Map<ResourceKey<PlacedFeature>, VerticalRange>> perDimRanges;
    private final int worldBottom;
    /** Inclusive top Y — the scanner converts {@code getMaxBuildHeight()} (exclusive) by subtracting 1. */
    private final int worldTop;

    VanillaRuleSnapshot(List<PlacedFeatureInfo> placedFeatures,
                        List<StructurePlacementInfo> structures,
                        Map<MobCategory, List<MobSpawnInfo>> mobsByCategory,
                        Map<ResourceKey<Biome>, List<MobSpawnInfo>> mobsByBiome,
                        Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> stepsByFeature,
                        Map<TagKey<PlacedFeature>, List<PlacedFeatureInfo>> placedFeaturesByTag,
                        Map<TagKey<Structure>, List<StructurePlacementInfo>> structuresByTag,
                        Map<ResourceKey<Biome>, Set<ResourceKey<PlacedFeature>>> featuresByBiome,
                        Map<ResourceKey<Level>, Map<ResourceKey<PlacedFeature>, VerticalRange>> perDimRanges,
                        int worldBottom,
                        int worldTop) {
        this.placedFeatures = List.copyOf(placedFeatures);
        this.structures = List.copyOf(structures);
        this.mobsByCategory = Map.copyOf(mobsByCategory);
        this.mobsByBiome = copyOfListMap(mobsByBiome);
        this.stepsByFeature = copyOfSetMap(stepsByFeature);
        this.placedFeaturesByTag = copyOfListMap(placedFeaturesByTag);
        this.structuresByTag = copyOfListMap(structuresByTag);
        this.featuresByBiome = copyOfSetMap(featuresByBiome);
        this.perDimRanges = copyOfNestedMap(perDimRanges);
        this.worldBottom = worldBottom;
        this.worldTop = worldTop;
    }

    /** Trigger a fresh registry scan. Delegates to {@link VanillaRuleScanner#scan}. */
    public static VanillaRuleSnapshot scan(MinecraftServer server) {
        return VanillaRuleScanner.scan(server);
    }

    // -- Bulk accessors ------------------------------------------------------------

    public List<PlacedFeatureInfo> placedFeatures() { return placedFeatures; }
    public List<StructurePlacementInfo> structures() { return structures; }

    /** Overworld build-height bottom captured at scan time. */
    public int worldBottom() { return worldBottom; }

    /** Inclusive overworld build-height top captured at scan time. */
    public int worldTop() { return worldTop; }

    public boolean isEmpty() {
        return placedFeatures.isEmpty() && structures.isEmpty() && mobsByCategory.isEmpty();
    }

    /** {@code true} when {@code info}'s range is the {@link HeightProviderExtraction#FALLBACK_RANGE} sentinel. */
    public boolean isFallback(PlacedFeatureInfo info) {
        return info.range() == HeightProviderExtraction.FALLBACK_RANGE;
    }

    // -- Keyed accessors -----------------------------------------------------------

    /**
     * Decoration step(s) the given PlacedFeature was originally indexed under across the
     * scanned biomes. Empty set means the feature wasn't referenced by any biome at scan
     * time — e.g. a feature defined in JSON but unused, or one that was only added by a
     * later biome modifier.
     */
    public Set<GenerationStep.Decoration> stepsFor(ResourceKey<PlacedFeature> key) {
        return stepsByFeature.getOrDefault(key, Set.of());
    }

    /**
     * PlacedFeatures that originally lived in the given biome at scan time. Used by the
     * ADD phase to scope ore re-injection to the biome's pre-modifier feature set.
     * Returns the empty set for biomes that weren't scanned.
     */
    public Set<ResourceKey<PlacedFeature>> featuresInBiome(ResourceKey<Biome> biome) {
        return featuresByBiome.getOrDefault(biome, Set.of());
    }

    public List<MobSpawnInfo> mobsForCategory(MobCategory category) {
        return mobsByCategory.getOrDefault(category, List.of());
    }

    /** Spawn entries originally defined in the given biome's MobSpawnSettings. */
    public List<MobSpawnInfo> mobsForBiome(ResourceKey<Biome> biome) {
        return mobsByBiome.getOrDefault(biome, List.of());
    }

    /** PlacedFeatures that were tagged with the given TagKey at scan time. */
    public List<PlacedFeatureInfo> placedFeaturesForTag(TagKey<PlacedFeature> tag) {
        return placedFeaturesByTag.getOrDefault(tag, List.of());
    }

    /** Structure placements that were tagged with the given TagKey at scan time. */
    public List<StructurePlacementInfo> structuresForTag(TagKey<Structure> tag) {
        return structuresByTag.getOrDefault(tag, List.of());
    }

    /**
     * VerticalRange for the given feature resolved against the given dimension's build
     * height. Returns {@code Optional.empty()} when the dimension wasn't loaded at scan
     * time, when the feature wasn't scanned, or when the dimension shares overworld build
     * bounds (in which case callers should fall back to the global range).
     */
    public Optional<VerticalRange> placedFeatureRangeInDimension(ResourceKey<PlacedFeature> feature,
                                                                  ResourceKey<Level> dimension) {
        Map<ResourceKey<PlacedFeature>, VerticalRange> dimMap = perDimRanges.get(dimension);
        if (dimMap == null) return Optional.empty();
        return Optional.ofNullable(dimMap.get(feature));
    }

    // -- Defensive-copy helpers ----------------------------------------------------

    private static <K, V> Map<K, List<V>> copyOfListMap(Map<K, List<V>> src) {
        Map<K, List<V>> copy = new HashMap<>(src.size());
        src.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return Map.copyOf(copy);
    }

    private static <K, V> Map<K, Set<V>> copyOfSetMap(Map<K, Set<V>> src) {
        Map<K, Set<V>> copy = new HashMap<>(src.size());
        src.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
        return Map.copyOf(copy);
    }

    private static <K1, K2, V> Map<K1, Map<K2, V>> copyOfNestedMap(Map<K1, Map<K2, V>> src) {
        Map<K1, Map<K2, V>> copy = new HashMap<>(src.size());
        src.forEach((k, v) -> copy.put(k, Map.copyOf(v)));
        return Map.copyOf(copy);
    }
}
