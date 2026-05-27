package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Sign reversal. Primitive: unary arithmetic. */
public record NegateDF(DensityFunction f) implements DensityFunction.SimpleFunction {
    public static final MapCodec<NegateDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(NegateDF::f)
    ).apply(i, NegateDF::new));
    public static final KeyDispatchDataCodec<NegateDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) { return -f.compute(ctx); }
    @Override public double minValue() { return -f.maxValue(); }
    @Override public double maxValue() { return -f.minValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
