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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.BiasedToBottomHeight;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.heightproviders.VeryBiasedToBottomHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable cached view of vanilla + modded worldgen rules taken at
 * {@code ServerAboutToStartEvent}. Backs all
 * {@link com.kuronami.isekaiapi.api.query.IsekaiQuery} methods in O(1).
 *
 * <p>Walks three registries at scan time:
 * <ul>
 *   <li>PLACED_FEATURE — extracts {@link VerticalRange} from each feature's
 *       {@link HeightRangePlacement} via the Access Transformer in
 *       {@code src/main/resources/META-INF/accesstransformer.cfg}. Supports
 *       UniformHeight, TrapezoidHeight, ConstantHeight, BiasedToBottomHeight,
 *       and VeryBiasedToBottomHeight. WeightedListHeight isn't a simple range,
 *       so it falls back. Features without HeightRangePlacement also fall back.</li>
 *   <li>STRUCTURE_SET → STRUCTURE — emits one {@link StructurePlacementInfo}
 *       per (structure, set) pairing; biome filter via {@code Either.left} tag.</li>
 *   <li>BIOME — collects every {@link MobSpawnSettings.SpawnerData} per MobCategory.</li>
 * </ul>
 *
 * <p>{@link VerticalAnchor} resolution reads the overworld's actual build height at
 * scan time (vanilla 1.21.1 = -64..320). True per-dimension snapshots land in v0.7+.
 */
public final class VanillaRuleSnapshot {

    public static final VanillaRuleSnapshot EMPTY =
            new VanillaRuleSnapshot(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), -64, 320);

    /**
     * Overworld defaults used by {@link #anchorToY} when no per-call dimension override is
     * supplied. NeoForge 1.21.1 vanilla overworld is -64..320. Per-dimension resolution
     * would require the {@link net.minecraft.world.level.levelgen.WorldGenerationContext}
     * for the consumer's target level — landing in v0.7 alongside the biome modifier ADD
     * phase, where dimension is known.
     */
    private static final int APPROX_WORLD_BOTTOM = -64;
    private static final int APPROX_WORLD_TOP = 320;

    /**
     * Returned for placed features that lack a {@link HeightRangePlacement} modifier or use
     * a {@link HeightProvider} subtype the AT doesn't expose yet. Identity-comparable
     * (via {@code ==}) for cheap "did we get a real range" checks in callers.
     */
    private static final VerticalRange FALLBACK_RANGE =
            new VerticalRange(APPROX_WORLD_BOTTOM, APPROX_WORLD_TOP, HeightDistribution.UNIFORM);

    private final List<PlacedFeatureInfo> ores;
    /**
     * Reverse tag index built at scan time. A PlacedFeature appearing in multiple tags is
     * added once per tag. Empty when a tag isn't keyed by any scanned feature.
     */
    private final Map<TagKey<PlacedFeature>, List<PlacedFeatureInfo>> oresByTag;
    private final List<StructurePlacementInfo> structures;
    private final Map<MobCategory, List<MobSpawnInfo>> mobsByCategory;
    private final Map<ResourceKey<Biome>, List<MobSpawnInfo>> mobsByBiome;
    /**
     * Reverse index: which decoration step(s) each PlacedFeature appears in across all
     * biomes. A feature can sit in multiple steps (rare but possible); the set captures
     * every step it was seen in. Used by the v0.8 strategy remap to inject the rebuilt
     * variant into the same step the original lived in.
     */
    private final Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> stepsByFeature;
    private final int worldBottom;
    private final int worldTop;

    public VanillaRuleSnapshot(List<PlacedFeatureInfo> ores,
                                List<StructurePlacementInfo> structures,
                                Map<MobCategory, List<MobSpawnInfo>> mobsByCategory,
                                Map<ResourceKey<Biome>, List<MobSpawnInfo>> mobsByBiome,
                                Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> stepsByFeature,
                                Map<TagKey<PlacedFeature>, List<PlacedFeatureInfo>> oresByTag,
                                int worldBottom,
                                int worldTop) {
        this.ores = List.copyOf(ores);
        this.structures = List.copyOf(structures);
        this.mobsByCategory = Map.copyOf(mobsByCategory);
        this.mobsByBiome = copyOfListMap(mobsByBiome);
        this.stepsByFeature = copyOfStepsMap(stepsByFeature);
        this.oresByTag = copyOfListMap(oresByTag);
        this.worldBottom = worldBottom;
        this.worldTop = worldTop;
    }

    private static <K, V> Map<K, List<V>> copyOfListMap(Map<K, List<V>> src) {
        Map<K, List<V>> copy = new HashMap<>(src.size());
        src.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return Map.copyOf(copy);
    }

    private static Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> copyOfStepsMap(
            Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> src) {
        Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> copy = new HashMap<>(src.size());
        src.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
        return Map.copyOf(copy);
    }

    public static VanillaRuleSnapshot scan(MinecraftServer server) {
        IsekaiApi.LOGGER.info("[Isekai v0.8] VanillaRuleSnapshot.scan: walking PLACED_FEATURE + STRUCTURE + BIOME registries");

        // Resolve the overworld's actual build height range — vanilla 1.21.1 ships -64..320
        // but cubic-chunks or world height mods can change this. PlacedFeatures scanned here
        // are typically authored against the overworld even when reused in modded dimensions
        // (the Y range absoluteness is set at server startup, not per-level visit).
        int overworldBottom = server.overworld().getMinBuildHeight();
        int overworldTop = server.overworld().getMaxBuildHeight();

        var placedScan = scanPlacedFeatures(server, overworldBottom, overworldTop);
        List<PlacedFeatureInfo> features = placedScan.features();
        Map<TagKey<PlacedFeature>, List<PlacedFeatureInfo>> oresByTag = placedScan.byTag();
        List<StructurePlacementInfo> structures = scanStructures(server);
        var mobScan = scanMobSpawns(server);
        Map<MobCategory, List<MobSpawnInfo>> mobs = mobScan.byCategory();
        Map<ResourceKey<Biome>, List<MobSpawnInfo>> mobsBiome = mobScan.byBiome();
        Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> stepsByFeature = scanFeatureSteps(server);

        long withRange = features.stream().filter(info -> info.range() != FALLBACK_RANGE).count();
        int mobTotal = mobs.values().stream().mapToInt(List::size).sum();
        IsekaiApi.LOGGER.info(
                "[Isekai v0.8] Scanned {} placed features ({} with extracted VerticalRange, "
                        + "{} with fallback, {} step-indexed) + {} structure placements + {} mob spawn entries across {} categories "
                        + "(overworld build-height {}..{})",
                features.size(), withRange, features.size() - withRange, stepsByFeature.size(),
                structures.size(), mobTotal, mobs.size(),
                overworldBottom, overworldTop);

        IsekaiApi.LOGGER.debug("[Isekai v0.9] PlacedFeature tag index: {} distinct tags", oresByTag.size());

        return new VanillaRuleSnapshot(features, structures, mobs, mobsBiome, stepsByFeature, oresByTag,
                overworldBottom, overworldTop);
    }

    /** Group result for {@link #scanMobSpawns}: spawns are indexed two ways simultaneously. */
    private record MobScan(Map<MobCategory, List<MobSpawnInfo>> byCategory,
                            Map<ResourceKey<Biome>, List<MobSpawnInfo>> byBiome) {}

    /** Group result for {@link #scanPlacedFeatures}: list + reverse tag index in one pass. */
    private record PlacedScan(List<PlacedFeatureInfo> features,
                               Map<TagKey<PlacedFeature>, List<PlacedFeatureInfo>> byTag) {}

    /**
     * Walk PLACED_FEATURE; extract VerticalRange via the Access Transformer-exposed fields,
     * and build a reverse tag index in the same pass using {@code Holder.tags()}.
     */
    private static PlacedScan scanPlacedFeatures(MinecraftServer server, int worldBottom, int worldTop) {
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        List<PlacedFeatureInfo> features = new ArrayList<>();
        Map<TagKey<PlacedFeature>, List<PlacedFeatureInfo>> byTag = new HashMap<>();
        lookup.listElements().forEach(ref -> {
            ResourceKey<PlacedFeature> key = ref.unwrapKey().orElseThrow();
            PlacedFeature pf = ref.value();
            VerticalRange range = extractVerticalRange(pf, worldBottom, worldTop);
            PlacedFeatureInfo info = new PlacedFeatureInfo(
                    key, range != null ? range : FALLBACK_RANGE, 1, Set.of());
            features.add(info);
            ref.tags().forEach(tag ->
                    byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(info));
        });
        return new PlacedScan(features, byTag);
    }

    /**
     * Walk STRUCTURE_SET to build a Structure -> StructurePlacement reverse map, then walk
     * STRUCTURE to emit one {@link StructurePlacementInfo} per (structure, set) pairing.
     * Structures not in any set are skipped (they would never generate). Structures in
     * multiple sets emit one entry per set, so callers see every placement variant.
     */
    private static List<StructurePlacementInfo> scanStructures(MinecraftServer server) {
        var registryAccess = server.registryAccess();
        HolderLookup.RegistryLookup<StructureSet> setLookup =
                registryAccess.lookupOrThrow(Registries.STRUCTURE_SET);
        HolderLookup.RegistryLookup<Structure> structureLookup =
                registryAccess.lookupOrThrow(Registries.STRUCTURE);

        // Build structure-key -> list-of-placements via the StructureSet entries.
        Map<ResourceKey<Structure>, List<StructurePlacement>> placementsByStructure = new HashMap<>();
        setLookup.listElements().forEach(setRef -> {
            StructureSet set = setRef.value();
            StructurePlacement placement = set.placement();
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                entry.structure().unwrapKey().ifPresent(key ->
                        placementsByStructure.computeIfAbsent(key, k -> new ArrayList<>()).add(placement));
            }
        });

        List<StructurePlacementInfo> infos = new ArrayList<>();
        structureLookup.listElements().forEach(ref -> {
            ResourceKey<Structure> key = ref.unwrapKey().orElseThrow();
            Structure structure = ref.value();
            List<StructurePlacement> placements = placementsByStructure.get(key);
            if (placements == null || placements.isEmpty()) {
                // Defined but not bound to any set — silently skip; it wouldn't generate.
                return;
            }
            Set<TagKey<Biome>> biomeTags = extractBiomeTags(structure);
            for (StructurePlacement placement : placements) {
                infos.add(new StructurePlacementInfo(key, placement, biomeTags));
            }
        });
        return infos;
    }

    /**
     * Extract biome tag references from a Structure's biome filter. HolderSet can be either
     * a tag-backed view ({@code Either.left}) or a direct list of holders; only the tag-backed
     * form yields a {@link TagKey}, so this returns an empty set otherwise.
     */
    private static Set<TagKey<Biome>> extractBiomeTags(Structure structure) {
        Set<TagKey<Biome>> tags = new HashSet<>();
        structure.biomes().unwrap().ifLeft(tags::add);
        return tags;
    }

    /**
     * Walk every biome's <em>original</em> generation settings and record which decoration
     * step(s) each PlacedFeature key appears in. Uses {@code modifiableBiomeInfo()
     * .getOriginalBiomeInfo()} so the lookup reflects pre-modifier state — biome modifiers
     * (including Isekai's own) haven't been applied at ServerAboutToStart time, so this is
     * the canonical "what step did the datapack put this feature in" mapping.
     *
     * <p>{@code BiomeGenerationSettings.features()} returns a list indexed by
     * {@link GenerationStep.Decoration#ordinal()}; we iterate the {@code values()} array in
     * parallel to recover the enum constant.
     */
    private static Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> scanFeatureSteps(MinecraftServer server) {
        HolderLookup.RegistryLookup<Biome> biomeLookup =
                server.registryAccess().lookupOrThrow(Registries.BIOME);
        Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> index = new HashMap<>();
        GenerationStep.Decoration[] steps = GenerationStep.Decoration.values();
        biomeLookup.listElements().forEach(ref -> {
            var generation = ref.value().modifiableBiomeInfo().getOriginalBiomeInfo().generationSettings();
            var perStep = generation.features();
            for (int i = 0; i < perStep.size() && i < steps.length; i++) {
                GenerationStep.Decoration step = steps[i];
                for (var holder : perStep.get(i)) {
                    holder.unwrapKey().ifPresent(key ->
                            index.computeIfAbsent(key, k -> EnumSet.noneOf(GenerationStep.Decoration.class)).add(step));
                }
            }
        });
        return index;
    }

    /**
     * Walk every biome's {@link MobSpawnSettings} and group spawn entries by mob category.
     * Each {@link MobSpawnSettings.SpawnerData} becomes one {@link MobSpawnInfo}.
     *
     * <p>Identical (entity, weight, min, max) tuples appearing in multiple biomes generate
     * duplicate entries — this is intentional, since per-biome spawn density compounds the
     * effective rate. Consumers that want unique entity types should de-dup downstream.
     */
    private static MobScan scanMobSpawns(MinecraftServer server) {
        HolderLookup.RegistryLookup<Biome> biomeLookup =
                server.registryAccess().lookupOrThrow(Registries.BIOME);

        Map<MobCategory, List<MobSpawnInfo>> byCategory = new HashMap<>();
        Map<ResourceKey<Biome>, List<MobSpawnInfo>> byBiome = new HashMap<>();
        biomeLookup.listElements().forEach(ref -> {
            ResourceKey<Biome> biomeKey = ref.unwrapKey().orElseThrow();
            Biome biome = ref.value();
            MobSpawnSettings mobSettings = biome.getMobSettings();
            for (MobCategory category : MobCategory.values()) {
                var spawners = mobSettings.getMobs(category).unwrap();
                if (spawners.isEmpty()) continue;
                List<MobSpawnInfo> categoryBucket = byCategory.computeIfAbsent(category, k -> new ArrayList<>());
                List<MobSpawnInfo> biomeBucket = byBiome.computeIfAbsent(biomeKey, k -> new ArrayList<>());
                for (MobSpawnSettings.SpawnerData sd : spawners) {
                    MobSpawnInfo info = new MobSpawnInfo(
                            sd.type, category, sd.getWeight().asInt(), sd.minCount, sd.maxCount);
                    categoryBucket.add(info);
                    biomeBucket.add(info);
                }
            }
        });
        return new MobScan(byCategory, byBiome);
    }

    private static VerticalRange extractVerticalRange(PlacedFeature pf, int worldBottom, int worldTop) {
        for (PlacementModifier mod : pf.placement()) {
            if (mod instanceof HeightRangePlacement hrp) {
                return convertHeightProvider(hrp.height, worldBottom, worldTop);
            }
        }
        return null;
    }

    private static VerticalRange convertHeightProvider(HeightProvider hp, int worldBottom, int worldTop) {
        if (hp instanceof UniformHeight uh) {
            return new VerticalRange(
                    anchorToY(uh.minInclusive, worldBottom, worldTop),
                    anchorToY(uh.maxInclusive, worldBottom, worldTop),
                    HeightDistribution.UNIFORM);
        }
        if (hp instanceof TrapezoidHeight th) {
            return new VerticalRange(
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
            return new VerticalRange(
                    anchorToY(bbh.minInclusive, worldBottom, worldTop),
                    anchorToY(bbh.maxInclusive, worldBottom, worldTop),
                    HeightDistribution.BIASED_LOW);
        }
        if (hp instanceof VeryBiasedToBottomHeight vbbh) {
            return new VerticalRange(
                    anchorToY(vbbh.minInclusive, worldBottom, worldTop),
                    anchorToY(vbbh.maxInclusive, worldBottom, worldTop),
                    HeightDistribution.BIASED_LOW);
        }
        // WeightedListHeight is the one remaining vanilla variant — its piece list isn't
        // a simple range, so we fall back. It's rare in vanilla worldgen.
        return FALLBACK_RANGE;
    }

    private static int anchorToY(VerticalAnchor anchor, int worldBottom, int worldTop) {
        if (anchor instanceof VerticalAnchor.Absolute a) return a.y();
        if (anchor instanceof VerticalAnchor.AboveBottom ab) return worldBottom + ab.offset();
        if (anchor instanceof VerticalAnchor.BelowTop bt) return worldTop - bt.offset();
        return 0;
    }

    public List<PlacedFeatureInfo> ores() { return ores; }
    public List<StructurePlacementInfo> structures() { return structures; }

    /** Overworld build height bottom captured at scan time. v0.7 strategy remap source range. */
    public int worldBottom() { return worldBottom; }

    /** Overworld build height top captured at scan time. */
    public int worldTop() { return worldTop; }

    /**
     * Decoration step(s) the given PlacedFeature was originally indexed under across the
     * scanned biomes. Empty set means the feature wasn't referenced by any biome at scan
     * time — e.g. a feature defined in JSON but unused, or one that was only added by a
     * later biome modifier.
     */
    public Set<GenerationStep.Decoration> stepsFor(ResourceKey<PlacedFeature> key) {
        return stepsByFeature.getOrDefault(key, Set.of());
    }

    /** {@code true} if this info's range is the sentinel returned when extraction failed. */
    public boolean isFallback(PlacedFeatureInfo info) {
        return info.range() == FALLBACK_RANGE;
    }

    public List<MobSpawnInfo> mobsForCategory(MobCategory category) {
        return mobsByCategory.getOrDefault(category, List.of());
    }

    /** Spawn entries originally defined in the given biome's MobSpawnSettings. */
    public List<MobSpawnInfo> mobsForBiome(ResourceKey<Biome> biome) {
        return mobsByBiome.getOrDefault(biome, List.of());
    }

    /** PlacedFeatures that were tagged with the given TagKey at scan time. */
    public List<PlacedFeatureInfo> oresForTag(TagKey<PlacedFeature> tag) {
        return oresByTag.getOrDefault(tag, List.of());
    }

    public boolean isEmpty() {
        return ores.isEmpty() && structures.isEmpty() && mobsByCategory.isEmpty();
    }

    /** v0.6 backwards-compat constructor — kept for tests / fixed snapshots that pre-date the indices. */
    public VanillaRuleSnapshot(List<PlacedFeatureInfo> ores,
                                List<StructurePlacementInfo> structures,
                                Map<MobCategory, List<MobSpawnInfo>> mobsByCategory,
                                int worldBottom,
                                int worldTop) {
        this(ores, structures, mobsByCategory, Map.of(), Map.of(), Map.of(), worldBottom, worldTop);
    }
}
