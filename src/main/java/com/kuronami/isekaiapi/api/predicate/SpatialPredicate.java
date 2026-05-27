package com.kuronami.isekaiapi.api.predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neutral spatial conditions for placement filtering. Combine via {@link And} / {@link Or} /
 * {@link Not} to express arbitrary placement constraints without committing to any specific
 * worldshape's vocabulary.
 *
 * <p>Each variant carries a stable {@link #typeId()} used as the discriminator in the JSON
 * representation, e.g. {@code {"type": "isekai:y_in_range", "min": 60, "max": 200}}.
 *
 * <p>The {@link #CODEC} field dispatches on the {@code "type"} key; consumers serialize
 * descriptors via {@code SpatialPredicate.CODEC.encodeStart(...)} or decode from datapack JSON
 * via {@code SpatialPredicate.CODEC.parse(...)}.
 */
public sealed interface SpatialPredicate {

    /** Stable type-id for dispatch. Namespaced under {@code isekai:}. */
    String typeId();

    /** This variant's payload codec (no {@code "type"} field). */
    MapCodec<? extends SpatialPredicate> codec();

    /** Dispatching codec keyed on a {@code "type"} field. */
    Codec<SpatialPredicate> CODEC = Codec.lazyInitialized(SpatialPredicate::buildDispatchCodec);

    // ---------------------------------------------------------------------
    // Leaf variants
    // ---------------------------------------------------------------------

    /** Y coordinate must fall within [min, max] (inclusive). */
    record YInRange(int min, int max) implements SpatialPredicate {
        public static final MapCodec<YInRange> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(YInRange::min),
                Codec.INT.fieldOf("max").forGetter(YInRange::max)
        ).apply(i, YInRange::new));

        @Override public String typeId() { return "isekai:y_in_range"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Block has solid ground beneath and at least {@code minClearance} blocks of empty space above. */
    record SolidFloor(int minClearance) implements SpatialPredicate {
        public static final MapCodec<SolidFloor> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_clearance").forGetter(SolidFloor::minClearance)
        ).apply(i, SolidFloor::new));

        @Override public String typeId() { return "isekai:solid_floor"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Block has solid ceiling above and at least {@code minClearance} blocks of empty space below. */
    record SolidCeiling(int minClearance) implements SpatialPredicate {
        public static final MapCodec<SolidCeiling> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_clearance").forGetter(SolidCeiling::minClearance)
        ).apply(i, SolidCeiling::new));

        @Override public String typeId() { return "isekai:solid_ceiling"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Local terrain slope falls within [minSlope, maxSlope]. 0 = flat, 1 = 45deg. */
    record TerrainSlope(double minSlope, double maxSlope) implements SpatialPredicate {
        public static final MapCodec<TerrainSlope> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("min_slope").forGetter(TerrainSlope::minSlope),
                Codec.DOUBLE.fieldOf("max_slope").forGetter(TerrainSlope::maxSlope)
        ).apply(i, TerrainSlope::new));

        @Override public String typeId() { return "isekai:terrain_slope"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Block matches {@code target} within {@code maxDistance}. */
    record NearBlock(Block target, int maxDistance) implements SpatialPredicate {
        public static final MapCodec<NearBlock> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("target").forGetter(NearBlock::target),
                Codec.INT.fieldOf("max_distance").forGetter(NearBlock::maxDistance)
        ).apply(i, NearBlock::new));

        @Override public String typeId() { return "isekai:near_block"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Position is within {@code maxDistance} of a chunk whose biome key matches. */
    record NearBiome(ResourceKey<Biome> biome, int maxDistance) implements SpatialPredicate {
        public static final MapCodec<NearBiome> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(NearBiome::biome),
                Codec.INT.fieldOf("max_distance").forGetter(NearBiome::maxDistance)
        ).apply(i, NearBiome::new));

        @Override public String typeId() { return "isekai:near_biome"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Position is inside the specified fluid. */
    record InFluid(Fluid fluid) implements SpatialPredicate {
        public static final MapCodec<InFluid> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(InFluid::fluid)
        ).apply(i, InFluid::new));

        @Override public String typeId() { return "isekai:in_fluid"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Always true. */
    record Always() implements SpatialPredicate {
        public static final Always INSTANCE = new Always();
        public static final MapCodec<Always> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public String typeId() { return "isekai:always"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Always false. */
    record Never() implements SpatialPredicate {
        public static final Never INSTANCE = new Never();
        public static final MapCodec<Never> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public String typeId() { return "isekai:never"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
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

        @Override public String typeId() { return "isekai:and"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Any sub-predicate holds. */
    record Or(List<SpatialPredicate> any) implements SpatialPredicate {
        public Or { any = List.copyOf(any); }
        public static final MapCodec<Or> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("any").forGetter(Or::any)
        ).apply(i, Or::new));

        @Override public String typeId() { return "isekai:or"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    /** Negation of inner predicate. */
    record Not(SpatialPredicate inner) implements SpatialPredicate {
        public static final MapCodec<Not> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).fieldOf("inner").forGetter(Not::inner)
        ).apply(i, Not::new));

        @Override public String typeId() { return "isekai:not"; }
        @Override public MapCodec<? extends SpatialPredicate> codec() { return MAP_CODEC; }
    }

    // ---------------------------------------------------------------------
    // Dispatch wiring
    // ---------------------------------------------------------------------

    /**
     * Build the dispatch codec lazily on first access. Registry insertion order is preserved
     * (LinkedHashMap) so error messages and tooling enumerate variants in declaration order.
     */
    private static Codec<SpatialPredicate> buildDispatchCodec() {
        Map<String, MapCodec<? extends SpatialPredicate>> registry = new LinkedHashMap<>();
        registry.put("isekai:y_in_range",    YInRange.MAP_CODEC);
        registry.put("isekai:solid_floor",   SolidFloor.MAP_CODEC);
        registry.put("isekai:solid_ceiling", SolidCeiling.MAP_CODEC);
        registry.put("isekai:terrain_slope", TerrainSlope.MAP_CODEC);
        registry.put("isekai:near_block",    NearBlock.MAP_CODEC);
        registry.put("isekai:near_biome",    NearBiome.MAP_CODEC);
        registry.put("isekai:in_fluid",      InFluid.MAP_CODEC);
        registry.put("isekai:always",        Always.MAP_CODEC);
        registry.put("isekai:never",         Never.MAP_CODEC);
        registry.put("isekai:and",           And.MAP_CODEC);
        registry.put("isekai:or",            Or.MAP_CODEC);
        registry.put("isekai:not",           Not.MAP_CODEC);
        Map<String, MapCodec<? extends SpatialPredicate>> frozen = Map.copyOf(registry);

        return Codec.STRING.dispatch(
                "type",
                SpatialPredicate::typeId,
                typeId -> {
                    MapCodec<? extends SpatialPredicate> mc = frozen.get(typeId);
                    if (mc == null) {
                        throw new IllegalArgumentException(
                                "Unknown SpatialPredicate type: '" + typeId
                                        + "'. Known types: " + frozen.keySet());
                    }
                    return mc;
                });
    }
}
