package com.kuronami.isekaiapi.api.remap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decode/encode contract for {@link ClientAtmosphereOverride}: every field is optional, an
 * absent block is a no-op, and the sky colour survives a round trip.
 */
class ClientAtmosphereCodecTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static ClientAtmosphereOverride decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        return ClientAtmosphereOverride.CODEC.parse(JsonOps.INSTANCE, el)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    @Test void emptyObjectIsNoOp() {
        ClientAtmosphereOverride v = decode("{}");
        assertTrue(v.isNoOp());
        assertEquals(Optional.empty(), v.skyColor());
    }

    @Test void skyColorDecodesAlone() {
        ClientAtmosphereOverride v = decode("{\"sky_color\":0}");
        assertEquals(Optional.of(0), v.skyColor());
        assertFalse(v.isNoOp());
        assertEquals(Optional.empty(), v.fogColor());
    }

    @Test void allFieldsDecode() {
        ClientAtmosphereOverride v = decode(
                "{\"sky_color\":13434879,\"fog_color\":16777215,"
                        + "\"fog_near_distance\":0.5,\"fog_far_distance\":64.0}");
        assertEquals(Optional.of(13434879), v.skyColor());
        assertEquals(Optional.of(16777215), v.fogColor());
        assertEquals(Optional.of(0.5f), v.fogNearDistance());
        assertEquals(Optional.of(64.0f), v.fogFarDistance());
    }

    @Test void roundTripsThroughEncode() {
        ClientAtmosphereOverride original = new ClientAtmosphereOverride(
                Optional.of(0x112233), Optional.of(0x334455), Optional.of(1.0f), Optional.of(96.0f));
        String json = ClientAtmosphereOverride.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg)).toString();
        assertTrue(json.contains("\"sky_color\":1122867"), json);
        assertTrue(json.contains("\"fog_color\":3359829"), json);
        assertEquals(original, decode(json));
    }
}
