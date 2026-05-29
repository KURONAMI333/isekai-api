package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.ApiStatus;

/**
 * High-level convenience composer for "noise visible only inside a Y-band" terrain shapes.
 * Equivalent to writing by hand:
 *
 * <pre>{@code
 * add(-0.05,
 *   add(-0.1,
 *     mul(y_envelope_bottom,
 *       add(0.1, add(-0.2,
 *         mul(y_envelope_top,
 *           add(0.2, add(-0.13, noise))))))))
 * }</pre>
 *
 * (The outer constants sum to a negative offset outside the active band, so density stays
 * below the solid threshold there and chunks stay empty.) Takes any {@link DensityFunction}
 * as the noise source — {@link BlendedNoiseDF} is the most common choice but you can pass
 * anything.
 *
 * <p>{@code invert} flips the two Y envelopes, producing terrain that hangs from the top
 * of the active band instead of sitting at the bottom (mirror across the band midpoint).
 *
 * <p>This composer is intentionally theme-neutral — it doesn't reference "sky", "flipped",
 * or any consumer concept. Consumers name their own world; Isekai just supplies the
 * primitive that the named worlds use.
 */
@ApiStatus.Internal
public record BandDensityDF(
        int activeMinY,
        int activeMaxY,
        int gradientWidth,
        boolean invert,
        double solidityBias,
        DensityFunction noise,
        DensityFunction inner
) implements CompositeDensityFunction {

    public static final MapCodec<BandDensityDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("active_min_y").forGetter(BandDensityDF::activeMinY),
            Codec.INT.fieldOf("active_max_y").forGetter(BandDensityDF::activeMaxY),
            Codec.INT.optionalFieldOf("gradient_width", 30).forGetter(BandDensityDF::gradientWidth),
            Codec.BOOL.optionalFieldOf("invert", false).forGetter(BandDensityDF::invert),
            Codec.doubleRange(-1.0, 1.0).optionalFieldOf("solidity_bias", 0.0).forGetter(BandDensityDF::solidityBias),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("noise").forGetter(BandDensityDF::noise)
    ).apply(i, BandDensityDF::fromConfig));
    static final KeyDispatchDataCodec<BandDensityDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    public static BandDensityDF fromConfig(int activeMinY, int activeMaxY, int gradientWidth,
                                            boolean invert, double solidityBias, DensityFunction noise) {
        if (activeMaxY <= activeMinY) {
            throw new IllegalArgumentException(
                    "band_density: active_max_y (" + activeMaxY + ") must be > active_min_y (" + activeMinY + ")");
        }
        if (gradientWidth < 1) {
            // Negative width inverts the boundary ramp; zero width makes the underlying
            // y_clamped_gradient compute 0/0 = NaN on the exact boundary plane (NaN density
            // leaves the surface/aquifer decision undefined for that slab). Require >= 1.
            throw new IllegalArgumentException(
                    "band_density: gradient_width must be >= 1: " + gradientWidth);
        }
        DensityFunction tree = buildInner(activeMinY, activeMaxY, gradientWidth, invert, solidityBias, noise);
        return new BandDensityDF(activeMinY, activeMaxY, gradientWidth, invert, solidityBias, noise, tree);
    }

    private static DensityFunction buildInner(int minY, int maxY, int gradWidth, boolean invert,
                                              double solidityBias, DensityFunction noise) {
        int bottomFrom = minY - gradWidth;
        int bottomTo = minY;
        int topFrom = maxY;
        int topTo = maxY + gradWidth;

        // The "inside-band" envelope: 1 inside [minY, maxY], 0 outside, linear ramp through
        // gradient_width at each end. This is the product of two y_clamped_gradients —
        // one that lifts from 0→1 at the bottom boundary, one that drops from 1→0 at the
        // top.
        DensityFunction insideEnvelope = DensityFunctions.mul(
                DensityFunctions.yClampedGradient(bottomFrom, bottomTo, 0.0, 1.0),
                DensityFunctions.yClampedGradient(topFrom, topTo, 1.0, 0.0));

        // For invert=true (continuous terrain ABOVE and BELOW the band, void INSIDE),
        // flip polarity: 0 inside, 1 outside. Single envelope, not AND of two — the AND
        // of two "outside" masks is geometrically inconsistent (only 1 at simultaneous
        // top-and-bottom-outside positions, which is empty).
        DensityFunction envelope = invert
                ? DensityFunctions.add(DensityFunctions.constant(1.0),
                        DensityFunctions.mul(DensityFunctions.constant(-1.0), insideEnvelope))
                : insideEnvelope;

        // Apply solidity_bias by shifting the inner noise toward solid (positive). At
        // bias=0 the band is mostly void with sparse positive-density blobs (scattered
        // islands); at bias=0.4+ density is positive across most of the band (continuous
        // terrain with occasional gaps).
        DensityFunction core = DensityFunctions.add(
                DensityFunctions.constant(0.2 + solidityBias),
                DensityFunctions.add(DensityFunctions.constant(-0.13), noise));

        // Outer offsets: the four constants (-0.05, -0.1, +0.1, -0.2) sum to -0.25, so
        // OUTSIDE the envelope (envelope=0) the result is -0.25 (well below the solid
        // threshold → void). INSIDE (envelope=1) it is noise + 0.07 + solidity_bias - 0.25
        // = noise + solidity_bias - 0.18 (terrain where noise+bias clears 0.18).
        return DensityFunctions.add(
                DensityFunctions.constant(-0.05),
                DensityFunctions.add(DensityFunctions.constant(-0.1),
                        DensityFunctions.add(
                                DensityFunctions.constant(0.1),
                                DensityFunctions.add(DensityFunctions.constant(-0.2),
                                        DensityFunctions.mul(envelope, core)))));
    }

    @Override public double compute(FunctionContext ctx) { return inner.compute(ctx); }
    @Override public DensityFunction mapAll(Visitor v) {
        return v.apply(new BandDensityDF(activeMinY, activeMaxY, gradientWidth, invert, solidityBias,
                noise.mapAll(v), inner.mapAll(v)));
    }
    @Override public double minValue() { return inner.minValue(); }
    @Override public double maxValue() { return inner.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
