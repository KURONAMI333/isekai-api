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
 * Registry-backed dispatch round-trips for {@link TransitionRule} (canonical + legacy prefix +
 * encode). Requires a bootstrap for the SPI registries.
 */
class TransitionRuleCodecTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static TransitionRule decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        DataResult<TransitionRule> r = TransitionRule.CODEC.parse(JsonOps.INSTANCE, el);
        return r.getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    private static String encode(TransitionRule t) {
        return TransitionRule.CODEC.encodeStart(JsonOps.INSTANCE, t)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg)).toString();
    }

    @Test void allVariantsDecodeCanonical() {
        assertInstanceOf(TransitionRule.Hard.class, decode("{\"type\":\"isekai_api:hard\"}"));
        assertInstanceOf(TransitionRule.Blend.class, decode("{\"type\":\"isekai_api:blend\",\"blend_height\":4}"));
        assertInstanceOf(TransitionRule.Gap.class, decode("{\"type\":\"isekai_api:gap\",\"gap_height\":3}"));
    }

    @Test void legacyPrefixDecodes() {
        assertEquals(new TransitionRule.Blend(4), decode("{\"type\":\"isekai:blend\",\"blend_height\":4}"));
        assertInstanceOf(TransitionRule.Hard.class, decode("{\"type\":\"isekai:hard\"}"));
    }

    @Test void encodeUsesCanonicalPrefix() {
        String json = encode(new TransitionRule.Gap(3));
        assertTrue(json.contains("isekai_api:gap"), json);
        assertFalse(json.contains("\"isekai:"), json);
    }
}
