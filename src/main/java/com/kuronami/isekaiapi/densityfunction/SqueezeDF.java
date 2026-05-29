package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/**
 * Tone-compression primitive: clamp to [-1, 1] then map to {@code x/2 - x^3/24}. Mirrors
 * vanilla's {@code minecraft:squeeze} (a {@link net.minecraft.world.level.levelgen.DensityFunctions.Mapped}
 * with {@code Mapped.Type.SQUEEZE}), provided here because {@code Mapped} is package-private
 * in vanilla and can't be constructed programmatically from outside its package.
 *
 * <p>Used as the final-density tone shaper in the standard overworld pipeline — wrapping
 * the post-{@code blend_density} result with squeeze softens extreme-magnitude noise so
 * cave/wall surfaces don't become spiky.
 */
@ApiStatus.Internal
public record SqueezeDF(DensityFunction inner) implements CompositeDensityFunction {

    public static final MapCodec<SqueezeDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(SqueezeDF::inner)
    ).apply(i, SqueezeDF::new));
    static final KeyDispatchDataCodec<SqueezeDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override
    public double compute(FunctionContext ctx) {
        double clamped = Mth.clamp(inner.compute(ctx), -1.0, 1.0);
        return clamped / 2.0 - clamped * clamped * clamped / 24.0;
    }

    @Override
    public DensityFunction mapAll(Visitor v) {
        return v.apply(new SqueezeDF(inner.mapAll(v)));
    }

    @Override
    public double minValue() {
        // Squeeze on [-1, 1] is monotone-increasing, so image extremes are at -1 and 1:
        //   squeeze(-1) = -1/2 - (-1)/24 = -0.4583...
        //   squeeze( 1) =  1/2 -    1/24 =  0.4583...
        return -11.0 / 24.0;
    }
    @Override
    public double maxValue() {
        return 11.0 / 24.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
