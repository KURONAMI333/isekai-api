package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bundle of fine-grained per-biome content overrides. Grouped here to keep the parent
 * {@link WorldshapeDescriptor} record under the 16-field RecordCodecBuilder limit and to
 * cluster fields that all "modify how content gets placed in matched biomes" semantically.
 *
 * <ul>
 *   <li>{@link #featurePredicates} — wrap a placed feature's placement modifier chain
 *       with a {@link SpatialPredicate} so it only spawns where the condition holds
 *       (e.g. "lake only where there's solid floor with 3+ block clearance").</li>
 *   <li>{@link #structureSpawnOverrides} — replace or augment a structure's mob spawn
 *       entries (e.g. "no creepers in pillager outposts").</li>
 *   <li>{@link #blockOverrides} — override surface_top or default_block per biome
 *       (e.g. "moon dust surface in every biome of this world").</li>
 * </ul>
 *
 * <p>All three default to empty. JSON shape: {@code content_overrides: { feature_predicates:
 * {...}, structure_spawn_overrides: [...], block_overrides: {...} }}.
 */
public record ContentOverrides(
        Map<ResourceKey<PlacedFeature>, SpatialPredicate> featurePredicates,
        List<WorldshapeDescriptor.StructureSpawnConfig> structureSpawnOverrides,
        BlockOverrides blockOverrides
) {
    public ContentOverrides {
        featurePredicates = Map.copyOf(featurePredicates);
        structureSpawnOverrides = List.copyOf(structureSpawnOverrides);
        if (blockOverrides == null) blockOverrides = BlockOverrides.EMPTY;
    }

    public static final ContentOverrides EMPTY = new ContentOverrides(
            Map.of(), List.of(), BlockOverrides.EMPTY);

    public boolean isEmpty() {
        return featurePredicates.isEmpty()
                && structureSpawnOverrides.isEmpty()
                && blockOverrides.isEmpty();
    }

    public static final Codec<ContentOverrides> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceKey.codec(Registries.PLACED_FEATURE), SpatialPredicate.CODEC)
                    .optionalFieldOf("feature_predicates", Map.of())
                    .forGetter(co -> new LinkedHashMap<>(co.featurePredicates())),
            WorldshapeDescriptor.StructureSpawnConfig.CODEC.listOf()
                    .optionalFieldOf("structure_spawn_overrides", List.of())
                    .forGetter(ContentOverrides::structureSpawnOverrides),
            BlockOverrides.CODEC.optionalFieldOf("block_overrides", BlockOverrides.EMPTY)
                    .forGetter(ContentOverrides::blockOverrides)
    ).apply(i, ContentOverrides::new));
}
