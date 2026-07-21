package com.kuronami.isekaiapi.examples;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Gate 3 (structure): the "30 lines to floating islands" shape example decodes and is genuinely
 * a Y-band terrain function. The example's hook override
 * ({@code data/isekai_api/worldgen/density_function/hook/final_density.json}) is decoded through
 * the real density-function codec (the isekai_api DF types are registered by the unitTest harness)
 * and asserted to be {@code isekai_api:squeeze} wrapping an {@code isekai_api:band_density} — the
 * band shape that replaces the mod's default terrain hook. The value proof (air below the band,
 * solid inside, air above) runs against a real {@code RandomState} in
 * {@code IsekaiHookGameTests.bandHookProducesFloatingIslandProfile}.
 */
class FloatingIslandExampleTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static Path floatingIslandHook() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            Path candidate = p.resolve(
                    "examples/1_shape/floating_island/data/isekai_api/worldgen/density_function/hook/final_density.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        fail("floating_island hook override not found by walking up from " + dir);
        throw new AssertionError("unreachable");
    }

    @Test
    void hookOverrideDecodesAsBandShape() throws IOException {
        JsonElement json = JsonParser.parseString(Files.readString(floatingIslandHook()));

        // Decodes through the real DF codec (proves it is a valid density function).
        DensityFunction.DIRECT_CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(msg -> new AssertionError("floating_island hook did not decode: " + msg));

        // And it is structurally a Y-band terrain: squeeze( ... band_density ... ).
        JsonObject root = json.getAsJsonObject();
        assertEquals("isekai_api:squeeze", root.get("type").getAsString(),
                "floating-island hook should tone-map its band with squeeze");
        assertTrue(json.toString().contains("isekai_api:band_density"),
                "floating-island hook should be built on isekai_api:band_density");
    }
}
