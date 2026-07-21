package com.kuronami.isekaiapi.api.remap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registry-backed dispatch round-trips for {@link RemapStrategy}: every built-in variant decodes
 * under the canonical {@code isekai_api:} prefix and the legacy {@code isekai:} alias, and encoding
 * emits the canonical prefix. Requires a game bootstrap so the SPI registries exist.
 */
class RemapStrategyCodecTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static RemapStrategy decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        DataResult<RemapStrategy> r = RemapStrategy.CODEC.parse(JsonOps.INSTANCE, el);
        return r.getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    private static String encode(RemapStrategy s) {
        return RemapStrategy.CODEC.encodeStart(JsonOps.INSTANCE, s)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg)).toString();
    }

    @Test void allVariantsDecodeCanonical() {
        assertInstanceOf(RemapStrategy.Linear.class, decode("{\"type\":\"isekai_api:linear\"}"));
        assertInstanceOf(RemapStrategy.Inverted.class, decode("{\"type\":\"isekai_api:inverted\"}"));
        assertInstanceOf(RemapStrategy.Identity.class, decode("{\"type\":\"isekai_api:identity\"}"));
        assertInstanceOf(RemapStrategy.CountScale.class, decode("{\"type\":\"isekai_api:count_scale\",\"factor\":0.5}"));
        assertInstanceOf(RemapStrategy.FixedRange.class, decode("{\"type\":\"isekai_api:fixed_range\",\"min\":0,\"max\":100,\"dist\":\"uniform\"}"));
        assertInstanceOf(RemapStrategy.BandSplit.class, decode(
                "{\"type\":\"isekai_api:band_split\",\"bands\":[{\"vanilla_source\":{\"min_y\":-64,\"max_y\":320,\"distribution\":\"uniform\"},\"target_ratio\":1.0}]}"));
        assertInstanceOf(RemapStrategy.Pipe.class, decode(
                "{\"type\":\"isekai_api:pipe\",\"chain\":[{\"type\":\"isekai_api:identity\"}]}"));
    }

    @Test void legacyPrefixDecodes() {
        assertInstanceOf(RemapStrategy.Linear.class, decode("{\"type\":\"isekai:linear\"}"));
        assertEquals(
                decode("{\"type\":\"isekai:pipe\",\"chain\":[{\"type\":\"isekai:identity\"}]}"),
                decode("{\"type\":\"isekai_api:pipe\",\"chain\":[{\"type\":\"isekai_api:identity\"}]}"));
    }

    @Test void encodeUsesCanonicalPrefix() {
        String json = encode(new RemapStrategy.CountScale(0.5));
        assertTrue(json.contains("isekai_api:count_scale"), json);
        assertFalse(json.contains("\"isekai:"), json);
    }

    @Test void nestedPipeRoundTrip() {
        RemapStrategy original = new RemapStrategy.Pipe(java.util.List.of(
                RemapStrategy.Linear.INSTANCE, new RemapStrategy.CountScale(2.0)));
        assertEquals(original, decode(encode(original)));
    }
}
