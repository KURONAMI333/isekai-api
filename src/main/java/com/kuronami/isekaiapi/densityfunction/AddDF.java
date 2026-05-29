package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/** Arithmetic sum of two functions. Primitive: binary arithmetic. */
@ApiStatus.Internal
public record AddDF(DensityFunction a, DensityFunction b) implements CompositeDensityFunction {
    public static final MapCodec<AddDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("a").forGetter(AddDF::a),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("b").forGetter(AddDF::b)
    ).apply(i, AddDF::new));
    static final KeyDispatchDataCodec<AddDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) { return a.compute(ctx) + b.compute(ctx); }
    @Override public DensityFunction mapAll(Visitor v) { return v.apply(new AddDF(a.mapAll(v), b.mapAll(v))); }
    @Override public double minValue() { return a.minValue() + b.minValue(); }
    @Override public double maxValue() { return a.maxValue() + b.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
