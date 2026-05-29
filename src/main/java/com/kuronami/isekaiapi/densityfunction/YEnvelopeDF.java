package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/**
 * Pure Y-axis mask: returns 1.0 inside the active band {@code [activeMinY, activeMaxY]},
 * 0.0 outside, with a linear {@code gradientWidth}-block transition at each end. When
 * {@code invert} is true the polarity flips (1 outside, 0 inside) — that's the foundation
 * for hollow-shell / inverted-overworld terrain shapes.
 *
 * <p>Composes with a noise source via multiplication: {@code y_envelope * noise} keeps
 * noise contributions only inside the active band. Pair with arithmetic offsets (constant +
 * mul) to build any band-shaped terrain — sky islands, hollow rings, capped mountains.
 *
 * <p>Leaf primitive — holds no inner density function.
 */
@ApiStatus.Internal
public record YEnvelopeDF(int activeMinY, int activeMaxY, int gradientWidth, boolean invert)
        implements DensityFunction.SimpleFunction {

    public static final MapCodec<YEnvelopeDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("active_min_y").forGetter(YEnvelopeDF::activeMinY),
            Codec.INT.fieldOf("active_max_y").forGetter(YEnvelopeDF::activeMaxY),
            Codec.INT.optionalFieldOf("gradient_width", 30).forGetter(YEnvelopeDF::gradientWidth),
            Codec.BOOL.optionalFieldOf("invert", false).forGetter(YEnvelopeDF::invert)
    ).apply(i, YEnvelopeDF::new));
    static final KeyDispatchDataCodec<YEnvelopeDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    public YEnvelopeDF {
        if (activeMaxY <= activeMinY) {
            throw new IllegalArgumentException(
                    "y_envelope: active_max_y (" + activeMaxY + ") must be > active_min_y (" + activeMinY + ")");
        }
        if (gradientWidth < 0) {
            throw new IllegalArgumentException("y_envelope: gradient_width must be >= 0: " + gradientWidth);
        }
    }

    @Override
    public double compute(FunctionContext ctx) {
        int y = ctx.blockY();
        double inside = envelopeValue(y);
        return invert ? 1.0 - inside : inside;
    }

    /**
     * Trapezoid envelope: ramps 0→1 over {@code [activeMinY - gradientWidth, activeMinY]},
     * stays at 1 through the active band, ramps 1→0 over {@code [activeMaxY, activeMaxY +
     * gradientWidth]}, then 0 outside.
     */
    private double envelopeValue(int y) {
        if (gradientWidth == 0) {
            return (y >= activeMinY && y <= activeMaxY) ? 1.0 : 0.0;
        }
        if (y < activeMinY - gradientWidth || y > activeMaxY + gradientWidth) return 0.0;
        if (y >= activeMinY && y <= activeMaxY) return 1.0;
        if (y < activeMinY) {
            return Mth.clamp((double)(y - (activeMinY - gradientWidth)) / gradientWidth, 0.0, 1.0);
        }
        // y > activeMaxY
        return Mth.clamp((double)((activeMaxY + gradientWidth) - y) / gradientWidth, 0.0, 1.0);
    }

    @Override public double minValue() { return 0.0; }
    @Override public double maxValue() { return 1.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
