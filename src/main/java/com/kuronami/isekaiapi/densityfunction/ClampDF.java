package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Clamp value to [min, max]. Primitive: value range. */
public record ClampDF(DensityFunction f, double min, double max) implements DensityFunction.SimpleFunction {
    public static final MapCodec<ClampDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(ClampDF::f),
            Codec.DOUBLE.fieldOf("min").forGetter(ClampDF::min),
            Codec.DOUBLE.fieldOf("max").forGetter(ClampDF::max)
    ).apply(i, ClampDF::new));
    public static final KeyDispatchDataCodec<ClampDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        double v = f.compute(ctx); return v < min ? min : (v > max ? max : v);
    }
    @Override public double minValue() { return Math.max(min, f.minValue()); }
    @Override public double maxValue() { return Math.min(max, f.maxValue()); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
