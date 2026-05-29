package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/** Sample {@code f} at coordinates shifted by (dx, dy, dz). */
@ApiStatus.Internal
public record TranslateDF(DensityFunction f, double dx, double dy, double dz) implements CompositeDensityFunction {
    public static final MapCodec<TranslateDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(TranslateDF::f),
            Codec.DOUBLE.fieldOf("dx").forGetter(TranslateDF::dx),
            Codec.DOUBLE.fieldOf("dy").forGetter(TranslateDF::dy),
            Codec.DOUBLE.fieldOf("dz").forGetter(TranslateDF::dz)
    ).apply(i, TranslateDF::new));
    static final KeyDispatchDataCodec<TranslateDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return f.compute(new TranslatedContext(ctx, dx, dy, dz));
    }
    @Override public DensityFunction mapAll(Visitor v) { return v.apply(new TranslateDF(f.mapAll(v), dx, dy, dz)); }
    @Override public double minValue() { return f.minValue(); }
    @Override public double maxValue() { return f.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    private record TranslatedContext(FunctionContext inner, double dx, double dy, double dz) implements FunctionContext {
        // Mth.floor (not (int)) keeps the mapping monotonic across x=0 / z=0 boundaries.
        @Override public int blockX() { return Mth.floor(inner.blockX() - dx); }
        @Override public int blockY() { return Mth.floor(inner.blockY() - dy); }
        @Override public int blockZ() { return Mth.floor(inner.blockZ() - dz); }
    }
}
