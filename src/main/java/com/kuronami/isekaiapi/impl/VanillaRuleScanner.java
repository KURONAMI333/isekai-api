package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
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
 * Scans the vanilla + modded worldgen registries at {@code ServerAboutToStartEvent} (and
 * on datapack reload) to populate a {@link VanillaRuleSnapshot}. Walked registries:
 *
 * <ul>
 *   <li>PLACED_FEATURE — every placed feature plus its tag index and per-biome membership.
 *       VerticalRange extraction goes through {@link HeightProviderExtraction}.</li>
 *   <li>STRUCTURE_SET → STRUCTURE — emits one {@link StructurePlacementInfo} per
 *       (structure, set) pairing; biome filter via the tag-backed HolderSet projection.</li>
 *   <li>BIOME — per-biome mob spawn lists, per-category aggregates, plus the per-biome
 *       feature membership used by the ADD-phase scoping.</li>
 * </ul>
 */
final class VanillaRuleScanner {

    private VanillaRuleScanner() {}

    static VanillaRuleSnapshot scan(MinecraftServer server) {
        IsekaiApi.LOGGER.info("[Isekai] VanillaRuleScanner.scan: walking PLACED_FEATURE + STRUCTURE + BIOME registries");

        // Resolve the overworld's actual build height range. At ServerAboutToStartEvent
        // (the first scan trigger), server.overworld() returns null because worlds aren't
        // yet loaded — fall back to vanilla defaults; the datapack-reload listener will
        // rebuild the snapshot once worlds are available and pick up any custom heights.
        // Inclusive top — Mojang's getMaxBuildHeight() returns exclusive, so subtract 1.
        int overworldBottom = VanillaRuleSnapshot.OVERWORLD_BOTTOM;
        int overworldTop = VanillaRuleSnapshot.OVERWORLD_TOP;
        var overworldLevel = server.overworld();
        if (overworldLevel != null) {
            overworldBottom = overworldLevel.getMinBuildHeight();
            overworldTop = overworldLevel.getMaxBuildHeight() - 1;
        } else {
            IsekaiApi.LOGGER.debug("[Isekai] scan at pre-world lifecycle stage; using overworld defaults {}..{}",
                    overworldBottom, overworldTop);
        }

        var placedScan = scanPlacedFeatures(server, overworldBottom, overworldTop);
        var perDimRanges = scanPerDimensionRanges(server, overworldBottom, overworldTop);
        var structScan = scanStructures(server);
        var mobScan = scanMobSpawns(server);
        var biomeFeatureScan = scanBiomeFeatures(server);

        long withRange = placedScan.features().stream()
                .filter(info -> info.range() != HeightProviderExtraction.FALLBACK_RANGE).count();
        int mobTotal = mobScan.byCategory().values().stream().mapToInt(List::size).sum();
        IsekaiApi.LOGGER.info(
                "[Isekai] Scanned {} placed features ({} with extracted VerticalRange, "
                        + "{} with fallback, {} step-indexed) + {} structure placements + {} mob spawn entries across {} categories "
                        + "(overworld build-height {}..{})",
                placedScan.features().size(), withRange, placedScan.features().size() - withRange,
                biomeFeatureScan.steps().size(),
                structScan.infos().size(), mobTotal, mobScan.byCategory().size(),
                overworldBottom, overworldTop);

        IsekaiApi.LOGGER.debug("[Isekai] tag indices: {} placed-feature tags, {} structure tags",
                placedScan.byTag().size(), structScan.byTag().size());
        IsekaiApi.LOGGER.debug("[Isekai] per-dim ranges: {} dimensions indexed", perDimRanges.size());

        return new VanillaRuleSnapshot(
                placedScan.features(), structScan.infos(),
                mobScan.byCategory(), mobScan.byBiome(),
                biomeFeatureScan.steps(), placedScan.byTag(), structScan.byTag(),
                biomeFeatureScan.byBiome(), perDimRanges,
                overworldBottom, overworldTop);
    }

    /**
     * Walk every loaded ServerLevel; for each, re-resolve every PlacedFeature's
     * HeightProvider against that level's actual build height. Stores only entries that
     * differ from the overworld-default range (memory optimisation — features with
     * absolute anchors return identical Y in every dimension).
     */
    private static Map<ResourceKey<Level>, Map<ResourceKey<PlacedFeature>, VerticalRange>> scanPerDimensionRanges(
            MinecraftServer server, int overworldBottom, int overworldTop) {
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        // Cache HeightProvider per feature once so we don't re-walk placement modifiers per dim.
        Map<ResourceKey<PlacedFeature>, HeightProvider> hpByKey = new HashMap<>();
        lookup.listElements().forEach(ref -> {
            ResourceKey<PlacedFeature> key = ref.unwrapKey().orElseThrow();
            HeightProvider hp = HeightProviderExtraction.extractHeightProvider(ref.value());
            if (hp != null) hpByKey.put(key, hp);
        });

        Map<ResourceKey<Level>, Map<ResourceKey<PlacedFeature>, VerticalRange>> out = new HashMap<>();
        for (var level : server.getAllLevels()) {
            int bottom = level.getMinBuildHeight();
            // Treat top as inclusive throughout the snapshot — Mojang's getMaxBuildHeight is exclusive.
            int top = level.getMaxBuildHeight() - 1;
            if (bottom == overworldBottom && top == overworldTop) {
                // Same bounds as overworld — every resolution is identical to the global list.
                continue;
            }
            Map<ResourceKey<PlacedFeature>, VerticalRange> dimMap = new HashMap<>();
            for (var e : hpByKey.entrySet()) {
                VerticalRange r = HeightProviderExtraction.convertHeightProvider(e.getValue(), bottom, top);
                if (r != null && r != HeightProviderExtraction.FALLBACK_RANGE) {
                    dimMap.put(e.getKey(), r);
                }
            }
            if (!dimMap.isEmpty()) {
                out.put(level.dimension(), dimMap);
            }
        }
        return out;
    }

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
            VerticalRange range = HeightProviderExtraction.extractVerticalRange(pf, worldBottom, worldTop);
            PlacedFeatureInfo info = new PlacedFeatureInfo(
                    key, range != null ? range : HeightProviderExtraction.FALLBACK_RANGE, 1, Set.of());
            features.add(info);
            ref.tags().forEach(tag ->
                    byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(info));
        });
        return new PlacedScan(features, byTag);
    }

    /** Group result for {@link #scanStructures}: list + reverse tag index in one pass. */
    private record StructureScan(List<StructurePlacementInfo> infos,
                                  Map<TagKey<Structure>, List<StructurePlacementInfo>> byTag) {}

    /**
     * Walk STRUCTURE_SET to build a Structure -> StructurePlacement reverse map, then walk
     * STRUCTURE to emit one {@link StructurePlacementInfo} per (structure, set) pairing.
     * Structures not in any set are skipped (they would never generate). Structures in
     * multiple sets emit one entry per set, so callers see every placement variant.
     *
     * <p>Also builds a parallel {@code Map<TagKey<Structure>, List<StructurePlacementInfo>>}
     * via {@code Holder.tags()} so {@code IsekaiQuery.getStructuresByTag} returns in O(1).
     */
    private static StructureScan scanStructures(MinecraftServer server) {
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
        Map<TagKey<Structure>, List<StructurePlacementInfo>> byTag = new HashMap<>();
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
                StructurePlacementInfo info = new StructurePlacementInfo(key, placement, biomeTags);
                infos.add(info);
                ref.tags().forEach(tag ->
                        byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(info));
            }
        });
        return new StructureScan(infos, byTag);
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

    /** Group result for {@link #scanBiomeFeatures}: step index + per-biome membership in one pass. */
    private record BiomeFeatureScan(
            Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> steps,
            Map<ResourceKey<Biome>, Set<ResourceKey<PlacedFeature>>> byBiome) {}

    /**
     * Walk every biome's <em>original</em> generation settings and record (a) which
     * decoration step(s) each PlacedFeature appears in across the registry, and (b) which
     * PlacedFeatures appear in each biome. Uses {@code modifiableBiomeInfo()
     * .getOriginalBiomeInfo()} — the immutable pre-modifier definition — so the result is
     * the canonical "what was originally there" mapping, stable regardless of when the scan
     * runs. (Biome modifiers <em>are</em> applied by {@code ServerLifecycleHooks.runModifiers}
     * before {@code ServerAboutToStartEvent}, and the lazy snapshot scan may run mid-apply;
     * reading the original info keeps this deterministic and free of the modified state.)
     *
     * <p>Consequence: ore/feature remap sees features present in the biome's <em>base</em>
     * definition. Ores another mod injects via its own biome modifier are NOT in the
     * original info, so they are outside the remap's scope.
     */
    private static BiomeFeatureScan scanBiomeFeatures(MinecraftServer server) {
        HolderLookup.RegistryLookup<Biome> biomeLookup =
                server.registryAccess().lookupOrThrow(Registries.BIOME);
        Map<ResourceKey<PlacedFeature>, Set<GenerationStep.Decoration>> stepIndex = new HashMap<>();
        Map<ResourceKey<Biome>, Set<ResourceKey<PlacedFeature>>> biomeIndex = new HashMap<>();
        GenerationStep.Decoration[] steps = GenerationStep.Decoration.values();
        biomeLookup.listElements().forEach(ref -> {
            ResourceKey<Biome> biomeKey = ref.unwrapKey().orElseThrow();
            Set<ResourceKey<PlacedFeature>> inBiome = biomeIndex.computeIfAbsent(biomeKey, k -> new HashSet<>());
            var generation = ref.value().modifiableBiomeInfo().getOriginalBiomeInfo().generationSettings();
            var perStep = generation.features();
            for (int i = 0; i < perStep.size() && i < steps.length; i++) {
                GenerationStep.Decoration step = steps[i];
                for (var holder : perStep.get(i)) {
                    holder.unwrapKey().ifPresent(key -> {
                        stepIndex.computeIfAbsent(key, k -> EnumSet.noneOf(GenerationStep.Decoration.class)).add(step);
                        inBiome.add(key);
                    });
                }
            }
        });
        return new BiomeFeatureScan(stepIndex, biomeIndex);
    }

    /** Group result for {@link #scanMobSpawns}: spawns are indexed two ways simultaneously. */
    private record MobScan(Map<MobCategory, List<MobSpawnInfo>> byCategory,
                            Map<ResourceKey<Biome>, List<MobSpawnInfo>> byBiome) {}

    /**
     * Walk every biome's {@link MobSpawnSettings} and group spawn entries by mob category.
     * Each {@link MobSpawnSettings.SpawnerData} becomes one {@link MobSpawnInfo}.
     *
     * <p>Identical (entity, weight, min, max) tuples appearing in multiple biomes generate
     * duplicate entries — intentional, since per-biome spawn density compounds the effective
     * rate. Consumers wanting unique entity types should de-dup downstream.
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
}
