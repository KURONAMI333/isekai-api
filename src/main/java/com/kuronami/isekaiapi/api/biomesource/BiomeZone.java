package com.kuronami.isekaiapi.api.biomesource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A neutral spatial condition for biome placement, mirroring the philosophy of
 * {@link com.kuronami.isekaiapi.api.predicate.SpatialPredicate} but evaluated at biome-grid
 * resolution (quart positions, i.e. one sample per 4 blocks) with no world context — only
 * the (x, y, z) coordinate is available, because biome assignment happens before terrain.
 *
 * <p>It is a separate type from {@link com.kuronami.isekaiapi.api.predicate.SpatialPredicate}
 * by necessity: {@code SpatialPredicate}'s terrain-probing variants ({@code SolidFloor},
 * {@code NearBlock}, {@code TerrainSlope}, {@code InFluid}) are meaningless before terrain
 * exists, so they cannot be reused here. {@code BiomeZone} exposes only the conditions that
 * are well-defined at biome-assignment time (pure coordinate geometry). It deliberately
 * matches {@code SpatialPredicate}'s dispatch idiom ({@link #typeId()} + {@code "type"}-keyed
 * {@link #CODEC}) so there is one dispatch convention across the library.
 *
 * <p>Used by {@code isekai_api:rule} (see
 * {@link com.kuronami.isekaiapi.biomesource.RuleBiomeSource}) to decide which biome a
 * position belongs to. Conditions are evaluated in declaration order; the first matching
 * entry's biome wins. This makes arbitrary biome distributions expressible from datapack:
 * vertical layering, concentric rings, half-and-half splits, etc.
 *
 * <p>Coordinates in the JSON are <b>block coordinates</b> for author convenience; the
 * evaluator converts the quart coordinates it receives to blocks before testing.
 *
 * <p>Dispatch by {@code "type"}:
 * <ul>
 *   <li>{@code isekai:always} — matches everywhere (use as the catch-all last entry).</li>
 *   <li>{@code isekai:y_above} {@code {y}} — block Y &ge; y.</li>
 *   <li>{@code isekai:y_below} {@code {y}} — block Y &lt; y.</li>
 *   <li>{@code isekai:y_between} {@code {min, max}} — min &le; block Y &lt; max.</li>
 *   <li>{@code isekai:within_distance} {@code {radius, [center_x], [center_z]}} — XZ
 *       distance from center (default origin) &le; radius.</li>
 *   <li>{@code isekai:beyond_distance} {@code {radius, [center_x], [center_z]}} — XZ
 *       distance &gt; radius.</li>
 *   <li>{@code isekai:and} {@code {all: [...]}} / {@code isekai:or} {@code {any: [...]}} /
 *       {@code isekai:not} {@code {inner}} — combinators.</li>
 *   <li>{@code isekai:noise_threshold} {@code {noise, seed?, threshold?, size_xz?, size_y?}}
 *       — true where a deterministic noise sample exceeds {@code threshold}; lets datapack
 *       authors lay biomes by an organic noise mask rather than geometric shape.</li>
 *   <li>{@code isekai:edge_jitter} {@code {inner, noise, seed?, strength?, size_xz?}} —
 *       perturbs the test coordinate by a small noise offset before delegating to {@code inner},
 *       so geometric borders (cylinders, half-planes, y-bands) get wavy, natural-looking
 *       boundaries without changing the inner zone's intent.</li>
 * </ul>
 */
public sealed interface BiomeZone {

    /**
     * Test this zone at a biome-grid position. {@code quartX/Y/Z} are quart coordinates
     * (block &gt;&gt; 2); implementations convert to block coordinates as needed.
     */
    boolean test(int quartX, int quartY, int quartZ);

    /** Stable type-id for dispatch. Namespaced under {@code isekai:}. */
    String typeId();

    /** This variant's payload codec (no {@code "type"} field). */
    MapCodec<? extends BiomeZone> codec();

    /** Polymorphic codec for any zone, dispatched by the {@code "type"} field. */
    Codec<BiomeZone> CODEC = Codec.lazyInitialized(BiomeZone::buildDispatchCodec);

    // --- variants ---

    record Always() implements BiomeZone {
        public static final MapCodec<Always> MAP_CODEC = MapCodec.unit(Always::new);
        @Override public boolean test(int x, int y, int z) { return true; }
        @Override public String typeId() { return "isekai:always"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record YAbove(int y) implements BiomeZone {
        public static final MapCodec<YAbove> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(YAbove::y)).apply(i, YAbove::new));
        @Override public boolean test(int x, int qy, int z) { return QuartPos.toBlock(qy) >= y; }
        @Override public String typeId() { return "isekai:y_above"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record YBelow(int y) implements BiomeZone {
        public static final MapCodec<YBelow> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(YBelow::y)).apply(i, YBelow::new));
        @Override public boolean test(int x, int qy, int z) { return QuartPos.toBlock(qy) < y; }
        @Override public String typeId() { return "isekai:y_below"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record YBetween(int min, int max) implements BiomeZone {
        public YBetween {
            if (min >= max) throw new IllegalArgumentException(
                    "y_between: min (" + min + ") must be < max (" + max + ")");
        }
        public static final MapCodec<YBetween> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(YBetween::min),
                Codec.INT.fieldOf("max").forGetter(YBetween::max)).apply(i, YBetween::new));
        @Override public boolean test(int x, int qy, int z) {
            int by = QuartPos.toBlock(qy);
            return by >= min && by < max;
        }
        @Override public String typeId() { return "isekai:y_between"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record WithinDistance(double radius, int centerX, int centerZ) implements BiomeZone {
        public WithinDistance {
            if (radius < 0) throw new IllegalArgumentException(
                    "within_distance: radius must be >= 0: " + radius);
        }
        public static final MapCodec<WithinDistance> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(WithinDistance::radius),
                Codec.INT.optionalFieldOf("center_x", 0).forGetter(WithinDistance::centerX),
                Codec.INT.optionalFieldOf("center_z", 0).forGetter(WithinDistance::centerZ))
                .apply(i, WithinDistance::new));
        @Override public boolean test(int qx, int y, int qz) {
            double dx = QuartPos.toBlock(qx) - centerX;
            double dz = QuartPos.toBlock(qz) - centerZ;
            return Math.sqrt(dx * dx + dz * dz) <= radius;
        }
        @Override public String typeId() { return "isekai:within_distance"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record BeyondDistance(double radius, int centerX, int centerZ) implements BiomeZone {
        public BeyondDistance {
            if (radius < 0) throw new IllegalArgumentException(
                    "beyond_distance: radius must be >= 0: " + radius);
        }
        public static final MapCodec<BeyondDistance> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.DOUBLE.fieldOf("radius").forGetter(BeyondDistance::radius),
                Codec.INT.optionalFieldOf("center_x", 0).forGetter(BeyondDistance::centerX),
                Codec.INT.optionalFieldOf("center_z", 0).forGetter(BeyondDistance::centerZ))
                .apply(i, BeyondDistance::new));
        @Override public boolean test(int qx, int y, int qz) {
            double dx = QuartPos.toBlock(qx) - centerX;
            double dz = QuartPos.toBlock(qz) - centerZ;
            return Math.sqrt(dx * dx + dz * dz) > radius;
        }
        @Override public String typeId() { return "isekai:beyond_distance"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record And(List<BiomeZone> all) implements BiomeZone {
        public And { all = List.copyOf(all); }
        public static final MapCodec<And> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("all").forGetter(And::all))
                .apply(i, And::new));
        @Override public boolean test(int x, int y, int z) {
            for (var c : all) if (!c.test(x, y, z)) return false;
            return true;
        }
        @Override public String typeId() { return "isekai:and"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record Or(List<BiomeZone> any) implements BiomeZone {
        public Or { any = List.copyOf(any); }
        public static final MapCodec<Or> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("any").forGetter(Or::any))
                .apply(i, Or::new));
        @Override public boolean test(int x, int y, int z) {
            for (var c : any) if (c.test(x, y, z)) return true;
            return false;
        }
        @Override public String typeId() { return "isekai:or"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record Not(BiomeZone inner) implements BiomeZone {
        public static final MapCodec<Not> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).fieldOf("inner").forGetter(Not::inner))
                .apply(i, Not::new));
        @Override public boolean test(int x, int y, int z) { return !inner.test(x, y, z); }
        @Override public String typeId() { return "isekai:not"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /**
     * Match where a {@link NormalNoise} sampled at the position exceeds {@code threshold}. The
     * noise is built once at zone construction from a {@link NoiseParameters} ref + a
     * {@code seed} (so the pattern is deterministic and independent of the world seed — by
     * design, since {@code BiomeZone} has no access to world context). Use to introduce
     * organic, non-geometric biome borders.
     *
     * <p>{@code size_xz} / {@code size_y} are 1/scale factors applied to the sampled block
     * coordinate — bigger values produce wider noise features.
     */
    record NoiseThreshold(Holder<NormalNoise.NoiseParameters> noise, long seed, double threshold,
                          double sizeXz, double sizeY, NormalNoise sampler) implements BiomeZone {
        public static final MapCodec<NoiseThreshold> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                NormalNoise.NoiseParameters.CODEC.fieldOf("noise").forGetter(NoiseThreshold::noise),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(NoiseThreshold::seed),
                Codec.DOUBLE.optionalFieldOf("threshold", 0.0).forGetter(NoiseThreshold::threshold),
                Codec.doubleRange(1.0, 1024.0).optionalFieldOf("size_xz", 64.0).forGetter(NoiseThreshold::sizeXz),
                Codec.doubleRange(1.0, 1024.0).optionalFieldOf("size_y", 64.0).forGetter(NoiseThreshold::sizeY))
                .apply(i, NoiseThreshold::fromConfig));
        public static NoiseThreshold fromConfig(Holder<NormalNoise.NoiseParameters> noise, long seed,
                                                double threshold, double sizeXz, double sizeY) {
            NormalNoise n = NormalNoise.create(RandomSource.create(seed), noise.value());
            return new NoiseThreshold(noise, seed, threshold, sizeXz, sizeY, n);
        }
        @Override public boolean test(int qx, int qy, int qz) {
            double x = QuartPos.toBlock(qx) / sizeXz;
            double y = QuartPos.toBlock(qy) / sizeY;
            double z = QuartPos.toBlock(qz) / sizeXz;
            return sampler.getValue(x, y, z) > threshold;
        }
        @Override public String typeId() { return "isekai:noise_threshold"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /**
     * Wrap an inner zone and perturb the test coordinate by a small noise offset before
     * delegating — turns geometric borders (cylinders, half-planes, y-bands) into wavy,
     * organic ones without changing the inner zone's intent. The {@code strength} is the
     * maximum block-distance the test position is shifted.
     *
     * <p>Like {@link NoiseThreshold} the jitter noise is deterministic from a fixed
     * {@code seed} (no world context available).
     */
    record EdgeJitter(BiomeZone inner, Holder<NormalNoise.NoiseParameters> noise, long seed,
                      double strength, double sizeXz, NormalNoise xSampler, NormalNoise zSampler)
            implements BiomeZone {
        public static final MapCodec<EdgeJitter> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).fieldOf("inner").forGetter(EdgeJitter::inner),
                NormalNoise.NoiseParameters.CODEC.fieldOf("noise").forGetter(EdgeJitter::noise),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(EdgeJitter::seed),
                Codec.doubleRange(0.0, 32.0).optionalFieldOf("strength", 4.0).forGetter(EdgeJitter::strength),
                Codec.doubleRange(1.0, 512.0).optionalFieldOf("size_xz", 32.0).forGetter(EdgeJitter::sizeXz))
                .apply(i, EdgeJitter::fromConfig));
        public static EdgeJitter fromConfig(BiomeZone inner, Holder<NormalNoise.NoiseParameters> noise,
                                            long seed, double strength, double sizeXz) {
            // Two independent samplers (different seed bits) so x-offset and z-offset are
            // decorrelated — otherwise both axes jitter together and the warp collapses to a
            // diagonal stretch instead of an organic ripple.
            NormalNoise nx = NormalNoise.create(RandomSource.create(seed), noise.value());
            NormalNoise nz = NormalNoise.create(RandomSource.create(seed ^ 0x9E3779B97F4A7C15L), noise.value());
            return new EdgeJitter(inner, noise, seed, strength, sizeXz, nx, nz);
        }
        @Override public boolean test(int qx, int qy, int qz) {
            double bx = QuartPos.toBlock(qx);
            double bz = QuartPos.toBlock(qz);
            double ox = xSampler.getValue(bx / sizeXz, 0.0, bz / sizeXz) * strength;
            double oz = zSampler.getValue(bx / sizeXz, 0.0, bz / sizeXz) * strength;
            int jqx = QuartPos.fromBlock((int) Math.round(bx + ox));
            int jqz = QuartPos.fromBlock((int) Math.round(bz + oz));
            return inner.test(jqx, qy, jqz);
        }
        @Override public String typeId() { return "isekai:edge_jitter"; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    // ---------------------------------------------------------------------
    // Dispatch wiring (matches SpatialPredicate's idiom exactly)
    // ---------------------------------------------------------------------

    /**
     * Build the dispatch codec lazily on first access. Registry insertion order is preserved
     * (LinkedHashMap) so error messages enumerate variants in declaration order.
     */
    private static Codec<BiomeZone> buildDispatchCodec() {
        Map<String, MapCodec<? extends BiomeZone>> registry = new LinkedHashMap<>();
        registry.put("isekai:always",          Always.MAP_CODEC);
        registry.put("isekai:y_above",         YAbove.MAP_CODEC);
        registry.put("isekai:y_below",         YBelow.MAP_CODEC);
        registry.put("isekai:y_between",       YBetween.MAP_CODEC);
        registry.put("isekai:within_distance", WithinDistance.MAP_CODEC);
        registry.put("isekai:beyond_distance", BeyondDistance.MAP_CODEC);
        registry.put("isekai:and",             And.MAP_CODEC);
        registry.put("isekai:or",              Or.MAP_CODEC);
        registry.put("isekai:not",             Not.MAP_CODEC);
        registry.put("isekai:noise_threshold", NoiseThreshold.MAP_CODEC);
        registry.put("isekai:edge_jitter",     EdgeJitter.MAP_CODEC);
        Map<String, MapCodec<? extends BiomeZone>> frozen = Map.copyOf(registry);

        return Codec.STRING.dispatch(
                "type",
                BiomeZone::typeId,
                typeId -> {
                    MapCodec<? extends BiomeZone> mc = frozen.get(typeId);
                    if (mc == null) {
                        throw new IllegalArgumentException(
                                "Unknown BiomeZone type: '" + typeId
                                        + "'. Known types: " + frozen.keySet());
                    }
                    return mc;
                });
    }
}
