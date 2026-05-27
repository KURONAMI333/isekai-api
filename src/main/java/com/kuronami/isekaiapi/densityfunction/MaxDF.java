package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Pointwise maximum. Primitive: binary combinator. */
public record MaxDF(DensityFunction a, DensityFunction b) implements DensityFunction.SimpleFunction {
    public static final MapCodec<MaxDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("a").forGetter(MaxDF::a),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("b").forGetter(MaxDF::b)
    ).apply(i, MaxDF::new));
    public static final KeyDispatchDataCodec<MaxDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) { return Math.max(a.compute(ctx), b.compute(ctx)); }
    @Override public double minValue() { return Math.max(a.minValue(), b.minValue()); }
    @Override public double maxValue() { return Math.max(a.maxValue(), b.maxValue()); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
