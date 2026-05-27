package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Euclidean distance from point (refX, refY, refZ). {@link Mode} selects 2D (xz) or full 3D. */
public record DistanceDF(double refX, double refY, double refZ, Mode mode) implements DensityFunction.SimpleFunction {
    public static final MapCodec<DistanceDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("ref_x").forGetter(DistanceDF::refX),
            Codec.DOUBLE.fieldOf("ref_y").forGetter(DistanceDF::refY),
            Codec.DOUBLE.fieldOf("ref_z").forGetter(DistanceDF::refZ),
            Mode.CODEC.fieldOf("mode").forGetter(DistanceDF::mode)
    ).apply(i, DistanceDF::new));
    public static final KeyDispatchDataCodec<DistanceDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    @Override public double compute(FunctionContext ctx) {
        double dx = ctx.blockX() - refX, dz = ctx.blockZ() - refZ;
        if (mode == Mode.XZ) return Math.sqrt(dx * dx + dz * dz);
        double dy = ctx.blockY() - refY;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    @Override public double minValue() { return 0; }
    @Override public double maxValue() { return Double.MAX_VALUE; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }

    public enum Mode implements StringRepresentable {
        XZ("xz"), XYZ("xyz");
        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);
        private final String n;
        Mode(String n) { this.n = n; }
        @Override public String getSerializedName() { return n; }
    }
}
