package com.kuronami.isekaiapi.densityfunction;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Neutral density function primitives. Each primitive is a small mathematical / geometric
 * operation; consumers compose them to express arbitrary worldshapes.
 *
 * <p>Categories:
 * <ul>
 *   <li>Value sources: {@code constant}, {@code coordinate}</li>
 *   <li>Arithmetic: {@code add}, {@code multiply}, {@code negate}, {@code abs}</li>
 *   <li>Range: {@code clamp}</li>
 *   <li>Combinators: {@code min}, {@code max}, {@code lerp}, {@code step}</li>
 *   <li>Spatial reference: {@code distance}</li>
 *   <li>Coordinate transforms: {@code translate}, {@code scale_coord}, {@code repeat}</li>
 *   <li>Masks: {@code mask_y_range}</li>
 * </ul>
 *
 * <p>Vanilla density functions remain accessible via standard {@code minecraft:} keys —
 * Isekai does not re-export them.
 */
@ApiStatus.Internal
public final class IsekaiDensityFunctions {

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> CODECS =
            DeferredRegister.create(BuiltInRegistries.DENSITY_FUNCTION_TYPE, IsekaiApi.MODID);

    public static final Supplier<MapCodec<? extends DensityFunction>> CONSTANT =
            CODECS.register("constant", () -> ConstantDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> COORDINATE =
            CODECS.register("coordinate", () -> CoordinateDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> ADD =
            CODECS.register("add", () -> AddDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> MULTIPLY =
            CODECS.register("multiply", () -> MultiplyDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> NEGATE =
            CODECS.register("negate", () -> NegateDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> CLAMP =
            CODECS.register("clamp", () -> ClampDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> ABS =
            CODECS.register("abs", () -> AbsDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> MIN =
            CODECS.register("min", () -> MinDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> MAX =
            CODECS.register("max", () -> MaxDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> LERP =
            CODECS.register("lerp", () -> LerpDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> STEP =
            CODECS.register("step", () -> StepDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> DISTANCE =
            CODECS.register("distance", () -> DistanceDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> TRANSLATE =
            CODECS.register("translate", () -> TranslateDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> SCALE_COORD =
            CODECS.register("scale_coord", () -> ScaleCoordDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> REPEAT =
            CODECS.register("repeat", () -> RepeatDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> MASK_Y_RANGE =
            CODECS.register("mask_y_range", () -> MaskYRangeDF.CODEC);

    // Neutral worldshape composers — theme-agnostic, combine with the primitives above.
    public static final Supplier<MapCodec<? extends DensityFunction>> SQUEEZE =
            CODECS.register("squeeze", () -> SqueezeDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> Y_ENVELOPE =
            CODECS.register("y_envelope", () -> YEnvelopeDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> BLENDED_NOISE =
            CODECS.register("blended_noise", () -> BlendedNoiseDF.CODEC);
    public static final Supplier<MapCodec<? extends DensityFunction>> BAND_DENSITY =
            CODECS.register("band_density", () -> BandDensityDF.CODEC);

    private IsekaiDensityFunctions() {}

    public static void register(IEventBus modBus) {
        CODECS.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] density function primitives registered: 16 neutral + 4 worldshape (squeeze, y_envelope, blended_noise, band_density)");
    }
}
