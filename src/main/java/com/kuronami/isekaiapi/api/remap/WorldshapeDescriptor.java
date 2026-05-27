package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashSet;
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
 * {@code RecordCodecBuilder} while keeping the public API self-documenting.
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
        Set<ResourceKey<Biome>> appliesTo,
        Exclusions exclusions,
        Map<MobCategory, RemapStrategy> mobSpawnStrategyByCategory,
        Additions additions,
        AtmosphereOverride atmosphere,
        int priority
) {
    public WorldshapeDescriptor {
        structurePredicates = Map.copyOf(structurePredicates);
        appliesTo = Set.copyOf(appliesTo);
        mobSpawnStrategyByCategory = Map.copyOf(mobSpawnStrategyByCategory);
        if (exclusions == null) exclusions = Exclusions.EMPTY;
        if (additions == null) additions = Additions.EMPTY;
        if (atmosphere == null) atmosphere = AtmosphereOverride.EMPTY;
    }

    /** Set of registry keys to drop from matched biomes during the REMOVE phase. */
    public record Exclusions(
            Set<ResourceKey<PlacedFeature>> features,
            Set<ResourceKey<Structure>> structures,
            Set<ResourceKey<ConfiguredWorldCarver<?>>> carvers
    ) {
        public Exclusions {
            features = Set.copyOf(features);
            structures = Set.copyOf(structures);
            carvers = Set.copyOf(carvers);
        }

        public static final Exclusions EMPTY = new Exclusions(Set.of(), Set.of(), Set.of());

        public static final Codec<Exclusions> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.PLACED_FEATURE).listOf()
                        .optionalFieldOf("features", List.of())
                        .xmap(list -> (Set<ResourceKey<PlacedFeature>>) new HashSet<>(list),
                              set -> List.copyOf(set))
                        .forGetter(Exclusions::features),
                ResourceKey.codec(Registries.STRUCTURE).listOf()
                        .optionalFieldOf("structures", List.of())
                        .xmap(list -> (Set<ResourceKey<Structure>>) new HashSet<>(list),
                              set -> List.copyOf(set))
                        .forGetter(Exclusions::structures),
                ResourceKey.codec(Registries.CONFIGURED_CARVER).listOf()
                        .optionalFieldOf("carvers", List.of())
                        .xmap(list -> (Set<ResourceKey<ConfiguredWorldCarver<?>>>) new HashSet<>(list),
                              set -> List.copyOf(set))
                        .forGetter(Exclusions::carvers)
        ).apply(i, Exclusions::new));
    }

    /** Lists of consumer-injected entries to add during the ADD phase. */
    public record Additions(
            List<AdditionalFeature> features,
            List<AdditionalCarver> carvers
    ) {
        public Additions {
            features = List.copyOf(features);
            carvers = List.copyOf(carvers);
        }

        public static final Additions EMPTY = new Additions(List.of(), List.of());

        public static final Codec<Additions> CODEC = RecordCodecBuilder.create(i -> i.group(
                AdditionalFeature.CODEC.listOf().optionalFieldOf("features", List.of())
                        .forGetter(Additions::features),
                AdditionalCarver.CODEC.listOf().optionalFieldOf("carvers", List.of())
                        .forGetter(Additions::carvers)
        ).apply(i, Additions::new));
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

    // Convenience accessors so consumers don't always have to write d.exclusions().features()
    public Set<ResourceKey<PlacedFeature>> excludedFeatures() { return exclusions.features(); }
    public Set<ResourceKey<Structure>> excludedStructures() { return exclusions.structures(); }
    public Set<ResourceKey<ConfiguredWorldCarver<?>>> excludedCarvers() { return exclusions.carvers(); }
    public List<AdditionalFeature> additionalFeatures() { return additions.features(); }
    public List<AdditionalCarver> additionalCarvers() { return additions.carvers(); }

    public static final int DEFAULT_PRIORITY = 100;

    /**
     * Full descriptor codec. Optional fields default per the builder pattern:
     * <ul>
     *   <li>{@code applies_to} omitted = empty set (descriptor applies dimension-wide)</li>
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
            ResourceKey.codec(Registries.BIOME).listOf()
                    .optionalFieldOf("applies_to", List.of())
                    .xmap(list -> (Set<ResourceKey<Biome>>) new HashSet<>(list),
                          set -> List.copyOf(set))
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
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY)
                    .forGetter(WorldshapeDescriptor::priority)
    ).apply(i, WorldshapeDescriptor::new));
}
