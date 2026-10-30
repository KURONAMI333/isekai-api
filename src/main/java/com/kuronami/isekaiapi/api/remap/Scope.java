package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.List;

/**
 * Where a worldshape applies: one dimension key, optionally narrowed to a set of
 * {@code dimension_type}s.
 *
 * <p>Two worldshapes can share a dimension key while describing different worlds — a
 * selectable world type ships its own {@code LevelStem}, so {@code minecraft:overworld} in
 * that world type is not {@code minecraft:overworld} in vanilla. {@code requiredDimensionTypes}
 * tells the API which types the descriptor was written for; a level whose type is not in the
 * list is left alone.
 *
 * <p>JSON accepts both spellings so existing packs keep loading:
 * <pre>{@code
 * "dimension": "minecraft:overworld"
 * "dimension": { "dimension": "minecraft:overworld",
 *                "required_dimension_types": ["sky_world:sky"] }
 * }</pre>
 * An empty list means "no restriction" — the descriptor applies to every world type that has
 * this dimension key, which is the pre-3.0.0 behaviour.
 *
 * @since 3.0.0
 */
public record Scope(
        ResourceKey<Level> dimension,
        List<ResourceKey<DimensionType>> requiredDimensionTypes
) {
    public Scope {
        requiredDimensionTypes = List.copyOf(requiredDimensionTypes);
    }

    /** Unrestricted scope for {@code dimension}. */
    public static Scope of(ResourceKey<Level> dimension) {
        return new Scope(dimension, List.of());
    }

    /** Scope narrowed to one dimension type. */
    public static Scope of(ResourceKey<Level> dimension, ResourceKey<DimensionType> type) {
        return new Scope(dimension, List.of(type));
    }

    /** {@code true} when the descriptor applies in a level of the given dimension type. */
    public boolean accepts(ResourceKey<DimensionType> type) {
        return requiredDimensionTypes.isEmpty() || requiredDimensionTypes.contains(type);
    }

    /** {@code true} when no dimension type restriction is declared. */
    public boolean isUnrestricted() {
        return requiredDimensionTypes.isEmpty();
    }

    public static final Codec<Scope> CODEC = Codec.either(
            ResourceKey.codec(Registries.DIMENSION).xmap(Scope::of, Scope::dimension),
            RecordCodecBuilder.<Scope>create(i -> i.group(
                    ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                            .forGetter(Scope::dimension),
                    ResourceKey.codec(Registries.DIMENSION_TYPE).listOf()
                            .optionalFieldOf("required_dimension_types", List.of())
                            .forGetter(Scope::requiredDimensionTypes)
            ).apply(i, Scope::new))
    ).xmap(
            either -> either.map(scope -> scope, scope -> scope),
            scope -> com.mojang.datafixers.util.Either.right(scope)
    );
}
