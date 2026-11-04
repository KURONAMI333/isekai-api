package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import org.jetbrains.annotations.ApiStatus;

import java.util.stream.Stream;

/**
 * Accept a position with a probability that falls off with its horizontal distance to the nearest
 * cell of a biome: {@code 1} within {@code full_distance}, {@code t^exponent} in between
 * ({@code t = (max - d) / (max - full)}), and {@code 0} past {@code max_distance}.
 *
 * <p>The edge is soft on purpose. A hard radius drawn on a biome border puts the same pack of
 * features right up to one side of the line and nothing on the other; because the acceptance rate
 * here is continuous, density thins toward the border and the seam stops being visible. For a
 * hard radius anyway, set {@code full_distance} equal to {@code max_distance}.
 *
 * <p>Distance is horizontal only — the biome under the candidate's own feet, plus the band
 * {@code vertical_range} reaches above and below it. Dimensions whose biome changes with altitude
 * (the nether: crimson forest over the lava sea, soul sand valley under it) need that band, or the
 * search only ever sees the thin slice at the candidate's own Y and the falloff measures noise.
 *
 * <p>JSON: {@code {"type":"isekai_api:biome_falloff", "biome":"minecraft:crimson_forest",
 * "max_distance":48, "full_distance":8, "exponent":1.5, "vertical_range":{"below":24,"above":8},
 * "step":4}}.
 *
 * <p>The biome is read from the dimension's {@link BiomeSource} rather than from the chunk, because
 * {@code WorldGenLevel#getBiome} cannot read the chunks outside the 3x3 the feature is generating
 * in — the radius it would have to read is exactly the area it cannot reach. Reading the source is
 * also what keeps the answer identical in every chunk of one biome, so the falloff does not flicker
 * along a chunk border. The cells it reads are memoized per thread (see {@link BiomeCellSamples}):
 * the 40 candidates of one chunk ask the same few thousand cells, and asking the source 77,000
 * times for them is the difference between 29 ms and 0.9 ms of a chunk.
 *
 * @since after 3.0.0
 */
@ApiStatus.Internal
public class BiomeFalloffModifier extends PlacementModifier {

    /** Horizontal lattice spacings the search may use. */
    public static final Codec<Integer> STEP_CODEC = Codec.INT.validate(v ->
            v == 4 || v == 8 || v == 16
                    ? DataResult.success(v)
                    : DataResult.error(() -> "step must be 4, 8 or 16, got " + v));

    /**
     * How far above and below the input position to look for the biome, in blocks. Same JSON shape
     * and bounds as {@code fluid_edge}'s field, but sampled every four blocks (one biome cell):
     * {@code {"below": 0, "above": 16}} reads Y, Y+4, Y+8, Y+12 and Y+16, and a value of 1–3 adds
     * nothing.
     */
    public record VerticalSpan(int below, int above) {
        public static final VerticalSpan NONE = new VerticalSpan(0, 0);

        public static final Codec<VerticalSpan> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 64).optionalFieldOf("below", 0).forGetter(VerticalSpan::below),
                Codec.intRange(0, 64).optionalFieldOf("above", 0).forGetter(VerticalSpan::above)
        ).apply(i, VerticalSpan::new));

        public boolean isSingleLevel() {
            return below == 0 && above == 0;
        }
    }

    private static final MapCodec<BiomeFalloffModifier> BASE = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("biome").forGetter(m -> m.biomes),
            Codec.intRange(4, 128).fieldOf("max_distance").forGetter(m -> m.maxDistance),
            Codec.intRange(0, 128).optionalFieldOf("full_distance", 0).forGetter(m -> m.fullDistance),
            Codec.floatRange(0.25f, 8f).optionalFieldOf("exponent", 1.0f).forGetter(m -> m.exponent),
            VerticalSpan.CODEC.optionalFieldOf("vertical_range", VerticalSpan.NONE)
                    .forGetter(m -> m.verticalRange),
            STEP_CODEC.optionalFieldOf("step", 4).forGetter(m -> m.step)
    ).apply(i, BiomeFalloffModifier::new));

    public static final MapCodec<BiomeFalloffModifier> CODEC = BASE.validate(BiomeFalloffModifier::check);

    private final HolderSet<Biome> biomes;
    private final int maxDistance;
    private final int fullDistance;
    private final float exponent;
    private final VerticalSpan verticalRange;
    private final int step;

    public BiomeFalloffModifier(HolderSet<Biome> biomes, int maxDistance, int fullDistance,
                                float exponent, VerticalSpan verticalRange, int step) {
        this.biomes = biomes;
        this.maxDistance = maxDistance;
        this.fullDistance = fullDistance;
        this.exponent = exponent;
        this.verticalRange = verticalRange;
        this.step = step;
    }

    /** The codec rejects a falloff band of negative width; Java callers get the hard filter instead. */
    private static DataResult<BiomeFalloffModifier> check(BiomeFalloffModifier m) {
        if (m.fullDistance > m.maxDistance) {
            return DataResult.error(() -> "full_distance (" + m.fullDistance
                    + ") must not exceed max_distance (" + m.maxDistance + ")");
        }
        return DataResult.success(m);
    }

    public HolderSet<Biome> biomes() {
        return biomes;
    }

    public int maxDistance() {
        return maxDistance;
    }

    public int fullDistance() {
        return fullDistance;
    }

    public float exponent() {
        return exponent;
    }

    public VerticalSpan verticalRange() {
        return verticalRange;
    }

    public int step() {
        return step;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        double radius = BiomeFalloffSearch.radius(rand.nextDouble(), fullDistance, maxDistance, exponent);
        WorldGenLevel level = ctx.getLevel();
        ServerLevel serverLevel = level.getLevel();
        BiomeSource source = ctx.generator().getBiomeSource();
        Climate.Sampler sampler = serverLevel.getChunkSource().randomState().sampler();
        BiomeCellSamples samples = BiomeCellSamples.current();
        try {
            BiomeFalloffSearch.Result result = BiomeFalloffSearch.search(
                    pos.getX(), pos.getY(), pos.getZ(), radius, step,
                    verticalRange.below(), verticalRange.above(),
                    // The biome grid is four blocks per sample in every axis; block -> sample is a
                    // floor shift, which is also how vanilla maps a block to its biome cell. The
                    // cell, not the block, is what gets remembered: the whole chunk wants the same
                    // few thousand of them.
                    (x, y, z) -> biomes.contains(
                            samples.at(source, sampler, x >> 2, y >> 2, z >> 2)));
            return result.found() ? Stream.of(pos) : Stream.empty();
        } catch (RuntimeException e) {
            // Reject rather than allow. A biome source that cannot answer means this dimension
            // cannot confirm the biome anywhere, and allowing would place the feature everywhere
            // at full density — the opposite of what the pack asked for. Structure-time near_biome
            // allows instead, but it is a filter on an otherwise-valid position; here the answer
            // is the whole modifier.
            IsekaiApi.LOGGER.debug("[Isekai] biome_falloff could not read the biome source; "
                    + "skipping this position: {}", e.getMessage());
            return Stream.empty();
        }
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.BIOME_FALLOFF.get();
    }
}
