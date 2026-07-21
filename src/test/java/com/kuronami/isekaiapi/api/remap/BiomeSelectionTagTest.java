package com.kuronami.isekaiapi.api.remap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 tag reconcile: {@link BiomeSelection} accepts a biome tag written either with the
 * vanilla HolderSet {@code #}-prefix or bare, both decoding to the same {@link net.minecraft.tags.TagKey},
 * and re-encodes canonically with the {@code #}. This unblocks the mixed {@code #}/no-{@code #}
 * notation across docs and examples (W2 GAP_LOG finding).
 */
class BiomeSelectionTagTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static BiomeSelection decode(String json) {
        JsonElement e = JsonParser.parseString(json);
        return BiomeSelection.CODEC.parse(JsonOps.INSTANCE, e)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    @Test
    void hashedAndBareTagDecodeIdentically() {
        BiomeSelection hashed = decode("{\"tags\":[\"#minecraft:is_overworld\"]}");
        BiomeSelection bare = decode("{\"tags\":[\"minecraft:is_overworld\"]}");
        assertEquals(1, hashed.tags().size());
        assertEquals(bare.tags(), hashed.tags(), "both notations must produce the same tag");
        assertTrue(hashed.tags().iterator().next().location().toString().equals("minecraft:is_overworld"));
    }

    @Test
    void encodesCanonicalHashForm() {
        BiomeSelection bare = decode("{\"tags\":[\"minecraft:is_overworld\"]}");
        JsonElement out = BiomeSelection.CODEC.encodeStart(JsonOps.INSTANCE, bare)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        assertTrue(out.toString().contains("#minecraft:is_overworld"),
                "re-encode should use the canonical #-prefixed form, got " + out);
    }
}
