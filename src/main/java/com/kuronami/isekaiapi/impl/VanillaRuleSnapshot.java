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
            new VanillaRuleSnapshot(List.of(), List.of(), Map.of(), -64, 320);

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
    private final List<StructurePlacementInfo> structures;
    private final Map<MobCategory, List<MobSpawnInfo>> mobsByCategory;
    private final int worldBottom;
    private final int worldTop;

    public VanillaRuleSnapshot(List<PlacedFeatureInfo> ores,
                                List<StructurePlacementInfo> structures,
                                Map<MobCategory, List<MobSpawnInfo>> mobsByCategory,
                                int worldBottom,
                                int worldTop) {
        this.ores = List.copyOf(ores);
        this.structures = List.copyOf(structures);
        this.mobsByCategory = Map.copyOf(mobsByCategory);
        this.worldBottom = worldBottom;
        this.worldTop = worldTop;
    }

    public static VanillaRuleSnapshot scan(MinecraftServer server) {
        IsekaiApi.LOGGER.info("[Isekai v0.6] VanillaRuleSnapshot.scan: walking PLACED_FEATURE + STRUCTURE + BIOME registries");

        // Resolve the overworld's actual build height range — vanilla 1.21.1 ships -64..320
        // but cubic-chunks or world height mods can change this. PlacedFeatures scanned here
        // are typically authored against the overworld even when reused in modded dimensions
        // (the Y range absoluteness is set at server startup, not per-level visit).
        int overworldBottom = server.overworld().getMinBuildHeight();
        int overworldTop = server.overworld().getMaxBuildHeight();

        List<PlacedFeatureInfo> features = scanPlacedFeatures(server, overworldBottom, overworldTop);
        List<StructurePlacementInfo> structures = scanStructures(server);
        Map<MobCategory, List<MobSpawnInfo>> mobs = scanMobSpawns(server);

        long withRange = features.stream().filter(info -> info.range() != FALLBACK_RANGE).count();
        int mobTotal = mobs.values().stream().mapToInt(List::size).sum();
        IsekaiApi.LOGGER.info(
                "[Isekai v0.6] Scanned {} placed features ({} with extracted VerticalRange, "
                        + "{} with fallback) + {} structure placements + {} mob spawn entries across {} categories "
                        + "(overworld build-height {}..{})",
                features.size(), withRange, features.size() - withRange,
                structures.size(), mobTotal, mobs.size(),
                overworldBottom, overworldTop);

        return new VanillaRuleSnapshot(features, structures, mobs, overworldBottom, overworldTop);
    }

    /** Walk PLACED_FEATURE; extract VerticalRange via the Access Transformer-exposed fields. */
    private static List<PlacedFeatureInfo> scanPlacedFeatures(MinecraftServer server, int worldBottom, int worldTop) {
        HolderLookup.RegistryLookup<PlacedFeature> lookup =
                server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        List<PlacedFeatureInfo> features = new ArrayList<>();
        lookup.listElements().forEach(ref -> {
            ResourceKey<PlacedFeature> key = ref.unwrapKey().orElseThrow();
            PlacedFeature pf = ref.value();
            VerticalRange range = extractVerticalRange(pf, worldBottom, worldTop);
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

    public List<MobSpawnInfo> mobsForCategory(MobCategory category) {
        return mobsByCategory.getOrDefault(category, List.of());
    }

    public boolean isEmpty() {
        return ores.isEmpty() && structures.isEmpty() && mobsByCategory.isEmpty();
    }
}
