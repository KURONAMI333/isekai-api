package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/** Arithmetic product of two functions. Primitive: binary arithmetic. */
@ApiStatus.Internal
public record MultiplyDF(DensityFunction a, DensityFunction b) implements CompositeDensityFunction {
    public static final MapCodec<MultiplyDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("a").forGetter(MultiplyDF::a),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("b").forGetter(MultiplyDF::b)
    ).apply(i, MultiplyDF::new));
    static final KeyDispatchDataCodec<MultiplyDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) { return a.compute(ctx) * b.compute(ctx); }
    @Override public DensityFunction mapAll(Visitor v) { return v.apply(new MultiplyDF(a.mapAll(v), b.mapAll(v))); }
    @Override public double minValue() {
        double[] vals = { a.minValue() * b.minValue(), a.minValue() * b.maxValue(),
                a.maxValue() * b.minValue(), a.maxValue() * b.maxValue() };
        double min = vals[0]; for (double v : vals) if (v < min) min = v; return min;
    }
    @Override public double maxValue() {
        double[] vals = { a.minValue() * b.minValue(), a.minValue() * b.maxValue(),
                a.maxValue() * b.minValue(), a.maxValue() * b.maxValue() };
        double max = vals[0]; for (double v : vals) if (v > max) max = v; return max;
    }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
