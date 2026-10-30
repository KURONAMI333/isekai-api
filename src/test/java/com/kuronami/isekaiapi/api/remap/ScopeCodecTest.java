package com.kuronami.isekaiapi.api.remap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Decode contract for {@link Scope}: the 2.x {@code "dimension": "<id>"} spelling still loads
 * (unrestricted), and the 3.0.0 object spelling narrows the worldshape to the dimension types
 * it was written for.
 */
class ScopeCodecTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<DimensionType> SKY_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sky_world", "sky"));
    private static final ResourceKey<DimensionType> VANILLA_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"));

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static Scope decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        return Scope.CODEC.parse(JsonOps.INSTANCE, el)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    @Test void legacyStringIsUnrestricted() {
        Scope scope = decode("\"minecraft:overworld\"");
        assertEquals(OVERWORLD, scope.dimension());
        assertTrue(scope.requiredDimensionTypes().isEmpty());
        assertTrue(scope.isUnrestricted());
        assertTrue(scope.accepts(SKY_TYPE));
        assertTrue(scope.accepts(VANILLA_TYPE));
    }

    @Test void objectFormNarrowsToDeclaredTypes() {
        Scope scope = decode("{\"dimension\":\"minecraft:overworld\","
                + "\"required_dimension_types\":[\"sky_world:sky\"]}");
        assertEquals(OVERWORLD, scope.dimension());
        assertEquals(List.of(SKY_TYPE), scope.requiredDimensionTypes());
        assertFalse(scope.isUnrestricted());
        assertTrue(scope.accepts(SKY_TYPE));
        assertFalse(scope.accepts(VANILLA_TYPE));
    }

    @Test void objectFormWithoutTypesIsUnrestricted() {
        Scope scope = decode("{\"dimension\":\"minecraft:overworld\"}");
        assertTrue(scope.requiredDimensionTypes().isEmpty());
        assertTrue(scope.accepts(VANILLA_TYPE));
    }

    @Test void acceptsHonoursEveryDeclaredType() {
        Scope scope = new Scope(OVERWORLD, List.of(SKY_TYPE, VANILLA_TYPE));
        assertTrue(scope.accepts(SKY_TYPE));
        assertTrue(scope.accepts(VANILLA_TYPE));
    }

    @Test void encodeAlwaysUsesObjectForm() {
        Scope scope = Scope.of(OVERWORLD);
        JsonElement encoded = Scope.CODEC.encodeStart(JsonOps.INSTANCE, scope).getOrThrow();
        assertTrue(encoded.isJsonObject(), "encoded: " + encoded);
        assertEquals("minecraft:overworld", encoded.getAsJsonObject().get("dimension").getAsString());
    }
}
