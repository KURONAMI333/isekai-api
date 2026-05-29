package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/** Step function: returns {@code low} if {@code value < threshold}, else {@code high}. */
@ApiStatus.Internal
public record StepDF(DensityFunction value, double threshold, DensityFunction low, DensityFunction high) implements CompositeDensityFunction {
    public static final MapCodec<StepDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("value").forGetter(StepDF::value),
            Codec.DOUBLE.fieldOf("threshold").forGetter(StepDF::threshold),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("low").forGetter(StepDF::low),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("high").forGetter(StepDF::high)
    ).apply(i, StepDF::new));
    static final KeyDispatchDataCodec<StepDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return value.compute(ctx) < threshold ? low.compute(ctx) : high.compute(ctx);
    }
    @Override public DensityFunction mapAll(Visitor v) { return v.apply(new StepDF(value.mapAll(v), threshold, low.mapAll(v), high.mapAll(v))); }
    @Override public double minValue() { return Math.min(low.minValue(), high.minValue()); }
    @Override public double maxValue() { return Math.max(low.maxValue(), high.maxValue()); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
