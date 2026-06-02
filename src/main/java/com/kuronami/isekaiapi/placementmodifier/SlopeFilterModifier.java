package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import org.jetbrains.annotations.ApiStatus;

import java.util.stream.Stream;

/**
 * Filter placement by local terrain slope. Samples the heightmap at the input position and at
 * its four cardinal neighbours, computes the maximum height difference within
 * {@code sample_radius} blocks, normalises by {@code 2*sample_radius} (so the value is
 * roughly in {@code [0, 1]} — 0 = perfectly flat, 1 ≈ a 45°+ cliff), and accepts when the
 * normalised slope falls within {@code [min_slope, max_slope]}.
 *
 * <p>Pure geometry — no biome or "beach"/"cliff" naming. Pair with a heightmap modifier
 * upstream (the test is at the heightmap-resolved position).
 *
 * <p>JSON: {@code {"type":"isekai_api:slope_filter", "min_slope": 0.0, "max_slope": 0.2,
 * "sample_radius": 2, "heightmap": "WORLD_SURFACE_WG"}}.
 */
@ApiStatus.Internal
public class SlopeFilterModifier extends PlacementModifier {

    public static final MapCodec<SlopeFilterModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("min_slope", 0.0).forGetter(m -> m.minSlope),
            Codec.doubleRange(0.0, 1.0).optionalFieldOf("max_slope", 1.0).forGetter(m -> m.maxSlope),
            Codec.intRange(1, 8).optionalFieldOf("sample_radius", 2).forGetter(m -> m.sampleRadius),
            Heightmap.Types.CODEC.optionalFieldOf("heightmap", Heightmap.Types.WORLD_SURFACE_WG)
                    .forGetter(m -> m.heightmap)
    ).apply(i, SlopeFilterModifier::new));

    private final double minSlope;
    private final double maxSlope;
    private final int sampleRadius;
    private final Heightmap.Types heightmap;

    public SlopeFilterModifier(double minSlope, double maxSlope, int sampleRadius,
                               Heightmap.Types heightmap) {
        this.minSlope = minSlope;
        this.maxSlope = maxSlope;
        this.sampleRadius = sampleRadius;
        this.heightmap = heightmap;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        int r = this.sampleRadius;
        int hc = ctx.getHeight(this.heightmap, x, z);
        int hxm = ctx.getHeight(this.heightmap, x - r, z);
        int hxp = ctx.getHeight(this.heightmap, x + r, z);
        int hzm = ctx.getHeight(this.heightmap, x, z - r);
        int hzp = ctx.getHeight(this.heightmap, x, z + r);
        int maxDelta = Math.max(Math.max(Math.abs(hxm - hc), Math.abs(hxp - hc)),
                                Math.max(Math.abs(hzm - hc), Math.abs(hzp - hc)));
        // Normalise: a 45° slope over `sampleRadius` blocks has |dY| = sampleRadius. So divide by r.
        // Cap at 1.0 so steep cliffs read as 1.
        double slope = Math.min(1.0, (double) maxDelta / (double) r);
        return (slope >= this.minSlope && slope <= this.maxSlope) ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.SLOPE_FILTER.get();
    }
}
