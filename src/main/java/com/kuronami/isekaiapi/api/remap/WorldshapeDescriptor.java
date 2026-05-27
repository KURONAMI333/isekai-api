package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.Structure;

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
}
