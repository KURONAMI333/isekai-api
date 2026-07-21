package com.kuronami.isekaiapi.surfacerule;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code isekai_api:vanilla_overworld_surface} delegate: it builds (the full vanilla
 * overworld tree reconstructs from runtime factories without error) and round-trips through
 * both its own {@link net.minecraft.util.KeyDispatchDataCodec} and the dispatching
 * {@link SurfaceRules.RuleSource#CODEC} once the mod's codec is registered.
 */
class VanillaOverworldSurfaceRuleTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    @Test
    void treeBuildsFromRuntimeFactories() {
        // Reconstruction touches only net.minecraft.world.level.levelgen.* (runtime), never the
        // datagen SurfaceRuleData — building it must not throw or NoClassDefFound.
        assertNotNull(VanillaOverworldSurface.tree());
        assertSame(VanillaOverworldSurface.tree(), VanillaOverworldSurface.tree(), "tree is cached");
    }

    @Test
    void unitCodecRoundTrip() {
        var codec = VanillaOverworldSurfaceRule.CODEC.codec();
        JsonElement enc = codec.encodeStart(JsonOps.INSTANCE, VanillaOverworldSurfaceRule.INSTANCE)
                .getOrThrow(msg -> new AssertionError("encode: " + msg));
        VanillaOverworldSurfaceRule dec = codec.parse(JsonOps.INSTANCE, enc)
                .getOrThrow(msg -> new AssertionError("decode: " + msg));
        assertSame(VanillaOverworldSurfaceRule.INSTANCE, dec);
    }

    @Test
    void dispatchesFromRuleSourceCodec() {
        // The registry-backed MATERIAL_RULE dispatch resolves isekai_api:vanilla_overworld_surface
        // (the mod is registered by the unitTest harness).
        JsonElement json = JsonParser.parseString("{\"type\":\"isekai_api:vanilla_overworld_surface\"}");
        SurfaceRules.RuleSource decoded = SurfaceRules.RuleSource.CODEC
                .parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new AssertionError("dispatch decode: " + msg));
        assertTrue(decoded instanceof VanillaOverworldSurfaceRule,
                "expected the delegate singleton, got " + decoded.getClass());
    }
}
