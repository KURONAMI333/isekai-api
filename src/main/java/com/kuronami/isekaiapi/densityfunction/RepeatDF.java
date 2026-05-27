package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Tile {@code f} periodically in the XZ plane with periods {@code periodX, periodZ}.
 * Effectively maps {@code (x, z)} to {@code (x mod periodX, z mod periodZ)} before sampling.
 */
public record RepeatDF(DensityFunction f, double periodX, double periodZ) implements DensityFunction.SimpleFunction {
    public static final MapCodec<RepeatDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(RepeatDF::f),
            Codec.DOUBLE.fieldOf("period_x").forGetter(RepeatDF::periodX),
            Codec.DOUBLE.fieldOf("period_z").forGetter(RepeatDF::periodZ)
    ).apply(i, RepeatDF::new));
    public static final KeyDispatchDataCodec<RepeatDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return f.compute(new TiledContext(ctx, periodX, periodZ));
    }
    @Override public double minValue() { return f.minValue(); }
    @Override public double maxValue() { return f.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    private record TiledContext(FunctionContext inner, double periodX, double periodZ) implements FunctionContext {
        @Override public int blockX() {
            double m = ((inner.blockX() % periodX) + periodX) % periodX; return (int) m;
        }
        @Override public int blockY() { return inner.blockY(); }
        @Override public int blockZ() {
            double m = ((inner.blockZ() % periodZ) + periodZ) % periodZ; return (int) m;
        }
    }
}
