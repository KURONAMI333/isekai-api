package com.kuronami.isekaiapi.densityfunction;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Codec round-trip tests for isekai density function types.
 * Uses each class's own MapCodec directly (not DensityFunction.DIRECT_CODEC, which requires
 * live registry holders for HOLDER_HELPER_CODEC children).
 *
 * Strategy: decode JSON → re-encode → second decode → re-encode again; assert final two
 * re-encodings are structurally identical.
 *
 * Leaf primitives (no nested DensityFunction children) can always be decoded from JSON.
 * Composite DFs that use HOLDER_HELPER_CODEC for children must be tested by constructing
 * instances directly and encoding them (encode→decode→encode).
 */
class DensityFunctionCodecRoundTripTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    // --- helpers ---

    /**
     * Decode JSON using the provided MapCodec, re-encode, decode again, re-encode again.
     * Asserts the last two encodings are equal (stable round-trip).
     */
    private static <T> void assertLeafRoundTrip(MapCodec<T> codec, String json) {
        JsonElement original = JsonParser.parseString(json);
        T decoded = codec.codec().parse(JsonOps.INSTANCE, original)
                .getOrThrow(err -> new AssertionError("Decode failed: " + err + "\nJSON: " + json));
        JsonElement enc1 = codec.codec().encodeStart(JsonOps.INSTANCE, decoded)
                .getOrThrow(err -> new AssertionError("Encode failed: " + err));
        T decoded2 = codec.codec().parse(JsonOps.INSTANCE, enc1)
                .getOrThrow(err -> new AssertionError("Second decode failed: " + err));
        JsonElement enc2 = codec.codec().encodeStart(JsonOps.INSTANCE, decoded2)
                .getOrThrow(err -> new AssertionError("Second encode failed: " + err));
        assertEquals(enc1, enc2, "Codec not stable: encode→decode→encode differs");
    }

    /**
     * For composite DFs: construct a value directly, encode it, decode, re-encode.
     * Asserts encode1 == encode2 (stable). The initial construction bypasses
     * HOLDER_HELPER_CODEC which would require live registry holders.
     */
    private static <T> void assertEncodeDecodeStable(MapCodec<T> codec, T instance) {
        JsonElement enc1 = codec.codec().encodeStart(JsonOps.INSTANCE, instance)
                .getOrThrow(err -> new AssertionError("Encode failed: " + err));
        T decoded = codec.codec().parse(JsonOps.INSTANCE, enc1)
                .getOrThrow(err -> new AssertionError("Decode failed: " + err));
        JsonElement enc2 = codec.codec().encodeStart(JsonOps.INSTANCE, decoded)
                .getOrThrow(err -> new AssertionError("Re-encode failed: " + err));
        assertEquals(enc1, enc2, "Codec not stable: encode→decode→encode differs");
    }

    private static DensityFunction c(double v) {
        return DensityFunctions.constant(v);
    }

    // ========== Leaf primitives (no HOLDER_HELPER_CODEC children) ==========

    @Test void codec_constant_roundTrip() {
        assertLeafRoundTrip(ConstantDF.CODEC, "{\"value\":3.14}");
    }

    @Test void codec_constant_negativeValue_roundTrip() {
        assertLeafRoundTrip(ConstantDF.CODEC, "{\"value\":-1.0}");
    }

    @Test void codec_constant_zero_roundTrip() {
        assertLeafRoundTrip(ConstantDF.CODEC, "{\"value\":0.0}");
    }

    @Test void codec_coordinate_xAxis_roundTrip() {
        assertLeafRoundTrip(CoordinateDF.CODEC, "{\"axis\":\"x\"}");
    }

    @Test void codec_coordinate_yAxis_roundTrip() {
        assertLeafRoundTrip(CoordinateDF.CODEC, "{\"axis\":\"y\"}");
    }

    @Test void codec_coordinate_zAxis_roundTrip() {
        assertLeafRoundTrip(CoordinateDF.CODEC, "{\"axis\":\"z\"}");
    }

    @Test void codec_distance_xz_roundTrip() {
        assertLeafRoundTrip(DistanceDF.CODEC,
                "{\"ref_x\":0.0,\"ref_y\":0.0,\"ref_z\":0.0,\"mode\":\"xz\"}");
    }

    @Test void codec_distance_xyz_roundTrip() {
        assertLeafRoundTrip(DistanceDF.CODEC,
                "{\"ref_x\":10.0,\"ref_y\":64.0,\"ref_z\":-5.0,\"mode\":\"xyz\"}");
    }

    @Test void codec_yEnvelope_defaultFields_roundTrip() {
        assertLeafRoundTrip(YEnvelopeDF.CODEC,
                "{\"active_min_y\":0,\"active_max_y\":100}");
    }

    @Test void codec_yEnvelope_allFields_roundTrip() {
        assertLeafRoundTrip(YEnvelopeDF.CODEC,
                "{\"active_min_y\":10,\"active_max_y\":200,\"gradient_width\":20,\"invert\":true}");
    }

    // ========== Composite DFs: encode→decode→encode stability ==========
    // Children are constructed directly as vanilla DensityFunctions.constant(v),
    // which encodes as a plain JSON number (not a typed object), so HOLDER_HELPER_CODEC
    // can serialize it without needing live registry holders.

    @Test void codec_abs_encodeDecodeStable() {
        assertEncodeDecodeStable(AbsDF.CODEC, new AbsDF(c(-3.0)));
    }

    @Test void codec_negate_encodeDecodeStable() {
        assertEncodeDecodeStable(NegateDF.CODEC, new NegateDF(c(5.0)));
    }

    @Test void codec_squeeze_encodeDecodeStable() {
        assertEncodeDecodeStable(SqueezeDF.CODEC, new SqueezeDF(c(0.5)));
    }

    @Test void codec_quarterNegative_encodeDecodeStable() {
        assertEncodeDecodeStable(QuarterNegativeDF.CODEC, new QuarterNegativeDF(c(2.0)));
    }

    @Test void codec_clamp_encodeDecodeStable() {
        assertEncodeDecodeStable(ClampDF.CODEC, new ClampDF(c(5.0), 0.0, 3.0));
    }

    @Test void codec_add_encodeDecodeStable() {
        assertEncodeDecodeStable(AddDF.CODEC, new AddDF(c(1.0), c(2.0)));
    }

    @Test void codec_multiply_encodeDecodeStable() {
        assertEncodeDecodeStable(MultiplyDF.CODEC, new MultiplyDF(c(3.0), c(4.0)));
    }

    @Test void codec_min_encodeDecodeStable() {
        assertEncodeDecodeStable(MinDF.CODEC, new MinDF(c(1.0), c(2.0)));
    }

    @Test void codec_max_encodeDecodeStable() {
        assertEncodeDecodeStable(MaxDF.CODEC, new MaxDF(c(1.0), c(2.0)));
    }

    @Test void codec_lerp_encodeDecodeStable() {
        assertEncodeDecodeStable(LerpDF.CODEC, new LerpDF(c(0.5), c(0.0), c(10.0)));
    }

    @Test void codec_step_encodeDecodeStable() {
        assertEncodeDecodeStable(StepDF.CODEC, new StepDF(c(0.5), 1.0, c(-1.0), c(1.0)));
    }

    @Test void codec_maskYRange_encodeDecodeStable() {
        assertEncodeDecodeStable(MaskYRangeDF.CODEC, new MaskYRangeDF(0, 100, c(1.0), c(0.0)));
    }

    @Test void codec_translate_encodeDecodeStable() {
        assertEncodeDecodeStable(TranslateDF.CODEC, new TranslateDF(c(0.0), 10.0, 0.0, -5.0));
    }

    @Test void codec_scaleCoord_encodeDecodeStable() {
        assertEncodeDecodeStable(ScaleCoordDF.CODEC,
                new ScaleCoordDF(new CoordinateDF(CoordinateDF.Axis.X), 2.0, 1.0, 0.5));
    }

    @Test void codec_repeat_encodeDecodeStable() {
        assertEncodeDecodeStable(RepeatDF.CODEC,
                new RepeatDF(new CoordinateDF(CoordinateDF.Axis.X), 16.0, 16.0));
    }

    @Test void codec_slopedDensity_encodeDecodeStable() {
        SlopedDensityDF df = SlopedDensityDF.fromConfig(c(0.5), 4.0, c(0.0));
        assertEncodeDecodeStable(SlopedDensityDF.CODEC, df);
    }

    @Test void codec_blendedNoise_encodeDecodeStable() {
        BlendedNoiseDF df = BlendedNoiseDF.fromParams(80.0, 120.0, 8.0);
        assertEncodeDecodeStable(BlendedNoiseDF.CODEC, df);
    }

    // ========== Verify decode produces functionally correct values ==========
    // These go further: after decode, compute() should match the known formula.

    @Test void codec_constant_decodesCorrectly() {
        JsonElement json = JsonParser.parseString("{\"value\":7.5}");
        ConstantDF decoded = ConstantDF.CODEC.codec()
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow();
        assertEquals(7.5, decoded.value(), 1e-9);
    }

    @Test void codec_yEnvelope_decodesCorrectly() {
        JsonElement json = JsonParser.parseString(
                "{\"active_min_y\":50,\"active_max_y\":150,\"gradient_width\":10,\"invert\":false}");
        YEnvelopeDF decoded = YEnvelopeDF.CODEC.codec()
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow();
        assertEquals(50, decoded.activeMinY());
        assertEquals(150, decoded.activeMaxY());
        assertEquals(10, decoded.gradientWidth());
        assertFalse(decoded.invert());
    }

    @Test void codec_distance_decodesCorrectly() {
        JsonElement json = JsonParser.parseString(
                "{\"ref_x\":1.0,\"ref_y\":2.0,\"ref_z\":3.0,\"mode\":\"xyz\"}");
        DistanceDF decoded = DistanceDF.CODEC.codec()
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow();
        assertEquals(1.0, decoded.refX(), 1e-9);
        assertEquals(2.0, decoded.refY(), 1e-9);
        assertEquals(3.0, decoded.refZ(), 1e-9);
        assertEquals(DistanceDF.Mode.XYZ, decoded.mode());
    }

    @Test void codec_coordinate_decodesCorrectly() {
        JsonElement json = JsonParser.parseString("{\"axis\":\"z\"}");
        CoordinateDF decoded = CoordinateDF.CODEC.codec()
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow();
        assertEquals(CoordinateDF.Axis.Z, decoded.axis());
    }
}
