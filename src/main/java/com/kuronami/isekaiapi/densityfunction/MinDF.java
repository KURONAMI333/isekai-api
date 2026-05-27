package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Pointwise minimum. Primitive: binary combinator. */
public record MinDF(DensityFunction a, DensityFunction b) implements DensityFunction.SimpleFunction {
    public static final MapCodec<MinDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("a").forGetter(MinDF::a),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("b").forGetter(MinDF::b)
    ).apply(i, MinDF::new));
    public static final KeyDispatchDataCodec<MinDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) { return Math.min(a.compute(ctx), b.compute(ctx)); }
    @Override public double minValue() { return Math.min(a.minValue(), b.minValue()); }
    @Override public double maxValue() { return Math.min(a.maxValue(), b.maxValue()); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
