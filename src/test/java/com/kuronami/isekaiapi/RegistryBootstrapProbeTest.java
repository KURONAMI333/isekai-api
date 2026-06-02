package com.kuronami.isekaiapi;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Probe: does the moddev unit-test environment make registries usable, and are this mod's
 * own registered types present? Determines whether codec round-trip / JSON-decode tests are
 * feasible (vs. pure-logic only).
 */
class RegistryBootstrapProbeTest {

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    @Test
    void vanillaDensityFunctionTypesArePresent() {
        assertTrue(BuiltInRegistries.DENSITY_FUNCTION_TYPE
                .containsKey(ResourceLocation.parse("minecraft:abs")));
    }

    @Test
    void isekaiDensityFunctionTypesAreRegistered() {
        assertTrue(BuiltInRegistries.DENSITY_FUNCTION_TYPE
                .containsKey(ResourceLocation.parse("isekai_api:quarter_negative")));
    }
}
