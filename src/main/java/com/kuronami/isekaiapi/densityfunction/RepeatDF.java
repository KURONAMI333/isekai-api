package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/**
 * Tile {@code f} periodically in the XZ plane with periods {@code periodX, periodZ}.
 * Effectively maps {@code (x, z)} to {@code (x mod periodX, z mod periodZ)} before sampling.
 *
 * <p>Periods must be {@code > 0} — zero or negative would produce {@code NaN} terrain via
 * division-by-zero in the modulo and is rejected at codec decode time.
 */
@ApiStatus.Internal
public record RepeatDF(DensityFunction f, double periodX, double periodZ) implements CompositeDensityFunction {
    public static final MapCodec<RepeatDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("f").forGetter(RepeatDF::f),
            Codec.doubleRange(0.0001, Double.MAX_VALUE).fieldOf("period_x").forGetter(RepeatDF::periodX),
            Codec.doubleRange(0.0001, Double.MAX_VALUE).fieldOf("period_z").forGetter(RepeatDF::periodZ)
    ).apply(i, RepeatDF::new));
    static final KeyDispatchDataCodec<RepeatDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return f.compute(new TiledContext(ctx, periodX, periodZ));
    }
    @Override public DensityFunction mapAll(Visitor v) { return v.apply(new RepeatDF(f.mapAll(v), periodX, periodZ)); }
    @Override public double minValue() { return f.minValue(); }
    @Override public double maxValue() { return f.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    private record TiledContext(FunctionContext inner, double periodX, double periodZ) implements FunctionContext {
        // Mth.floor for negative coords: (int)(-0.5) == 0 but Mth.floor(-0.5) == -1, so use the
        // latter to keep the tiling monotonic and avoid a seam at x=0/z=0.
        @Override public int blockX() {
            double m = ((inner.blockX() % periodX) + periodX) % periodX; return Mth.floor(m);
        }
        @Override public int blockY() { return inner.blockY(); }
        @Override public int blockZ() {
            double m = ((inner.blockZ() % periodZ) + periodZ) % periodZ; return Mth.floor(m);
        }
    }
}
