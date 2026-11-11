package com.kuronami.isekaiapi.api.biomesource;

import com.kuronami.isekaiapi.registry.IsekaiDispatch;
import com.kuronami.isekaiapi.registry.IsekaiSpiTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;
import java.util.Optional;

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
 *   <li>{@code box} {@code {min_x, max_x, min_z, max_z, [min_y], [max_y]}} — block-coordinate box, both ends inclusive.</li>
 *   <li>{@code chunk_box} {@code {min_chunk_x, max_chunk_x, min_chunk_z, max_chunk_z}} — chunk-aligned box, full height.</li>
 *   <li>{@code and} {@code {all: [...]}} / {@code or} {@code {any: [...]}} / {@code not} {@code {inner}} — combinators.</li>
 *   <li>{@code noise_threshold} — true where a noise sample exceeds {@code threshold}.</li>
 *   <li>{@code edge_jitter} — perturbs the test coordinate by a small noise offset before delegating to {@code inner}.</li>
 *   <li>{@code base_biome} {@code {biome}} — the wrapped source's own answer at this position is one of {@code biome} (3.0.0).</li>
 *   <li>{@code climate} {@code {temperature?, humidity?, continentalness?, erosion?, weirdness?, depth?}} — the vanilla climate sample falls inside the given axes (3.0.0).</li>
 * </ul>
 *
 * <p><b>World state.</b> Every variant above is pure coordinate geometry; the last two need
 * something a coordinate cannot express and read it from {@link ZoneContext}, which only a
 * {@code BiomeSource} can fill in. Without one (a standalone {@link #test} call, or inside
 * {@code isekai_api:rule}) they answer false — see {@link ZoneContext} and
 * {@link #test(int, int, int, ZoneContext)}.
 *
 * <p><b>World seed.</b> Noise-backed variants take their randomness from the world seed combined
 * with the zone's own {@code seed} field, so the same datapack produces a different pattern in
 * every world and the same pattern for the same world seed. See {@link #withWorldSeed(long)}.
 *
 * @since 1.0.0
 */
public interface BiomeZone {

    /**
     * Test this zone at a biome-grid position. {@code quartX/Y/Z} are quart coordinates
     * (block &gt;&gt; 2); implementations convert to block coordinates as needed.
     * @since 1.0.0
     */
    boolean test(int quartX, int quartY, int quartZ);

    /**
     * Test this zone at a biome-grid position with the world state a source can supply — the
     * wrapped source's answer and the vanilla climate sample.
     *
     * <p>The biome source calls this one. The default delegates to the three-argument
     * {@link #test(int, int, int)}, so every variant that is pure geometry — including third-party
     * ones written before this method existed — keeps working and never sees the context. Only
     * {@link BaseBiome} and {@link ClimateMatch} read it, and only the combinators that wrap them
     * ({@link And}, {@link Or}, {@link Not}, {@link EdgeJitter}) forward it.
     *
     * <p>Calling the three-argument form directly is still a valid pure-geometry test; zones that
     * need context answer {@code false} there rather than throwing, because "no world state" is a
     * legitimate question to ask a zone.
     *
     * @param ctx the caller's world state; use {@link ZoneContext#NONE} when there is none
     * @since 3.0.0
     */
    default boolean test(int quartX, int quartY, int quartZ, ZoneContext ctx) {
        return test(quartX, quartY, quartZ);
    }

    /** This variant's payload codec (no {@code "type"} field); must be the registered instance. @since 1.0.0 */
    MapCodec<? extends BiomeZone> codec();

    /** Nested zones, for tree-walking. Empty for leaf variants. @since 2.0.0 */
    default List<BiomeZone> children() { return List.of(); }

    /**
     * Return a copy of this zone bound to a world seed, or {@code this} when the zone's result
     * does not depend on one.
     *
     * <p>{@link #test} receives only a coordinate, so a zone that wants world-seeded randomness
     * cannot obtain the seed at test time. Instead the biome source rebuilds its whole zone tree
     * once, when the level it belongs to is loaded, and evaluates the rebuilt tree from then on —
     * the per-sample path stays a plain {@code test(x, y, z)} with no lookup.
     *
     * <p>Contract for implementors:
     * <ul>
     *   <li>The returned zone must be equivalent to this one in every respect except the noise
     *       streams it derives from the world seed. In particular the codec fields must survive
     *       unchanged, so the rebuilt zone still encodes back to the JSON it came from.</li>
     *   <li>A zone with children must call {@code withWorldSeed} on each child and rebuild
     *       itself around the results, otherwise nested noise zones stay unseeded.</li>
     *   <li>Calling it again with a different seed must re-derive from the zone's own
     *       {@code seed} field, not from the previously derived value.</li>
     * </ul>
     *
     * <p>The default returns {@code this}, so purely geometric variants — including third-party
     * ones written before this method existed — need no change.
     *
     * @param worldSeed the seed of the level this zone will be evaluated in
     * @since 2.1.0
     */
    default BiomeZone withWorldSeed(long worldSeed) { return this; }

    /**
     * Combine a world seed with a zone-local {@code seed} field into the seed a noise-backed
     * variant actually samples with. Mirrors how vanilla hands out derived randomness from
     * {@code RandomState}: one level seed fans out into independent, reproducible streams.
     *
     * <p>Deterministic and injective in both arguments — two zones with different {@code seed}
     * fields get different patterns inside one world, and the same zone gets a different pattern
     * in every world. Third-party variants that implement {@link #withWorldSeed(long)} should
     * route their seed through this so they behave like the built-ins.
     *
     * @since 2.1.0
     */
    static long deriveSeed(long worldSeed, long zoneSeed) {
        // SplitMix64 finalizer over (worldSeed XOR odd-multiplied zoneSeed): the multiply is a
        // bijection, so distinct zoneSeeds never collide, and the finalizer decorrelates worlds
        // whose seeds differ by only a few bits.
        long z = worldSeed ^ (zoneSeed * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Whether a vanilla climate sample falls inside every axis given. An absent axis matches any
     * value, including samples past {@code ±1} (depth goes above 1 underground). Values are compared as the quantised longs the sampler produces, exactly as
     * vanilla's own multi-noise matching does.
     *
     * @since 3.0.0
     */
    static boolean climateMatches(Climate.TargetPoint sample,
                                  Optional<Climate.Parameter> temperature,
                                  Optional<Climate.Parameter> humidity,
                                  Optional<Climate.Parameter> continentalness,
                                  Optional<Climate.Parameter> erosion,
                                  Optional<Climate.Parameter> weirdness,
                                  Optional<Climate.Parameter> depth) {
        return containsAxis(temperature, sample.temperature())
                && containsAxis(humidity, sample.humidity())
                && containsAxis(continentalness, sample.continentalness())
                && containsAxis(erosion, sample.erosion())
                && containsAxis(weirdness, sample.weirdness())
                && containsAxis(depth, sample.depth());
    }

    private static boolean containsAxis(Optional<Climate.Parameter> axis, long value) {
        if (axis.isEmpty()) return true;
        Climate.Parameter p = axis.get();
        return value >= p.min() && value <= p.max();
    }

    /** Dispatching codec keyed on a {@code "type"} field, backed by the BiomeZone registry. */
    Codec<BiomeZone> CODEC = IsekaiDispatch.dispatchCodec(
            IsekaiSpiTypes.BIOME_ZONE_REGISTRY, BiomeZone::codec, "BiomeZone");

    // --- variants ---

    /** Matches everywhere. Use as the catch-all last entry. @since 1.0.0 */
    record Always() implements BiomeZone {
        public static final MapCodec<Always> MAP_CODEC = MapCodec.unit(Always::new);
        @Override public boolean test(int x, int y, int z) { return true; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /** Matches where block Y &ge; {@code y}. @since 1.0.0 */
    record YAbove(int y) implements BiomeZone {
        public static final MapCodec<YAbove> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(YAbove::y)).apply(i, YAbove::new));
        @Override public boolean test(int x, int qy, int z) { return QuartPos.toBlock(qy) >= y; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /** Matches where block Y &lt; {@code y}. @since 1.0.0 */
    record YBelow(int y) implements BiomeZone {
        public static final MapCodec<YBelow> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("y").forGetter(YBelow::y)).apply(i, YBelow::new));
        @Override public boolean test(int x, int qy, int z) { return QuartPos.toBlock(qy) < y; }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /** Matches where {@code min} &le; block Y &lt; {@code max}. @since 1.0.0 */
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

    /** Matches within {@code radius} of {@code (centerX, centerZ)} in the XZ plane. @since 1.0.0 */
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

    /** Matches beyond {@code radius} of {@code (centerX, centerZ)} in the XZ plane. @since 1.0.0 */
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

    /**
     * Matches inside an axis-aligned block-coordinate box, both ends inclusive. {@code min_y} and
     * {@code max_y} are optional and independently so: omitted means unbounded in that direction,
     * so an XZ-only footprint simply leaves both out.
     *
     * <p>Biomes are sampled on a 4-block grid, so a cell is inside the box when its representative
     * block coordinate ({@code quart << 2}) is — a border that is not a multiple of 4 includes or
     * excludes the straddling cell as a whole, never half of it.
     * @since 3.0.0
     */
    record Box(int minX, int maxX, int minZ, int maxZ, Optional<Integer> minY, Optional<Integer> maxY)
            implements BiomeZone {
        public Box {
            if (minX > maxX) throw new IllegalArgumentException(
                    "box: min_x must be <= max_x: " + minX + " > " + maxX);
            if (minZ > maxZ) throw new IllegalArgumentException(
                    "box: min_z must be <= max_z: " + minZ + " > " + maxZ);
            if (minY.isPresent() && maxY.isPresent() && minY.get() > maxY.get()) throw new IllegalArgumentException(
                    "box: min_y must be <= max_y: " + minY.get() + " > " + maxY.get());
        }
        public static final MapCodec<Box> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_x").forGetter(Box::minX),
                Codec.INT.fieldOf("max_x").forGetter(Box::maxX),
                Codec.INT.fieldOf("min_z").forGetter(Box::minZ),
                Codec.INT.fieldOf("max_z").forGetter(Box::maxZ),
                Codec.INT.optionalFieldOf("min_y").forGetter(Box::minY),
                Codec.INT.optionalFieldOf("max_y").forGetter(Box::maxY))
                .apply(i, Box::new));
        @Override public boolean test(int qx, int qy, int qz) {
            int bx = QuartPos.toBlock(qx);
            int by = QuartPos.toBlock(qy);
            int bz = QuartPos.toBlock(qz);
            if (bx < minX || bx > maxX || bz < minZ || bz > maxZ) return false;
            if (minY.isPresent() && by < minY.get()) return false;
            if (maxY.isPresent() && by > maxY.get()) return false;
            return true;
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /**
     * Matches inside a box expressed in chunk coordinates, both ends inclusive, at every height.
     * Chunk borders land on biome-grid borders (16 is a multiple of 4), so the footprint is exact
     * with no rounding choice to make — unlike {@link Box} with arbitrary block bounds.
     * @since 3.0.0
     */
    record ChunkBox(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) implements BiomeZone {
        public ChunkBox {
            if (minChunkX > maxChunkX) throw new IllegalArgumentException(
                    "chunk_box: min_chunk_x must be <= max_chunk_x: " + minChunkX + " > " + maxChunkX);
            if (minChunkZ > maxChunkZ) throw new IllegalArgumentException(
                    "chunk_box: min_chunk_z must be <= max_chunk_z: " + minChunkZ + " > " + maxChunkZ);
        }
        public static final MapCodec<ChunkBox> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_chunk_x").forGetter(ChunkBox::minChunkX),
                Codec.INT.fieldOf("max_chunk_x").forGetter(ChunkBox::maxChunkX),
                Codec.INT.fieldOf("min_chunk_z").forGetter(ChunkBox::minChunkZ),
                Codec.INT.fieldOf("max_chunk_z").forGetter(ChunkBox::maxChunkZ))
                .apply(i, ChunkBox::new));
        @Override public boolean test(int qx, int qy, int qz) {
            int cx = QuartPos.toBlock(qx) >> 4;
            int cz = QuartPos.toBlock(qz) >> 4;
            return cx >= minChunkX && cx <= maxChunkX && cz >= minChunkZ && cz <= maxChunkZ;
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /** Matches where all inner zones match. @since 1.0.0 */
    record And(List<BiomeZone> all) implements BiomeZone {
        public And { all = List.copyOf(all); }
        public static final MapCodec<And> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("all").forGetter(And::all))
                .apply(i, And::new));
        @Override public boolean test(int x, int y, int z) {
            for (var c : all) if (!c.test(x, y, z)) return false;
            return true;
        }
        @Override public boolean test(int x, int y, int z, ZoneContext ctx) {
            for (var c : all) if (!c.test(x, y, z, ctx)) return false;
            return true;
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return all; }
        @Override public BiomeZone withWorldSeed(long worldSeed) {
            return new And(all.stream().map(c -> c.withWorldSeed(worldSeed)).toList());
        }
    }

    /** Matches where any inner zone matches. @since 1.0.0 */
    record Or(List<BiomeZone> any) implements BiomeZone {
        public Or { any = List.copyOf(any); }
        public static final MapCodec<Or> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("any").forGetter(Or::any))
                .apply(i, Or::new));
        @Override public boolean test(int x, int y, int z) {
            for (var c : any) if (c.test(x, y, z)) return true;
            return false;
        }
        @Override public boolean test(int x, int y, int z, ZoneContext ctx) {
            for (var c : any) if (c.test(x, y, z, ctx)) return true;
            return false;
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return any; }
        @Override public BiomeZone withWorldSeed(long worldSeed) {
            return new Or(any.stream().map(c -> c.withWorldSeed(worldSeed)).toList());
        }
    }

    /** Matches where the inner zone does not match. @since 1.0.0 */
    record Not(BiomeZone inner) implements BiomeZone {
        public static final MapCodec<Not> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).fieldOf("inner").forGetter(Not::inner))
                .apply(i, Not::new));
        @Override public boolean test(int x, int y, int z) { return !inner.test(x, y, z); }
        @Override public boolean test(int x, int y, int z, ZoneContext ctx) {
            return !inner.test(x, y, z, ctx);
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return List.of(inner); }
        @Override public BiomeZone withWorldSeed(long worldSeed) {
            return new Not(inner.withWorldSeed(worldSeed));
        }
    }

    /**
     * Match where a {@link NormalNoise} sampled at the position exceeds {@code threshold}. Use to
     * introduce organic, non-geometric biome borders.
     *
     * <p>The noise stream comes from {@link BiomeZone#deriveSeed(long, long)} over the world seed
     * and this zone's {@code seed} field, so one datapack draws a different pattern in every world
     * while two zones with different {@code seed} fields stay independent inside one world. The
     * sampler is built once per level load (see {@link BiomeZone#withWorldSeed(long)}), not per
     * sample. Until a world seed is bound the zone samples as if the world seed were {@code 0}.
     *
     * <p>{@code size_xz} / {@code size_y} are 1/scale factors applied to the sampled block
     * coordinate — bigger values produce wider noise features.
     * @since 1.0.0
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
            return seeded(noise, seed, threshold, sizeXz, sizeY, 0L);
        }
        private static NoiseThreshold seeded(Holder<NormalNoise.NoiseParameters> noise, long seed,
                                             double threshold, double sizeXz, double sizeY, long worldSeed) {
            NormalNoise n = NormalNoise.create(
                    RandomSource.create(BiomeZone.deriveSeed(worldSeed, seed)), noise.value());
            return new NoiseThreshold(noise, seed, threshold, sizeXz, sizeY, n);
        }
        // Re-derives from the codec `seed` field, so re-binding a already-bound zone is a no-op
        // rather than a compounding shift of the pattern.
        @Override public BiomeZone withWorldSeed(long worldSeed) {
            return seeded(noise, seed, threshold, sizeXz, sizeY, worldSeed);
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
     * <p>Like {@link NoiseThreshold} the jitter noise is drawn from the world seed combined with
     * this zone's {@code seed} field, so the ripple differs per world and repeats for the same
     * world seed. The two offset streams are additionally salted apart from each other and from
     * {@code noise_threshold}, so a jitter and a threshold sharing one {@code seed} do not warp
     * and cut along the same contour.
     * @since 1.0.0
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
            return seeded(inner, noise, seed, strength, sizeXz, 0L);
        }
        private static EdgeJitter seeded(BiomeZone inner, Holder<NormalNoise.NoiseParameters> noise,
                                         long seed, double strength, double sizeXz, long worldSeed) {
            // Two independent samplers (different seed bits) so x-offset and z-offset are
            // decorrelated — otherwise both axes jitter together and the warp collapses to a
            // diagonal stretch instead of an organic ripple. Both are salted off the derived
            // seed, which also keeps them clear of a noise_threshold carrying the same `seed`.
            long derived = BiomeZone.deriveSeed(worldSeed, seed);
            NormalNoise nx = NormalNoise.create(RandomSource.create(derived ^ 0x9E3779B97F4A7C15L), noise.value());
            NormalNoise nz = NormalNoise.create(RandomSource.create(derived ^ 0xC2B2AE3D27D4EB4FL), noise.value());
            return new EdgeJitter(inner, noise, seed, strength, sizeXz, nx, nz);
        }
        @Override public BiomeZone withWorldSeed(long worldSeed) {
            return seeded(inner.withWorldSeed(worldSeed), noise, seed, strength, sizeXz, worldSeed);
        }
        @Override public boolean test(int qx, int qy, int qz) {
            return testJittered(qx, qy, qz, ZoneContext.NONE);
        }
        @Override public boolean test(int qx, int qy, int qz, ZoneContext ctx) {
            return testJittered(qx, qy, qz, ctx);
        }
        private boolean testJittered(int qx, int qy, int qz, ZoneContext ctx) {
            double bx = QuartPos.toBlock(qx);
            double bz = QuartPos.toBlock(qz);
            double ox = xSampler.getValue(bx / sizeXz, 0.0, bz / sizeXz) * strength;
            double oz = zSampler.getValue(bx / sizeXz, 0.0, bz / sizeXz) * strength;
            int jqx = QuartPos.fromBlock((int) Math.round(bx + ox));
            int jqz = QuartPos.fromBlock((int) Math.round(bz + oz));
            return inner.test(jqx, qy, jqz, ctx);
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
        @Override public List<BiomeZone> children() { return List.of(inner); }
    }

    /**
     * Match where the wrapped source's own answer at this position is one of {@code biome}.
     *
     * <p>This is how "replace the biome, but only inside the biomes I already have" is written:
     * the base source decides the layout, and the zone selects the part of it to paint over. The
     * answer comes from {@link ZoneContext}, filled by {@code isekai_api:override}; inside
     * {@code isekai_api:rule} — and in a bare {@link #test(int, int, int)} call — there is no
     * wrapped source, so the zone answers false.
     *
     * <p>Selection is by biome identity, so the referenced biomes must be ones the base source can
     * actually produce. {@code biome} takes an id, a list of ids, or a {@code #tag}.
     *
     * <p>Wrapping this in {@code edge_jitter} alone moves the <em>query</em> position, not the
     * boundary of the base biome: near a base border the base answer changes with the jitter, so
     * the result can spill a few blocks past the parent biome. To keep the edge while still
     * respecting it, put the jitter on the other side — {@code and(base_biome, edge_jitter(...))}.
     * @since 3.0.0
     */
    record BaseBiome(HolderSet<Biome> biomes) implements BiomeZone {
        public static final MapCodec<BaseBiome> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biome").forGetter(BaseBiome::biomes))
                .apply(i, BaseBiome::new));
        @Override public boolean test(int qx, int qy, int qz) { return false; }
        @Override public boolean test(int qx, int qy, int qz, ZoneContext ctx) {
            return ctx.baseBiome(qx, qy, qz).map(biomes::contains).orElse(false);
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }

    /**
     * Match where the vanilla climate sample falls inside every axis given. Each axis is an
     * optional {@code [min, max]} pair on the same {@code -1..1} scale the F3 screen and
     * {@code multi_noise} presets use; an omitted axis is unconstrained. At least one axis is
     * required — a {@code climate} zone with none would match everywhere, which is better written
     * as {@code always}.
     *
     * <p>Matching is the vanilla comparison: the sample's quantised values are tested against the
     * quantised bounds, so a boundary behaves exactly as it does in a {@code multi_noise} preset.
     *
     * <p>Reads the sampler through {@link ZoneContext}, so it works both under
     * {@code isekai_api:override} (the vanilla climate stays valid even where the base biome is
     * replaced) and under {@code isekai_api:rule}. Without a sampler it answers false.
     *
     * <p>The source samples climate once per position and reuses it across zones, so a stack of
     * climate conditions costs one sample, not one per condition.
     * @since 3.0.0
     */
    record ClimateMatch(Optional<Climate.Parameter> temperature, Optional<Climate.Parameter> humidity,
                        Optional<Climate.Parameter> continentalness, Optional<Climate.Parameter> erosion,
                        Optional<Climate.Parameter> weirdness, Optional<Climate.Parameter> depth)
            implements BiomeZone {
        private static final MapCodec<ClimateMatch> BASE = RecordCodecBuilder.mapCodec(i -> i.group(
                Climate.Parameter.CODEC.optionalFieldOf("temperature").forGetter(ClimateMatch::temperature),
                Climate.Parameter.CODEC.optionalFieldOf("humidity").forGetter(ClimateMatch::humidity),
                Climate.Parameter.CODEC.optionalFieldOf("continentalness").forGetter(ClimateMatch::continentalness),
                Climate.Parameter.CODEC.optionalFieldOf("erosion").forGetter(ClimateMatch::erosion),
                Climate.Parameter.CODEC.optionalFieldOf("weirdness").forGetter(ClimateMatch::weirdness),
                Climate.Parameter.CODEC.optionalFieldOf("depth").forGetter(ClimateMatch::depth))
                .apply(i, ClimateMatch::new));
        public static final MapCodec<ClimateMatch> MAP_CODEC = BASE.validate(ClimateMatch::checkAxes);
        private static DataResult<ClimateMatch> checkAxes(ClimateMatch zone) {
            if (zone.temperature().isEmpty() && zone.humidity().isEmpty() && zone.continentalness().isEmpty()
                    && zone.erosion().isEmpty() && zone.weirdness().isEmpty() && zone.depth().isEmpty()) {
                return DataResult.error(() -> "climate: at least one of temperature, humidity, continentalness,"
                        + " erosion, weirdness, depth is required");
            }
            return DataResult.success(zone);
        }
        @Override public boolean test(int qx, int qy, int qz) { return false; }
        @Override public boolean test(int qx, int qy, int qz, ZoneContext ctx) {
            return ctx.climate(qx, qy, qz).map(sample -> BiomeZone.climateMatches(sample,
                    temperature, humidity, continentalness, erosion, weirdness, depth)).orElse(false);
        }
        @Override public MapCodec<? extends BiomeZone> codec() { return MAP_CODEC; }
    }
}
