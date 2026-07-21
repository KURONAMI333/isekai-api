package com.kuronami.isekaiapi.api.biomesource;

import com.kuronami.isekaiapi.registry.IsekaiDispatch;
import com.kuronami.isekaiapi.registry.IsekaiSpiTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;

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
 * are well-defined at biome-assignment time (pure coordinate geometry).
 *
 * <p><b>Extensible.</b> Built-in variants are registered in
 * {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries#BIOME_ZONE_TYPE}; third parties add
 * their own by registering a {@link MapCodec} under that key. The {@link #CODEC} dispatches on a
 * {@code "type"} field, e.g. {@code {"type": "isekai_api:y_above", "y": 64}}. The legacy
 * {@code isekai:} prefix is accepted as a deprecated alias.
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
 * <p>Built-in variants dispatched by {@code "type"}:
 * <ul>
 *   <li>{@code always} — matches everywhere (use as the catch-all last entry).</li>
 *   <li>{@code y_above} {@code {y}} — block Y &ge; y.</li>
 *   <li>{@code y_below} {@code {y}} — block Y &lt; y.</li>
 *   <li>{@code y_between} {@code {min, max}} — min &le; block Y &lt; max.</li>
 *   <li>{@code within_distance} {@code {radius, [center_x], [center_z]}} — XZ distance &le; radius.</li>
 *   <li>{@code beyond_distance} {@code {radius, [center_x], [center_z]}} — XZ distance &gt; radius.</li>
 *   <li>{@code and} {@code {all: [...]}} / {@code or} {@code {any: [...]}} / {@code not} {@code {inner}} — combinators.</li>
 *   <li>{@code noise_threshold} — true where a deterministic noise sample exceeds {@code threshold}.</li>
 *   <li>{@code edge_jitter} — perturbs the test coordinate by a small noise offset before delegating to {@code inner}.</li>
 * </ul>
 *
 * @since 2.0.0 open for third-party extension via the registry (was a sealed interface before).
 */
public interface BiomeZone {

    /**
     * Test this zone at a biome-grid position. {@code quartX/Y/Z} are quart coordinates
     * (block &gt;&gt; 2); implementations convert to block coordinates as needed.
     */
    boolean test(int quartX, int quartY, int quartZ);

    /** This variant's payload codec (no {@code "type"} field); must be the registered instance. */
    MapCodec<? extends BiomeZone> codec();

    /** Nested zones, for tree-walking. Empty for leaf variants. @since 2.0.0 */
    default List<BiomeZone> children() { return List.of(); }

    /** Dispatching codec keyed on a {@code "type"} field, backed by the BiomeZone registry. */
    Codec<BiomeZone> CODEC = IsekaiDispatch.dispatchCodec(
            IsekaiSpiTypes.BIOME_ZONE_REGISTRY, BiomeZone::codec, "BiomeZone");

    // --- variants ---

    record Always() implements BiomeZone {
        public static final MapCodec<Always> MAP_CODEC = MapCodec.unit(Always::new);
        @Override public boolean test(int x, int y, int z) { return true; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record YAbove(int y) implements BiomeZone {
        public static final MapCodec<YAbove> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(YAbove::y)).apply(i, YAbove::new));
        @Override public boolean test(int x, int qy, int z) { return QuartPos.toBlock(qy) >= y; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    record YBelow(int y) implements BiomeZone {
        public static final MapCodec<YBelow> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(YBelow::y)).apply(i, YBelow::new));
        @Override public boolean test(int x, int qy, int z) { return QuartPos.toBlock(qy) < y; }
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
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return all; }
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
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return any; }
    }

    record Not(BiomeZone inner) implements BiomeZone {
        public static final MapCodec<Not> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).fieldOf("inner").forGetter(Not::inner))
                .apply(i, Not::new));
        @Override public boolean test(int x, int y, int z) { return !inner.test(x, y, z); }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return List.of(inner); }
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
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return List.of(inner); }
    }
}
