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
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.TrapezoidHeight;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
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
        IsekaiApi.LOGGER.info("[Isekai v0.6] VanillaRuleSnapshot.scan: walking PLACED_FEATURE + STRUCTURE + BIOME registries");

        List<PlacedFeatureInfo> features = scanPlacedFeatures(server);
        List<StructurePlacementInfo> structures = scanStructures(server);
        Map<MobCategory, List<MobSpawnInfo>> mobs = scanMobSpawns(server);

        long withRange = features.stream().filter(info -> info.range() != FALLBACK_RANGE).count();
        int mobTotal = mobs.values().stream().mapToInt(List::size).sum();
        IsekaiApi.LOGGER.info(
                "[Isekai v0.6] Scanned {} placed features ({} with extracted VerticalRange, "
                        + "{} with fallback) + {} structure placements + {} mob spawn entries across {} categories",
                features.size(), withRange, features.size() - withRange,
                structures.size(), mobTotal, mobs.size());

        return new VanillaRuleSnapshot(features, structures, mobs);
    }

    /** Walk PLACED_FEATURE; extract VerticalRange via the Access Transformer-exposed fields. */
    private static List<PlacedFeatureInfo> scanPlacedFeatures(MinecraftServer server) {
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        List<PlacedFeatureInfo> features = new ArrayList<>();
        lookup.listElements().forEach(ref -> {
            ResourceKey<PlacedFeature> key = ref.unwrapKey().orElseThrow();
            PlacedFeature pf = ref.value();
            VerticalRange range = extractVerticalRange(pf);
            features.add(new PlacedFeatureInfo(key, range != null ? range : FALLBACK_RANGE, 1, Set.of()));
        });
        return features;
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
     * Walk every biome's {@link MobSpawnSettings} and group spawn entries by mob category.
     * Each {@link MobSpawnSettings.SpawnerData} becomes one {@link MobSpawnInfo}.
     *
     * <p>Identical (entity, weight, min, max) tuples appearing in multiple biomes generate
     * duplicate entries — this is intentional, since per-biome spawn density compounds the
     * effective rate. Consumers that want unique entity types should de-dup downstream.
     */
    private static Map<MobCategory, List<MobSpawnInfo>> scanMobSpawns(MinecraftServer server) {
        HolderLookup.RegistryLookup<Biome> biomeLookup =
                server.registryAccess().lookupOrThrow(Registries.BIOME);

        Map<MobCategory, List<MobSpawnInfo>> byCategory = new HashMap<>();
        biomeLookup.listElements().forEach(ref -> {
            Biome biome = ref.value();
            MobSpawnSettings mobSettings = biome.getMobSettings();
            for (MobCategory category : MobCategory.values()) {
                var spawners = mobSettings.getMobs(category).unwrap();
                if (spawners.isEmpty()) continue;
                List<MobSpawnInfo> bucket = byCategory.computeIfAbsent(category, k -> new ArrayList<>());
                for (MobSpawnSettings.SpawnerData sd : spawners) {
                    bucket.add(new MobSpawnInfo(
                            sd.type, category, sd.getWeight().asInt(), sd.minCount, sd.maxCount));
                }
            }
        });
        return byCategory;
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
