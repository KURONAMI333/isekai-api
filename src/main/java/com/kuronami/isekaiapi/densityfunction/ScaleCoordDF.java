package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Sample {@code f} at coordinates scaled by (sx, sy, sz). Negative factors mirror the axis.
 * sx=sy=sz=1.0 is identity. sx=2.0 stretches the function horizontally by 2x.
 */
public record ScaleCoordDF(DensityFunction f, double sx, double sy, double sz) implements DensityFunction.SimpleFunction {
    public static final MapCodec<ScaleCoordDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(ScaleCoordDF::f),
            Codec.DOUBLE.fieldOf("sx").forGetter(ScaleCoordDF::sx),
            Codec.DOUBLE.fieldOf("sy").forGetter(ScaleCoordDF::sy),
            Codec.DOUBLE.fieldOf("sz").forGetter(ScaleCoordDF::sz)
    ).apply(i, ScaleCoordDF::new));
    public static final KeyDispatchDataCodec<ScaleCoordDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return f.compute(new ScaledContext(ctx, sx, sy, sz));
    }
    @Override public double minValue() { return f.minValue(); }
    @Override public double maxValue() { return f.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    private record ScaledContext(FunctionContext inner, double sx, double sy, double sz) implements FunctionContext {
        @Override public int blockX() { return (int) (inner.blockX() / sx); }
        @Override public int blockY() { return (int) (inner.blockY() / sy); }
        @Override public int blockZ() { return (int) (inner.blockZ() / sz); }
    }
}
