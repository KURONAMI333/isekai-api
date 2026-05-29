package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.ApiStatus;

/**
 * Raw coordinate on the chosen axis. Primitive: spatial reference.
 *
 * <p>{@link #minValue()} / {@link #maxValue()} are bounded by the world border (XZ) and
 * a wide safe Y window. Using {@code ±Double.MAX_VALUE} here would cause {@code Infinity}
 * propagation through {@link MultiplyDF}'s range arithmetic and poison the noise router's
 * branch pruning. The 30M XZ bound matches vanilla {@code WorldBorder.MAX_SIZE}; the Y
 * bound spans the widest plausible dimension height.
 */
@ApiStatus.Internal
public record CoordinateDF(Axis axis) implements DensityFunction.SimpleFunction {
    /** Vanilla {@code WorldBorder.MAX_SIZE / 2} — the absolute horizontal world bound. */
    private static final double XZ_BOUND = 30_000_000.0;
    /** Conservative Y bound that fits every plausible dimension height (vanilla overworld is 384). */
    private static final double Y_BOUND = 4_096.0;

    public static final MapCodec<CoordinateDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Axis.CODEC.fieldOf("axis").forGetter(CoordinateDF::axis)
    ).apply(i, CoordinateDF::new));
    static final KeyDispatchDataCodec<CoordinateDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return switch (axis) {
            case X -> ctx.blockX();
            case Y -> ctx.blockY();
            case Z -> ctx.blockZ();
        };
    }
    @Override public double minValue() { return axis == Axis.Y ? -Y_BOUND : -XZ_BOUND; }
    @Override public double maxValue() { return axis == Axis.Y ? Y_BOUND : XZ_BOUND; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    public enum Axis implements StringRepresentable {
        X("x"), Y("y"), Z("z");
        public static final Codec<Axis> CODEC = StringRepresentable.fromEnum(Axis::values);
        private final String n;
        Axis(String n) { this.n = n; }
        @Override public String getSerializedName() { return n; }
    }
}
