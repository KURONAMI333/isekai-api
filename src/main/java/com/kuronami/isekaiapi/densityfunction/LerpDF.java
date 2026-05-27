package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Linear interpolation: t * b + (1 - t) * a. {@code t} clamped to [0, 1]. */
public record LerpDF(DensityFunction t, DensityFunction a, DensityFunction b) implements DensityFunction.SimpleFunction {
    public static final MapCodec<LerpDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("t").forGetter(LerpDF::t),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("a").forGetter(LerpDF::a),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("b").forGetter(LerpDF::b)
    ).apply(i, LerpDF::new));
    public static final KeyDispatchDataCodec<LerpDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        double tv = t.compute(ctx); if (tv < 0) tv = 0; else if (tv > 1) tv = 1;
        return tv * b.compute(ctx) + (1.0 - tv) * a.compute(ctx);
    }
    @Override public double minValue() { return Math.min(a.minValue(), b.minValue()); }
    @Override public double maxValue() { return Math.max(a.maxValue(), b.maxValue()); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
