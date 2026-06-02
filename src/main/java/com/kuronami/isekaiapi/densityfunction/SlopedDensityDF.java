package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.ApiStatus;

/**
 * Neutral terrain-density composer: assemble the proven non-terraced surface formula from
 * three structural inputs. Encapsulates four pieces of tribal knowledge so a consumer never
 * has to learn them: (a) vanilla's {@code quarter_negative} exists, (b) the 4× post-amplifier,
 * (c) {@code base_noise} must stay at full weight or the surface voxel-terraces into
 * staircases, (d) {@code factor} above ~6 reintroduces terracing and turns coasts into cliffs.
 *
 * <p>Emits the tree {@code add(mul(4, quarter_negative(mul(depth_field, factor))), base_noise)}
 * — vanilla's {@code sloped_cheese} shape, exposed under a neutral name. Pair the result with
 * {@code minecraft:blend_density} + {@code minecraft:interpolated} in the {@code final_density}
 * slot, the same way you would any other density function.
 *
 * <p><b>Theme stays with the consumer.</b> {@code depth_field} is supplied by the consumer:
 * compose any {@code y_clamped_gradient} + xz-offset (continentalness, distance, custom noise,
 * spline-remapped erosion, anything) that expresses the world you want. This primitive
 * supplies only the structural amplifier + detail-noise composition that makes the result a
 * natural surface instead of a 2D-heightmap staircase.
 *
 * <p>Recommended pairing for ocean/island shapes: set {@code aquifers_enabled: false} in the
 * noise_settings. Aquifers place perched water that pours off coasts as waterfalls; turning
 * them off gives clean shorelines.
 */
@ApiStatus.Internal
public record SlopedDensityDF(DensityFunction depthField, double factor,
                              DensityFunction baseNoise, DensityFunction inner)
        implements CompositeDensityFunction {

    /** Hard ceiling on {@code factor}: above this, terracing returns and coasts become cliffs. */
    public static final double FACTOR_CEILING = 6.0;

    public static final MapCodec<SlopedDensityDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("depth_field").forGetter(SlopedDensityDF::depthField),
            Codec.doubleRange(0.5, FACTOR_CEILING).optionalFieldOf("factor", 4.0).forGetter(SlopedDensityDF::factor),
            DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("base_noise", BlendedNoiseDF.fromParams(320.0, 240.0, 8.0))
                    .forGetter(SlopedDensityDF::baseNoise)
    ).apply(i, SlopedDensityDF::fromConfig));
    static final KeyDispatchDataCodec<SlopedDensityDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    public static SlopedDensityDF fromConfig(DensityFunction depthField, double factor,
                                             DensityFunction baseNoise) {
        DensityFunction tree = build(depthField, factor, baseNoise);
        return new SlopedDensityDF(depthField, factor, baseNoise, tree);
    }

    /**
     * Build the proven non-terraced density tree. Public so future neutral composers can
     * delegate to it without going through the codec.
     */
    public static DensityFunction build(DensityFunction depthField, double factor,
                                        DensityFunction baseNoise) {
        DensityFunction shaped = DensityFunctions.mul(
                DensityFunctions.constant(4.0),
                new QuarterNegativeDF(
                        DensityFunctions.mul(depthField, DensityFunctions.constant(factor))));
        return DensityFunctions.add(shaped, baseNoise);
    }

    @Override public double compute(FunctionContext ctx) { return inner.compute(ctx); }

    @Override
    public DensityFunction mapAll(Visitor v) {
        return v.apply(new SlopedDensityDF(depthField.mapAll(v), factor,
                baseNoise.mapAll(v), inner.mapAll(v)));
    }

    @Override public double minValue() { return inner.minValue(); }
    @Override public double maxValue() { return inner.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
