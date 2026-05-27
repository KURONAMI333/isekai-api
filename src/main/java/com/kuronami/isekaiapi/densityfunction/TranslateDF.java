package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Sample {@code f} at coordinates shifted by (dx, dy, dz). */
public record TranslateDF(DensityFunction f, double dx, double dy, double dz) implements DensityFunction.SimpleFunction {
    public static final MapCodec<TranslateDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(TranslateDF::f),
            Codec.DOUBLE.fieldOf("dx").forGetter(TranslateDF::dx),
            Codec.DOUBLE.fieldOf("dy").forGetter(TranslateDF::dy),
            Codec.DOUBLE.fieldOf("dz").forGetter(TranslateDF::dz)
    ).apply(i, TranslateDF::new));
    public static final KeyDispatchDataCodec<TranslateDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return f.compute(new TranslatedContext(ctx, dx, dy, dz));
    }
    @Override public double minValue() { return f.minValue(); }
    @Override public double maxValue() { return f.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    private record TranslatedContext(FunctionContext inner, double dx, double dy, double dz) implements FunctionContext {
        @Override public int blockX() { return (int) (inner.blockX() - dx); }
        @Override public int blockY() { return (int) (inner.blockY() - dy); }
        @Override public int blockZ() { return (int) (inner.blockZ() - dz); }
    }
}
