package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Raw coordinate on the chosen axis. Primitive: spatial reference. */
public record CoordinateDF(Axis axis) implements DensityFunction.SimpleFunction {
    public static final MapCodec<CoordinateDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Axis.CODEC.fieldOf("axis").forGetter(CoordinateDF::axis)
    ).apply(i, CoordinateDF::new));
    public static final KeyDispatchDataCodec<CoordinateDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        return switch (axis) {
            case X -> ctx.blockX();
            case Y -> ctx.blockY();
            case Z -> ctx.blockZ();
        };
    }
    @Override public double minValue() { return -Double.MAX_VALUE; }
    @Override public double maxValue() { return Double.MAX_VALUE; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    public enum Axis implements StringRepresentable {
        X("x"), Y("y"), Z("z");
        public static final Codec<Axis> CODEC = StringRepresentable.fromEnum(Axis::values);
        private final String n;
        Axis(String n) { this.n = n; }
        @Override public String getSerializedName() { return n; }
    }
}
