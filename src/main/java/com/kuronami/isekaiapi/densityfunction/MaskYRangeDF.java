package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Y-band mask: returns {@code inside} when {@code ymin <= y <= ymax}, else {@code outside}.
 * Primitive for layered constructions (multi-layer worlds, surface bands, etc.).
 */
public record MaskYRangeDF(int ymin, int ymax, DensityFunction inside, DensityFunction outside) implements DensityFunction.SimpleFunction {
    public static final MapCodec<MaskYRangeDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("ymin").forGetter(MaskYRangeDF::ymin),
            Codec.INT.fieldOf("ymax").forGetter(MaskYRangeDF::ymax),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("inside").forGetter(MaskYRangeDF::inside),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("outside").forGetter(MaskYRangeDF::outside)
    ).apply(i, MaskYRangeDF::new));
    public static final KeyDispatchDataCodec<MaskYRangeDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        int y = ctx.blockY();
        return (y >= ymin && y <= ymax) ? inside.compute(ctx) : outside.compute(ctx);
    }
    @Override public double minValue() { return Math.min(inside.minValue(), outside.minValue()); }
    @Override public double maxValue() { return Math.max(inside.maxValue(), outside.maxValue()); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
