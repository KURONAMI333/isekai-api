package com.kuronami.isekaiapi.surfacerule;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@code isekai_api:worldshape_surface_top} is self-gating: {@link WorldshapeSurfaceTopRule#create}
 * wraps the per-biome lookup in vanilla's {@code ON_FLOOR} {@code stone_depth} condition, so a
 * consumer prepends the rule bare and it touches only the surface layer. These tests lock that the
 * gate lives inside the rule (not in consumer JSON) and does not leak into serialization.
 *
 * <p>The runtime block-for-block behavior (top = override, block below = untouched) is the W5 RCON
 * block-probe on a real dedicated server — GameTest-unreachable because the custom dimension isn't
 * instantiated there.
 */
class WorldshapeSurfaceTopRuleTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static final ResourceKey<Level> DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

    @Test
    void createWrapsLookupInAConditionGate() {
        WorldshapeSurfaceTopRule rule = WorldshapeSurfaceTopRule.create(DIM);
        assertNotNull(rule.gated(), "create() must build the gated tree");
        // The gate is vanilla's condition rule source (ifTrue) — not the raw lookup. Its simple name
        // is version-pinned (1.21.1) and precisely identifies the condition wrapper.
        assertEquals("TestRuleSource", rule.gated().getClass().getSimpleName(),
                "gated rule must be a vanilla condition (ifTrue) wrapper, got " + rule.gated().getClass());
        // A vanilla condition rule has a working codec; the inner un-serializable lookup would throw.
        assertDoesNotThrow(() -> rule.gated().codec(),
                "gated must be the vanilla condition rule (serializable), not the throwing inner lookup");
    }

    @Test
    void serializationIsBareDimensionOnly() {
        // Encoding through the dispatch codec must emit only the dimension — the internal ON_FLOOR
        // gate never leaks to disk, so datapacks stay bare (no stone_depth wrapper).
        WorldshapeSurfaceTopRule rule = WorldshapeSurfaceTopRule.create(DIM);
        JsonElement json = SurfaceRules.RuleSource.CODEC.encodeStart(JsonOps.INSTANCE, rule)
                .getOrThrow(msg -> new AssertionError("encode: " + msg));
        JsonObject obj = json.getAsJsonObject();
        assertEquals("isekai_api:worldshape_surface_top", obj.get("type").getAsString());
        assertEquals("minecraft:overworld", obj.get("dimension").getAsString());
        assertFalse(json.toString().contains("stone_depth"),
                "the internal gate must not appear in serialization: " + json);
    }

    @Test
    void bareJsonDecodesAndRebuildsTheGate() {
        // On-disk bare wiring loads back self-gated: decode rebuilds the ON_FLOOR gate via create().
        JsonElement json = JsonParser.parseString(
                "{\"type\":\"isekai_api:worldshape_surface_top\",\"dimension\":\"minecraft:overworld\"}");
        SurfaceRules.RuleSource decoded = SurfaceRules.RuleSource.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new AssertionError("decode: " + msg));
        WorldshapeSurfaceTopRule rule = assertInstanceOf(WorldshapeSurfaceTopRule.class, decoded);
        assertEquals(DIM, rule.dimension());
        assertEquals("TestRuleSource", rule.gated().getClass().getSimpleName(),
                "decoded rule must be self-gated");
    }
}
