package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single-layer worldshape specification. Pass to
 * {@link IsekaiRemap#declareWorldshape}. For multi-layer (stacked) worldshapes, use
 * {@link LayeredDescriptor} via {@link IsekaiRemap#declareLayeredWorldshape}.
 *
 * <p>{@code structurePredicates} maps each vanilla / modded structure key to the spatial
 * condition under which it should spawn in this worldshape. Structures absent from the
 * map fall back to {@code defaultStructurePredicate} — this catches modded structures
 * the consumer wasn't aware of at design time, while still letting them be placed
 * sensibly. {@code structureStrategy} additionally controls Y-range remapping for any
 * structure that survives the predicate filter.
 *
 * <p>{@code exclusions} and {@code additions} bundle the per-content-kind sets/lists
 * (features, structures, carvers) so the descriptor stays under the 16-field limit of
 * {@code RecordCodecBuilder} while keeping the public API self-documenting. {@code
 * contentOverrides} further bundles {@code featurePredicates}, {@code
 * structureSpawnOverrides}, and {@code blockOverrides} for the same reason — the parent
 * record exposes those three via convenience accessors so call sites keep a flat API.
 *
 * <p>{@code appliesTo} (a {@link BiomeSelection}) accepts either a list of biome keys or
 * an object with explicit {@code keys} / {@code tags} fields. Tags collapse 35-biome
 * enumerations to a single line.
 *
 * <p>If two consumers register a descriptor for the same dimension, the one with the
 * higher {@link #priority} wins. Ties replace.
 */
public record WorldshapeDescriptor(
        ResourceKey<Level> dimension,
        VerticalRange playableRange,
        SurfaceAnchor surfaceAnchor,
        RemapStrategy oreStrategy,
        RemapStrategy structureStrategy,
        RemapStrategy mobSpawnStrategy,
        Map<ResourceKey<Structure>, SpatialPredicate> structurePredicates,
        SpatialPredicate defaultStructurePredicate,
        BiomeSelection appliesTo,
        Exclusions exclusions,
        Map<MobCategory, RemapStrategy> mobSpawnStrategyByCategory,
        Additions additions,
        AtmosphereOverride atmosphere,
        ClientAtmosphereOverride clientAtmosphere,
        ContentOverrides contentOverrides,
        int priority
) {
    public WorldshapeDescriptor {
        structurePredicates = Map.copyOf(structurePredicates);
        if (appliesTo == null) appliesTo = BiomeSelection.EMPTY;
        mobSpawnStrategyByCategory = Map.copyOf(mobSpawnStrategyByCategory);
        if (exclusions == null) exclusions = Exclusions.EMPTY;
        if (additions == null) additions = Additions.EMPTY;
        if (atmosphere == null) atmosphere = AtmosphereOverride.EMPTY;
        if (clientAtmosphere == null) clientAtmosphere = ClientAtmosphereOverride.EMPTY;
        if (contentOverrides == null) contentOverrides = ContentOverrides.EMPTY;
    }

    // --- Convenience accessors so call sites keep a flat API ---
    /** Shortcut for {@code contentOverrides().featurePredicates()}. */
    public Map<ResourceKey<PlacedFeature>, SpatialPredicate> featurePredicates() {
        return contentOverrides.featurePredicates();
    }
    /** Shortcut for {@code contentOverrides().structureSpawnOverrides()}. */
    public List<StructureSpawnConfig> structureSpawnOverrides() {
        return contentOverrides.structureSpawnOverrides();
    }
    /** Shortcut for {@code contentOverrides().blockOverrides()}. */
    public BlockOverrides blockOverrides() {
        return contentOverrides.blockOverrides();
    }

    /**
     * Per-structure mob spawn override entry. Applied by {@code apply_worldshape_structures}
     * to the named structure's {@code StructureSettings.spawnOverrides()} during the MODIFY
     * phase. Each entry binds one (structure, category) pair to a bounding-box scope and a
     * list of spawn entries; when {@code replace} is true the existing override for that
     * (structure, category) is cleared before our spawns are added, when false our spawns
     * are appended on top of the existing list.
     *
     * <p>Use case: "no creepers in pillager outposts" — set
     * {@code structure=pillager_outpost, category=monster, replace=true, spawns=[only the
     * mobs you want]}; or "pillager outposts also spawn extra mobs" — same but
     * {@code replace=false}.
     */
    public record StructureSpawnConfig(
            ResourceKey<Structure> structure,
            MobCategory category,
            StructureSpawnOverride.BoundingBoxType boundingBox,
            List<AdditionalMobSpawn> spawns,
            boolean replace
    ) {
        public StructureSpawnConfig { spawns = List.copyOf(spawns); }

        public static final Codec<StructureSpawnConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.STRUCTURE).fieldOf("structure")
                        .forGetter(StructureSpawnConfig::structure),
                MobCategory.CODEC.fieldOf("category").forGetter(StructureSpawnConfig::category),
                StructureSpawnOverride.BoundingBoxType.CODEC.optionalFieldOf(
                                "bounding_box", StructureSpawnOverride.BoundingBoxType.PIECE)
                        .forGetter(StructureSpawnConfig::boundingBox),
                AdditionalMobSpawn.CODEC.listOf().fieldOf("spawns")
                        .forGetter(StructureSpawnConfig::spawns),
                Codec.BOOL.optionalFieldOf("replace", true)
                        .forGetter(StructureSpawnConfig::replace)
        ).apply(i, StructureSpawnConfig::new));
    }

    /** Set of registry keys to drop from matched biomes during the REMOVE phase. */
    public record Exclusions(
            Set<ResourceKey<PlacedFeature>> features,
            Set<ResourceKey<Structure>> structures,
            Set<ResourceKey<ConfiguredWorldCarver<?>>> carvers,
            Set<EntityType<?>> mobSpawns
    ) {
        public Exclusions {
            features = Set.copyOf(features);
            structures = Set.copyOf(structures);
            carvers = Set.copyOf(carvers);
            mobSpawns = Set.copyOf(mobSpawns);
        }

        public static final Exclusions EMPTY = new Exclusions(Set.of(), Set.of(), Set.of(), Set.of());

        public static final Codec<Exclusions> CODEC = RecordCodecBuilder.create(i -> i.group(
                resourceKeySetCodec(Registries.PLACED_FEATURE, "features").forGetter(Exclusions::features),
                resourceKeySetCodec(Registries.STRUCTURE, "structures").forGetter(Exclusions::structures),
                resourceKeySetCodec(Registries.CONFIGURED_CARVER, "carvers").forGetter(Exclusions::carvers),
                entityTypeSetCodec("mob_spawns").forGetter(Exclusions::mobSpawns)
        ).apply(i, Exclusions::new));
    }

    /** Lists of consumer-injected entries to add during the ADD / MODIFY phases. */
    public record Additions(
            List<AdditionalFeature> features,
            List<AdditionalCarver> carvers,
            List<AdditionalMobSpawn> mobSpawns
    ) {
        public Additions {
            features = List.copyOf(features);
            carvers = List.copyOf(carvers);
            mobSpawns = List.copyOf(mobSpawns);
        }

        public static final Additions EMPTY = new Additions(List.of(), List.of(), List.of());

        public static final Codec<Additions> CODEC = RecordCodecBuilder.create(i -> i.group(
                AdditionalFeature.CODEC.listOf().optionalFieldOf("features", List.of())
                        .forGetter(Additions::features),
                AdditionalCarver.CODEC.listOf().optionalFieldOf("carvers", List.of())
                        .forGetter(Additions::carvers),
                AdditionalMobSpawn.CODEC.listOf().optionalFieldOf("mob_spawns", List.of())
                        .forGetter(Additions::mobSpawns)
        ).apply(i, Additions::new));
    }

    /**
     * An extra mob spawn entry to add to matched biomes during the MODIFY phase. Mirrors
     * vanilla {@code MobSpawnSettings.SpawnerData}: a (type, weight, minCount, maxCount)
     * tuple plus the MobCategory bucket it lives in.
     */
    public record AdditionalMobSpawn(MobCategory category, EntityType<?> type,
                                      int weight, int minCount, int maxCount) {
        public AdditionalMobSpawn {
            if (weight < 1) throw new IllegalArgumentException("weight must be >= 1: " + weight);
            if (minCount < 1) throw new IllegalArgumentException("minCount must be >= 1: " + minCount);
            if (maxCount < minCount) throw new IllegalArgumentException(
                    "maxCount (" + maxCount + ") < minCount (" + minCount + ")");
        }
        public static final Codec<AdditionalMobSpawn> CODEC = RecordCodecBuilder.create(i -> i.group(
                MobCategory.CODEC.fieldOf("category").forGetter(AdditionalMobSpawn::category),
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("type").forGetter(AdditionalMobSpawn::type),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(AdditionalMobSpawn::weight),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("min_count").forGetter(AdditionalMobSpawn::minCount),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_count").forGetter(AdditionalMobSpawn::maxCount)
        ).apply(i, AdditionalMobSpawn::new));
    }

    /** A ConfiguredWorldCarver the consumer wants injected at the named carving step. */
    public record AdditionalCarver(ResourceKey<ConfiguredWorldCarver<?>> carver,
                                    GenerationStep.Carving step) {
        public static final Codec<AdditionalCarver> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.CONFIGURED_CARVER).fieldOf("carver")
                        .forGetter(AdditionalCarver::carver),
                GenerationStep.Carving.CODEC.fieldOf("step").forGetter(AdditionalCarver::step)
        ).apply(i, AdditionalCarver::new));
    }

    /**
     * A {@link PlacedFeature} the consumer wants injected into the matched biomes at the
     * given {@link GenerationStep.Decoration} step.
     */
    public record AdditionalFeature(ResourceKey<PlacedFeature> feature, GenerationStep.Decoration step) {
        public static final Codec<AdditionalFeature> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.PLACED_FEATURE).fieldOf("feature")
                        .forGetter(AdditionalFeature::feature),
                GenerationStep.Decoration.CODEC.fieldOf("step")
                        .forGetter(AdditionalFeature::step)
        ).apply(i, AdditionalFeature::new));
    }

    /**
     * Resolve the strategy for the given {@link MobCategory}. Falls through to the global
     * {@link #mobSpawnStrategy} when no per-category override is present.
     */
    public RemapStrategy resolveMobSpawnStrategy(MobCategory category) {
        return mobSpawnStrategyByCategory.getOrDefault(category, mobSpawnStrategy);
    }

    public static final int DEFAULT_PRIORITY = 100;

    /**
     * Convenience builder for Java consumers — avoids threading 14 positional arguments
     * through the canonical constructor and gives optional fields sensible defaults.
     * Required fields ({@code dimension}, {@code playableRange}, {@code surfaceAnchor},
     * three strategies, {@code defaultStructurePredicate}) must be set before {@link #build}
     * is called; optional fields default to {@code Set.of()} / {@code Map.of()} / EMPTY
     * sub-records / {@link #DEFAULT_PRIORITY}.
     *
     * <pre>{@code
     * WorldshapeDescriptor.builder()
     *     .dimension(Level.OVERWORLD)
     *     .playableRange(new VerticalRange(80, 200, HeightDistribution.UNIFORM))
     *     .surfaceAnchor(new SurfaceAnchor.FixedY(150))
     *     .oreStrategy(new RemapStrategy.Linear())
     *     .structureStrategy(new RemapStrategy.Identity())
     *     .mobSpawnStrategy(new RemapStrategy.Identity())
     *     .defaultStructurePredicate(new SpatialPredicate.YInRange(80, 200))
     *     .priority(110)
     *     .build();
     * }</pre>
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder; see {@link WorldshapeDescriptor#builder()}. */
    public static final class Builder {
        private ResourceKey<Level> dimension;
        private VerticalRange playableRange;
        private SurfaceAnchor surfaceAnchor;
        private RemapStrategy oreStrategy;
        private RemapStrategy structureStrategy;
        private RemapStrategy mobSpawnStrategy;
        private SpatialPredicate defaultStructurePredicate;
        private Map<ResourceKey<Structure>, SpatialPredicate> structurePredicates = Map.of();
        private BiomeSelection appliesTo = BiomeSelection.EMPTY;
        private Exclusions exclusions = Exclusions.EMPTY;
        private Map<MobCategory, RemapStrategy> mobSpawnStrategyByCategory = Map.of();
        private Additions additions = Additions.EMPTY;
        private AtmosphereOverride atmosphere = AtmosphereOverride.EMPTY;
        private ClientAtmosphereOverride clientAtmosphere = ClientAtmosphereOverride.EMPTY;
        private ContentOverrides contentOverrides = ContentOverrides.EMPTY;
        private int priority = DEFAULT_PRIORITY;

        private Builder() {}

        public Builder dimension(ResourceKey<Level> v) { this.dimension = v; return this; }
        public Builder playableRange(VerticalRange v) { this.playableRange = v; return this; }
        public Builder surfaceAnchor(SurfaceAnchor v) { this.surfaceAnchor = v; return this; }
        public Builder oreStrategy(RemapStrategy v) { this.oreStrategy = v; return this; }
        public Builder structureStrategy(RemapStrategy v) { this.structureStrategy = v; return this; }
        public Builder mobSpawnStrategy(RemapStrategy v) { this.mobSpawnStrategy = v; return this; }
        public Builder defaultStructurePredicate(SpatialPredicate v) { this.defaultStructurePredicate = v; return this; }
        public Builder structurePredicates(Map<ResourceKey<Structure>, SpatialPredicate> v) { this.structurePredicates = v; return this; }
        public Builder appliesTo(BiomeSelection v) { this.appliesTo = v; return this; }
        /** Convenience: wrap a key set as a {@link BiomeSelection} with no tags. */
        public Builder appliesTo(Set<ResourceKey<Biome>> keys) { this.appliesTo = BiomeSelection.ofKeys(keys); return this; }
        public Builder exclusions(Exclusions v) { this.exclusions = v; return this; }
        public Builder mobSpawnStrategyByCategory(Map<MobCategory, RemapStrategy> v) { this.mobSpawnStrategyByCategory = v; return this; }
        public Builder additions(Additions v) { this.additions = v; return this; }
        public Builder atmosphere(AtmosphereOverride v) { this.atmosphere = v; return this; }
        public Builder clientAtmosphere(ClientAtmosphereOverride v) { this.clientAtmosphere = v; return this; }
        public Builder contentOverrides(ContentOverrides v) { this.contentOverrides = v; return this; }
        public Builder priority(int v) { this.priority = v; return this; }

        public WorldshapeDescriptor build() {
            if (dimension == null) throw new IllegalStateException("dimension is required");
            if (playableRange == null) throw new IllegalStateException("playableRange is required");
            if (surfaceAnchor == null) throw new IllegalStateException("surfaceAnchor is required");
            if (oreStrategy == null) throw new IllegalStateException("oreStrategy is required");
            if (structureStrategy == null) throw new IllegalStateException("structureStrategy is required");
            if (mobSpawnStrategy == null) throw new IllegalStateException("mobSpawnStrategy is required");
            if (defaultStructurePredicate == null) throw new IllegalStateException("defaultStructurePredicate is required");
            return new WorldshapeDescriptor(
                    dimension, playableRange, surfaceAnchor,
                    oreStrategy, structureStrategy, mobSpawnStrategy,
                    structurePredicates, defaultStructurePredicate,
                    appliesTo, exclusions, mobSpawnStrategyByCategory,
                    additions, atmosphere, clientAtmosphere, contentOverrides, priority);
        }
    }

    /**
     * Full descriptor codec. Optional fields default per the builder pattern:
     * <ul>
     *   <li>{@code applies_to} omitted = empty set (descriptor matches no biome — explicit opt-in
     *       required since {@code BiomeModifier} has no dimension scope; "empty = all" would
     *       silently apply across every dimension that reuses the matching biomes)</li>
     *   <li>{@code exclusions} omitted = {@link Exclusions#EMPTY}</li>
     *   <li>{@code additions} omitted = {@link Additions#EMPTY}</li>
     *   <li>{@code atmosphere} omitted = {@link AtmosphereOverride#EMPTY}</li>
     *   <li>{@code structure_predicates} omitted = empty map (defer to default predicate)</li>
     *   <li>{@code mob_spawn_strategy_by_category} omitted = empty map</li>
     *   <li>{@code priority} omitted = {@link #DEFAULT_PRIORITY}</li>
     * </ul>
     */
    public static final Codec<WorldshapeDescriptor> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .forGetter(WorldshapeDescriptor::dimension),
            VerticalRange.CODEC.fieldOf("playable_range")
                    .forGetter(WorldshapeDescriptor::playableRange),
            SurfaceAnchor.CODEC.fieldOf("surface_anchor")
                    .forGetter(WorldshapeDescriptor::surfaceAnchor),
            RemapStrategy.CODEC.fieldOf("ore_strategy")
                    .forGetter(WorldshapeDescriptor::oreStrategy),
            RemapStrategy.CODEC.fieldOf("structure_strategy")
                    .forGetter(WorldshapeDescriptor::structureStrategy),
            RemapStrategy.CODEC.fieldOf("mob_spawn_strategy")
                    .forGetter(WorldshapeDescriptor::mobSpawnStrategy),
            Codec.unboundedMap(ResourceKey.codec(Registries.STRUCTURE), SpatialPredicate.CODEC)
                    .optionalFieldOf("structure_predicates", Map.of())
                    .forGetter(d -> new LinkedHashMap<>(d.structurePredicates())),
            SpatialPredicate.CODEC.fieldOf("default_structure_predicate")
                    .forGetter(WorldshapeDescriptor::defaultStructurePredicate),
            BiomeSelection.CODEC.optionalFieldOf("applies_to", BiomeSelection.EMPTY)
                    .forGetter(WorldshapeDescriptor::appliesTo),
            Exclusions.CODEC.optionalFieldOf("exclusions", Exclusions.EMPTY)
                    .forGetter(WorldshapeDescriptor::exclusions),
            Codec.unboundedMap(MobCategory.CODEC, RemapStrategy.CODEC)
                    .optionalFieldOf("mob_spawn_strategy_by_category", Map.of())
                    .forGetter(WorldshapeDescriptor::mobSpawnStrategyByCategory),
            Additions.CODEC.optionalFieldOf("additions", Additions.EMPTY)
                    .forGetter(WorldshapeDescriptor::additions),
            AtmosphereOverride.CODEC.optionalFieldOf("atmosphere", AtmosphereOverride.EMPTY)
                    .forGetter(WorldshapeDescriptor::atmosphere),
            ClientAtmosphereOverride.CODEC.optionalFieldOf("client_atmosphere", ClientAtmosphereOverride.EMPTY)
                    .forGetter(WorldshapeDescriptor::clientAtmosphere),
            ContentOverrides.CODEC.optionalFieldOf("content_overrides", ContentOverrides.EMPTY)
                    .forGetter(WorldshapeDescriptor::contentOverrides),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY)
                    .forGetter(WorldshapeDescriptor::priority)
    ).apply(i, WorldshapeDescriptor::new));

    /**
     * Optional {@code Set<ResourceKey<T>>} field codec — decodes a JSON list of registry
     * IDs into a {@link Set}, encodes back as a deterministically-sorted list so JSON
     * round-trips are byte-stable. Defaults to {@link Set#of() empty} when the field is
     * omitted.
     */
    private static <T> com.mojang.serialization.MapCodec<Set<ResourceKey<T>>> resourceKeySetCodec(
            ResourceKey<? extends net.minecraft.core.Registry<T>> registry, String fieldName) {
        return ResourceKey.codec(registry).listOf()
                .optionalFieldOf(fieldName, List.of())
                .xmap(Set::copyOf,
                      set -> set.stream()
                              .sorted(Comparator.comparing(k -> k.location().toString()))
                              .toList());
    }

    /**
     * Optional {@code Set<EntityType<?>>} field codec mirroring
     * {@link #resourceKeySetCodec} but keyed by entity-type name rather than
     * {@link ResourceKey}.
     */
    private static com.mojang.serialization.MapCodec<Set<EntityType<?>>> entityTypeSetCodec(String fieldName) {
        return BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf()
                .optionalFieldOf(fieldName, List.of())
                .xmap(Set::copyOf,
                      set -> set.stream()
                              .sorted(Comparator.comparing(t -> EntityType.getKey(t).toString()))
                              .toList());
    }
}
