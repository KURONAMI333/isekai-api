package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/**
 * Maps {@code v -> (v > 0 ? v : v*0.25)} — vanilla's {@code minecraft:quarter_negative}
 * ({@link net.minecraft.world.level.levelgen.DensityFunctions.Mapped} with
 * {@code Mapped.Type.QUARTER_NEGATIVE}), reimplemented here because {@code Mapped} and its
 * {@code Type} enum are package-private in vanilla and cannot be constructed from outside —
 * the same reason {@link SqueezeDF} reimplements squeeze.
 *
 * <p>Why it matters for terrain: in the vanilla {@code sloped_cheese} chain the (depth*factor)
 * field is passed through {@code quarter_negative} before amplification, which makes the
 * terrain SOLID quickly below the surface (positive density preserved) while keeping the
 * AIR side gentle (negative density quartered). Without it the surface gradient is symmetric
 * and islands turn to floating overhangs. {@link SlopedDensityDF} relies on it.
 */
@ApiStatus.Internal
public record QuarterNegativeDF(DensityFunction inner) implements CompositeDensityFunction {

    public static final MapCodec<QuarterNegativeDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(QuarterNegativeDF::inner)
    ).apply(i, QuarterNegativeDF::new));
    static final KeyDispatchDataCodec<QuarterNegativeDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override
    public double compute(FunctionContext ctx) {
        double v = inner.compute(ctx);
        return v > 0.0 ? v : v * 0.25;
    }

    @Override
    public DensityFunction mapAll(Visitor v) {
        return v.apply(new QuarterNegativeDF(inner.mapAll(v)));
    }

    @Override
    public double minValue() {
        double m = inner.minValue();
        return m > 0.0 ? m : m * 0.25;
    }

    @Override
    public double maxValue() {
        // quarter_negative is monotone non-decreasing, so the max image is at the max input.
        double mx = inner.maxValue();
        return mx > 0.0 ? mx : mx * 0.25;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
