package com.kuronami.isekaiapi.api.biomesource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderSet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Codec and no-context behaviour of the two world-state zones, {@code base_biome} and
 * {@code climate}. Decoding {@code base_biome} needs a real biome registry, so only the constructor
 * and the three-argument test are checked here; the registry-backed decode runs in
 * {@code BiomeZoneContextGameTests}.
 *
 * <p>Neither zone may match when the caller supplies no {@link ZoneContext} — a caller asking a
 * pure-coordinate question must get a definite answer rather than an exception.
 */
class BiomeZoneContextTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static DataResult<BiomeZone> parse(String json) {
        JsonElement el = JsonParser.parseString(json);
        return BiomeZone.CODEC.parse(JsonOps.INSTANCE, el);
    }

    private static BiomeZone decode(String json) {
        return parse(json).getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    @Test
    void climateWithoutAnyAxisIsRejected() {
        DataResult<BiomeZone> result = parse("{\"type\":\"isekai_api:climate\"}");
        assertTrue(result.error().isPresent(), "a climate zone without axes must not decode");
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains("climate"), message);
        assertTrue(message.contains("temperature"), message);
    }

    @Test
    void climateDecodesTheAxesItConstrains() {
        BiomeZone zone = decode("{\"type\":\"isekai_api:climate\","
                + "\"temperature\":[-0.5,0.5],\"depth\":[0.2,0.4]}");
        BiomeZone.ClimateMatch match = assertInstanceOf(BiomeZone.ClimateMatch.class, zone);
        assertEquals(Optional.of(Climate.Parameter.span(-0.5F, 0.5F)), match.temperature());
        assertEquals(Optional.of(Climate.Parameter.span(0.2F, 0.4F)), match.depth());
        assertEquals(Optional.empty(), match.humidity());
        assertEquals(Optional.empty(), match.continentalness());
        assertEquals(Optional.empty(), match.erosion());
        assertEquals(Optional.empty(), match.weirdness());
    }

    @Test
    void climateAxesRoundTripThroughJson() {
        BiomeZone original = decode("{\"type\":\"isekai_api:climate\","
                + "\"humidity\":[0.1,0.9],\"weirdness\":[-1.0,-0.25]}");
        String encoded = BiomeZone.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg)).toString();
        assertEquals(original, decode(encoded));
    }

    @Test
    void bothZonesAnswerFalseWithoutAContext() {
        BiomeZone baseBiome = new BiomeZone.BaseBiome(HolderSet.empty());
        BiomeZone climate = decode("{\"type\":\"isekai_api:climate\",\"temperature\":[-1.0,1.0]}");

        for (int[] at : new int[][] {{0, 0, 0}, {12, 4, -7}, {-100, 0, 100}}) {
            assertFalse(baseBiome.test(at[0], at[1], at[2]),
                    "base_biome must not match without a wrapped source");
            assertFalse(climate.test(at[0], at[1], at[2]),
                    "climate must not match without a sampler");
        }
        assertFalse(baseBiome.test(0, 0, 0, ZoneContext.NONE));
        assertFalse(climate.test(0, 0, 0, ZoneContext.NONE));
    }

    @Test
    void anEmptyContextAnswersNothing() {
        assertTrue(ZoneContext.NONE.baseBiome(0, 0, 0).isEmpty());
        assertTrue(ZoneContext.NONE.climate(0, 0, 0).isEmpty());
        assertTrue(ZoneContext.NONE.cachedBaseBiome().isEmpty());
        assertTrue(ZoneContext.forClimate(0, 0, 0, null).climate(0, 0, 0).isEmpty());
    }

    @Test
    void onlyTheDecidedPositionIsRemembered() {
        int[] calls = {0};
        ZoneContext ctx = new ZoneContext(5, 0, 7, (x, y, z) -> {
            calls[0]++;
            return null;
        }, null);
        // A jittered query first: computed, not remembered.
        ctx.baseBiome(6, 0, 7);
        assertEquals(1, calls[0]);
        assertTrue(ctx.cachedBaseBiome().isEmpty());
        // The decided position: asked once, then reused.
        ctx.baseBiome(5, 0, 7);
        ctx.baseBiome(5, 0, 7);
        assertEquals(2, calls[0]);
        ctx.baseBiome(6, 0, 7);
        assertEquals(3, calls[0]);
    }
}
