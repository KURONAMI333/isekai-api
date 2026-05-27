package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashSet;
import java.util.LinkedHashMap;
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
        Set<ResourceKey<Feature<?>>> excludedFeatures,
        int priority
) {
    public WorldshapeDescriptor {
        structurePredicates = Map.copyOf(structurePredicates);
        appliesTo = Set.copyOf(appliesTo);
        excludedFeatures = Set.copyOf(excludedFeatures);
    }

    public static final int DEFAULT_PRIORITY = 100;

    /**
     * Full descriptor codec. {@code structurePredicates} encodes as an unbounded map keyed
     * by structure registry id; {@code appliesTo} / {@code excludedFeatures} encode as lists
     * of registry ids (xmapped back to immutable sets). Optional fields default per the
     * builder pattern:
     * <ul>
     *   <li>{@code applies_to} omitted = empty set (descriptor applies dimension-wide)</li>
     *   <li>{@code excluded_features} omitted = empty set</li>
     *   <li>{@code structure_predicates} omitted = empty map (defer to default predicate)</li>
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
                    .optionalFieldOf("applies_to", java.util.List.of())
                    .xmap(list -> (Set<ResourceKey<Biome>>) new HashSet<>(list),
                          set -> java.util.List.copyOf(set))
                    .forGetter(WorldshapeDescriptor::appliesTo),
            ResourceKey.codec(Registries.FEATURE).listOf()
                    .optionalFieldOf("excluded_features", java.util.List.of())
                    .xmap(list -> (Set<ResourceKey<Feature<?>>>) new HashSet<>(list),
                          set -> java.util.List.copyOf(set))
                    .forGetter(WorldshapeDescriptor::excludedFeatures),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY)
                    .forGetter(WorldshapeDescriptor::priority)
    ).apply(i, WorldshapeDescriptor::new));
}
