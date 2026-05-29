package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/**
 * Sample {@code f} at coordinates scaled by (sx, sy, sz). Negative factors mirror the axis.
 * sx=sy=sz=1.0 is identity. sx=2.0 stretches the function horizontally by 2x.
 *
 * <p>Scale factors must be non-zero — zero would divide by zero and produce {@code NaN}
 * terrain. Negative is allowed (mirrors the axis) and validated at codec decode time.
 */
@ApiStatus.Internal
public record ScaleCoordDF(DensityFunction f, double sx, double sy, double sz) implements CompositeDensityFunction {
    private static final Codec<Double> NONZERO_DOUBLE = Codec.DOUBLE.validate(d ->
            d == 0.0 ? DataResult.error(() -> "scale factor must be non-zero")
                     : DataResult.success(d));

    public static final MapCodec<ScaleCoordDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(ScaleCoordDF::f),
            NONZERO_DOUBLE.fieldOf("sx").forGetter(ScaleCoordDF::sx),
            NONZERO_DOUBLE.fieldOf("sy").forGetter(ScaleCoordDF::sy),
            NONZERO_DOUBLE.fieldOf("sz").forGetter(ScaleCoordDF::sz)
    ).apply(i, ScaleCoordDF::new));
    static final KeyDispatchDataCodec<ScaleCoordDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return f.compute(new ScaledContext(ctx, sx, sy, sz));
    }
    @Override public DensityFunction mapAll(Visitor v) { return v.apply(new ScaleCoordDF(f.mapAll(v), sx, sy, sz)); }
    @Override public double minValue() { return f.minValue(); }
    @Override public double maxValue() { return f.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    private record ScaledContext(FunctionContext inner, double sx, double sy, double sz) implements FunctionContext {
        // Mth.floor (not (int)) keeps the mapping monotonic — (int)(-0.5)==0 but
        // Mth.floor(-0.5)==-1, avoiding a seam at axis=0.
        @Override public int blockX() { return Mth.floor(inner.blockX() / sx); }
        @Override public int blockY() { return Mth.floor(inner.blockY() / sy); }
        @Override public int blockZ() { return Mth.floor(inner.blockZ() / sz); }
    }
}
