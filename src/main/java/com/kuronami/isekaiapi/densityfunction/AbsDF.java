package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/** Absolute value. Primitive: unary. */
@ApiStatus.Internal
public record AbsDF(DensityFunction f) implements CompositeDensityFunction {
    public static final MapCodec<AbsDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(AbsDF::f)
    ).apply(i, AbsDF::new));
    static final KeyDispatchDataCodec<AbsDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) { return Math.abs(f.compute(ctx)); }
    @Override public DensityFunction mapAll(Visitor v) { return v.apply(new AbsDF(f.mapAll(v))); }
    @Override public double minValue() {
        return (f.minValue() <= 0 && f.maxValue() >= 0) ? 0 : Math.min(Math.abs(f.minValue()), Math.abs(f.maxValue()));
    }
    @Override public double maxValue() { return Math.max(Math.abs(f.minValue()), Math.abs(f.maxValue())); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
