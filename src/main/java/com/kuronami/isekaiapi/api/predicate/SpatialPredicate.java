package com.kuronami.isekaiapi.api.predicate;

import com.kuronami.isekaiapi.registry.IsekaiDispatch;
import com.kuronami.isekaiapi.registry.IsekaiSpiTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * Neutral spatial conditions for placement filtering. Combine via {@link And} / {@link Or} /
 * {@link Not} to express arbitrary placement constraints without committing to any specific
 * worldshape's vocabulary.
 *
 * <p><b>Extensible.</b> The built-in variants below are registered in the
 * {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries#SPATIAL_PREDICATE_TYPE} registry;
 * third parties add their own by registering a {@link MapCodec} under that key (see
 * {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries}). The {@link #CODEC} dispatches on
 * a {@code "type"} field, e.g. {@code {"type": "isekai_api:y_in_range", "min": 60, "max": 200}}.
 * The legacy {@code isekai:} prefix is accepted as a deprecated alias.
 *
 * <p><b>Implementing a variant.</b> Implementations must be immutable (records are recommended),
 * return their registered codec from {@link #codec()}, evaluate themselves against a world via
 * {@link #test(EvaluationContext)}, and expose any nested predicates via {@link #children()} so
 * validators can walk the tree.
 *
 * @since 2.0.0 open for third-party extension via the registry (was a sealed interface before).
 */
public interface SpatialPredicate {

    /** This variant's payload codec (no {@code "type"} field); must be the instance registered
     *  under {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries#SPATIAL_PREDICATE_TYPE}. */
    MapCodec<? extends SpatialPredicate> codec();

    /** Evaluate this predicate against a world context. @since 2.0.0 */
    boolean test(EvaluationContext ctx);

    /** Nested predicates, for tree-walking validators. Empty for leaf variants. @since 2.0.0 */
    default List<SpatialPredicate> children() { return List.of(); }

    /** Dispatching codec keyed on a {@code "type"} field, backed by the SpatialPredicate registry. */
    Codec<SpatialPredicate> CODEC = IsekaiDispatch.dispatchCodec(
            IsekaiSpiTypes.SPATIAL_PREDICATE_REGISTRY, SpatialPredicate::codec, "SpatialPredicate");

    // ---------------------------------------------------------------------
    // Leaf variants
    // ---------------------------------------------------------------------

    /** Y coordinate must fall within [min, max] (inclusive). */
    record YInRange(int min, int max) implements SpatialPredicate {
        public static final MapCodec<YInRange> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(YInRange::min),
                Codec.INT.fieldOf("max").forGetter(YInRange::max)
        ).apply(i, YInRange::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) {
            int y = ctx.pos().getY();
            return y >= min && y <= max;
        }
    }

    /** Block has solid ground beneath and at least {@code minClearance} blocks of empty space above. */
    record SolidFloor(int minClearance) implements SpatialPredicate {
        public static final MapCodec<SolidFloor> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_clearance").forGetter(SolidFloor::minClearance)
        ).apply(i, SolidFloor::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return ctx.solidFloor(minClearance); }
    }

    /** Block has solid ceiling above and at least {@code minClearance} blocks of empty space below. */
    record SolidCeiling(int minClearance) implements SpatialPredicate {
        public static final MapCodec<SolidCeiling> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_clearance").forGetter(SolidCeiling::minClearance)
        ).apply(i, SolidCeiling::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return ctx.solidCeiling(minClearance); }
    }

    /** Local terrain slope falls within [minSlope, maxSlope]. 0 = flat, 1 = 45deg. */
    record TerrainSlope(double minSlope, double maxSlope) implements SpatialPredicate {
        public static final MapCodec<TerrainSlope> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("min_slope").forGetter(TerrainSlope::minSlope),
                Codec.DOUBLE.fieldOf("max_slope").forGetter(TerrainSlope::maxSlope)
        ).apply(i, TerrainSlope::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return ctx.terrainSlope(minSlope, maxSlope); }
    }

    /**
     * Block matches any entry in {@code targets} within {@code maxDistance}. {@code targets}
     * accepts the standard {@link HolderSet} JSON shapes: a single block id, a list of
     * block ids, or a tag reference like {@code "#minecraft:logs"}.
     */
    record NearBlock(HolderSet<Block> targets, int maxDistance) implements SpatialPredicate {
        public static final MapCodec<NearBlock> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("targets").forGetter(NearBlock::targets),
                Codec.INT.fieldOf("max_distance").forGetter(NearBlock::maxDistance)
        ).apply(i, NearBlock::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return ctx.nearBlock(targets, maxDistance); }
    }

    /** Position is within {@code maxDistance} of a chunk whose biome key matches. */
    record NearBiome(ResourceKey<Biome> biome, int maxDistance) implements SpatialPredicate {
        public static final MapCodec<NearBiome> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(NearBiome::biome),
                Codec.INT.fieldOf("max_distance").forGetter(NearBiome::maxDistance)
        ).apply(i, NearBiome::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return ctx.nearBiome(biome, maxDistance); }
    }

    /** Position is inside the specified fluid. */
    record InFluid(Fluid fluid) implements SpatialPredicate {
        public static final MapCodec<InFluid> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(InFluid::fluid)
        ).apply(i, InFluid::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return ctx.inFluid(fluid); }
    }

    /** Always true. */
    record Always() implements SpatialPredicate {
        public static final Always INSTANCE = new Always();
        public static final MapCodec<Always> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return true; }
    }

    /** Always false. */
    record Never() implements SpatialPredicate {
        public static final Never INSTANCE = new Never();
        public static final MapCodec<Never> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return false; }
    }

    // ---------------------------------------------------------------------
    // Combinators (recursive — resolve children via the dispatch CODEC)
    // ---------------------------------------------------------------------

    /** All sub-predicates must hold. */
    record And(List<SpatialPredicate> all) implements SpatialPredicate {
        public And { all = List.copyOf(all); }
        public static final MapCodec<And> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("all").forGetter(And::all)
        ).apply(i, And::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) {
            for (SpatialPredicate child : all) if (!child.test(ctx)) return false;
            return true;
        }
        @Override public List<SpatialPredicate> children() { return all; }
    }

    /** Any sub-predicate holds. */
    record Or(List<SpatialPredicate> any) implements SpatialPredicate {
        public Or { any = List.copyOf(any); }
        public static final MapCodec<Or> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("any").forGetter(Or::any)
        ).apply(i, Or::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) {
            for (SpatialPredicate child : any) if (child.test(ctx)) return true;
            return false;
        }
        @Override public List<SpatialPredicate> children() { return any; }
    }

    /** Negation of inner predicate. */
    record Not(SpatialPredicate inner) implements SpatialPredicate {
        public static final MapCodec<Not> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).fieldOf("inner").forGetter(Not::inner)
        ).apply(i, Not::new));

        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
        @Override public boolean test(EvaluationContext ctx) { return !inner.test(ctx); }
        @Override public List<SpatialPredicate> children() { return List.of(inner); }
    }
}
