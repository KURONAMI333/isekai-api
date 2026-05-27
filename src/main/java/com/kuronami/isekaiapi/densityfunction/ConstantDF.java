package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Constant value. Primitive: scalar. */
public record ConstantDF(double value) implements DensityFunction.SimpleFunction {
    public static final MapCodec<ConstantDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("value").forGetter(ConstantDF::value)
    ).apply(i, ConstantDF::new));
    public static final KeyDispatchDataCodec<ConstantDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) { return value; }
    @Override public double minValue() { return value; }
    @Override public double maxValue() { return value; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
