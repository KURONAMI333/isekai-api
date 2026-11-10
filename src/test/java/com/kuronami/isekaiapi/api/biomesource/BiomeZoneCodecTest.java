package com.kuronami.isekaiapi.api.biomesource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registry-backed dispatch round-trips for {@link BiomeZone}'s geometric + combinator variants
 * under the canonical {@code isekai_api:} prefix and the legacy {@code isekai:} alias. The noise
 * variants (noise_threshold / edge_jitter) need a NoiseParameters registry lookup and are covered
 * by their existing construction tests; the dispatch mechanism is fully exercised by the variants
 * here (they include the recursive combinators). Requires a bootstrap for the SPI registries.
 */
class BiomeZoneCodecTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static BiomeZone decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        DataResult<BiomeZone> r = BiomeZone.CODEC.parse(JsonOps.INSTANCE, el);
        return r.getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    private static String encode(BiomeZone z) {
        return BiomeZone.CODEC.encodeStart(JsonOps.INSTANCE, z)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg)).toString();
    }

    @Test void geometricAndCombinatorVariantsDecodeCanonical() {
        assertInstanceOf(BiomeZone.Always.class, decode("{\"type\":\"isekai_api:always\"}"));
        assertInstanceOf(BiomeZone.YAbove.class, decode("{\"type\":\"isekai_api:y_above\",\"y\":64}"));
        assertInstanceOf(BiomeZone.YBelow.class, decode("{\"type\":\"isekai_api:y_below\",\"y\":64}"));
        assertInstanceOf(BiomeZone.YBetween.class, decode("{\"type\":\"isekai_api:y_between\",\"min\":0,\"max\":100}"));
        assertInstanceOf(BiomeZone.WithinDistance.class, decode("{\"type\":\"isekai_api:within_distance\",\"radius\":1000.0}"));
        assertInstanceOf(BiomeZone.BeyondDistance.class, decode("{\"type\":\"isekai_api:beyond_distance\",\"radius\":1000.0}"));
        assertInstanceOf(BiomeZone.And.class, decode("{\"type\":\"isekai_api:and\",\"all\":[]}"));
        assertInstanceOf(BiomeZone.Or.class, decode("{\"type\":\"isekai_api:or\",\"any\":[]}"));
        assertInstanceOf(BiomeZone.Not.class, decode("{\"type\":\"isekai_api:not\",\"inner\":{\"type\":\"isekai_api:always\"}}"));
    }

    @Test void legacyPrefixDecodes() {
        assertEquals(new BiomeZone.YAbove(64), decode("{\"type\":\"isekai:y_above\",\"y\":64}"));
        assertEquals(
                decode("{\"type\":\"isekai:and\",\"all\":[{\"type\":\"isekai:always\"}]}"),
                decode("{\"type\":\"isekai_api:and\",\"all\":[{\"type\":\"isekai_api:always\"}]}"));
    }

    @Test void encodeUsesCanonicalPrefix() {
        String json = encode(new BiomeZone.YAbove(64));
        assertTrue(json.contains("isekai_api:y_above"), json);
        assertFalse(json.contains("\"isekai:"), json);
    }

    @Test void boxDecodesWithAndWithoutVerticalBounds() {
        assertEquals(new BiomeZone.Box(-64, 64, -32, 32, java.util.Optional.of(-16),
                        java.util.Optional.of(96)),
                decode("{\"type\":\"isekai_api:box\",\"min_x\":-64,\"max_x\":64,"
                        + "\"min_z\":-32,\"max_z\":32,\"min_y\":-16,\"max_y\":96}"));
        assertEquals(new BiomeZone.Box(0, 15, 0, 15, java.util.Optional.empty(),
                        java.util.Optional.empty()),
                decode("{\"type\":\"isekai_api:box\",\"min_x\":0,\"max_x\":15,"
                        + "\"min_z\":0,\"max_z\":15}"));
        assertEquals(new BiomeZone.Box(0, 15, 0, 15, java.util.Optional.of(64),
                        java.util.Optional.empty()),
                decode("{\"type\":\"isekai_api:box\",\"min_x\":0,\"max_x\":15,"
                        + "\"min_z\":0,\"max_z\":15,\"min_y\":64}"));
    }

    @Test void chunkBoxRoundTrip() {
        BiomeZone original = new BiomeZone.ChunkBox(-3, 3, -2, 5);
        assertEquals(original, decode(encode(original)));
        assertEquals(new BiomeZone.ChunkBox(0, 0, 0, 0),
                decode("{\"type\":\"isekai_api:chunk_box\",\"min_chunk_x\":0,"
                        + "\"max_chunk_x\":0,\"min_chunk_z\":0,\"max_chunk_z\":0}"));
    }

    @Test void invertedBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BiomeZone.Box(10, 0, 0, 10, java.util.Optional.empty(), java.util.Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new BiomeZone.Box(0, 10, 10, 0, java.util.Optional.empty(), java.util.Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new BiomeZone.Box(0, 10, 0, 10, java.util.Optional.of(96), java.util.Optional.of(-16)));
        assertThrows(IllegalArgumentException.class, () -> new BiomeZone.ChunkBox(5, 4, 0, 0));
    }

    @Test void nestedRoundTrip() {
        BiomeZone original = new BiomeZone.And(java.util.List.of(
                new BiomeZone.YAbove(0), new BiomeZone.Not(new BiomeZone.WithinDistance(500.0, 10, 20))));
        assertEquals(original, decode(encode(original)));
    }
}
