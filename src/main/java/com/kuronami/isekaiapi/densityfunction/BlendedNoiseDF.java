package com.kuronami.isekaiapi.densityfunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import org.jetbrains.annotations.ApiStatus;

/**
 * Friendly wrapper around vanilla {@code old_blended_noise} with the {@code xz_scale} and
 * {@code y_scale} fixed at 0.25 (the value mainstream overworld-overlay mods converge on)
 * so consumers pick only the two intuitive knobs: feature size in xz and in y.
 *
 * <p>The underlying {@link BlendedNoise} is created via {@code createUnseeded(...)} and
 * re-seeded by the standard NoiseRouter pass — no consumer-visible seed handling.
 *
 * <p>Bigger {@code sizeXz} = wider, less fragmented features.
 * Bigger {@code sizeY} = taller / longer-vertical-period features.
 * This primitive is theme-neutral: it produces fractal 3D noise, nothing terrain-shaped
 * by itself. Combine it with {@code band_density} / {@code y_envelope} to give it a shape.
 */
@ApiStatus.Internal
public record BlendedNoiseDF(double sizeXz, double sizeY, double smearMultiplier,
                             DensityFunction inner) implements CompositeDensityFunction {

    public static final MapCodec<BlendedNoiseDF> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(1.0, 1000.0).fieldOf("size_xz").forGetter(BlendedNoiseDF::sizeXz),
            Codec.doubleRange(1.0, 1000.0).fieldOf("size_y").forGetter(BlendedNoiseDF::sizeY),
            Codec.doubleRange(1.0, 8.0).optionalFieldOf("smear_multiplier", 8.0).forGetter(BlendedNoiseDF::smearMultiplier)
    ).apply(i, BlendedNoiseDF::fromParams));
    static final KeyDispatchDataCodec<BlendedNoiseDF> KEY_CODEC = new KeyDispatchDataCodec<>(CODEC);

    public static BlendedNoiseDF fromParams(double sizeXz, double sizeY, double smearMultiplier) {
        // xz_scale / y_scale fixed at 0.25 — mainstream overworld overlays converge on this
        // value; exposing it adds a knob no one tunes correctly.
        BlendedNoise noise = BlendedNoise.createUnseeded(0.25, 0.25, sizeXz, sizeY, smearMultiplier);
        return new BlendedNoiseDF(sizeXz, sizeY, smearMultiplier, noise);
    }

    @Override public double compute(FunctionContext ctx) { return inner.compute(ctx); }
    @Override public DensityFunction mapAll(Visitor v) {
        return v.apply(new BlendedNoiseDF(sizeXz, sizeY, smearMultiplier, inner.mapAll(v)));
    }
    @Override public double minValue() { return inner.minValue(); }
    @Override public double maxValue() { return inner.maxValue(); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return KEY_CODEC; }
}
