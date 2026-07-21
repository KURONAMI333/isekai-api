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
 * Registry-backed dispatch round-trips for {@link SurfaceAnchor} (canonical + legacy prefix +
 * encode), plus the pure {@link SurfaceAnchor.FixedY#resolveY} behavior. Requires a bootstrap
 * for the SPI registries and the fluid registry ({@code below_fluid}).
 */
class SurfaceAnchorCodecTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static SurfaceAnchor decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        DataResult<SurfaceAnchor> r = SurfaceAnchor.CODEC.parse(JsonOps.INSTANCE, el);
        return r.getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    private static String encode(SurfaceAnchor a) {
        return SurfaceAnchor.CODEC.encodeStart(JsonOps.INSTANCE, a)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg)).toString();
    }

    @Test void allVariantsDecodeCanonical() {
        assertInstanceOf(SurfaceAnchor.WorldSurface.class, decode("{\"type\":\"isekai_api:world_surface\"}"));
        assertInstanceOf(SurfaceAnchor.FixedY.class, decode("{\"type\":\"isekai_api:fixed_y\",\"y\":64}"));
        assertInstanceOf(SurfaceAnchor.BelowFluid.class, decode("{\"type\":\"isekai_api:below_fluid\",\"fluid\":\"minecraft:water\"}"));
    }

    @Test void legacyPrefixDecodes() {
        assertEquals(new SurfaceAnchor.FixedY(150), decode("{\"type\":\"isekai:fixed_y\",\"y\":150}"));
        assertInstanceOf(SurfaceAnchor.WorldSurface.class, decode("{\"type\":\"isekai:world_surface\"}"));
    }

    @Test void encodeUsesCanonicalPrefix() {
        String json = encode(new SurfaceAnchor.FixedY(150));
        assertTrue(json.contains("isekai_api:fixed_y"), json);
        assertFalse(json.contains("\"isekai:"), json);
    }

    @Test void fixedY_resolvesToItsY() {
        // FixedY ignores the world context, so a null ctx/pos is a valid pure-logic probe.
        assertEquals(150, new SurfaceAnchor.FixedY(150).resolveY(null, null));
    }
}
