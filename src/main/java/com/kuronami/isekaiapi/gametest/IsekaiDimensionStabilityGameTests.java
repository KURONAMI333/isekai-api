package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.mixin.WorldDimensionsInvoker;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pins which dimensions vanilla's experimental-settings check accepts once Isekai vouches for its
 * own biome sources. Each case decodes a dimension JSON the way a mod jar ships it and asks
 * vanilla's {@code WorldDimensions.checkStability} directly, so the mixin is exercised end to end.
 *
 * <p>Accepted: a nether or end that keeps its stock dimension type key, uses a noise generator
 * (stock or own noise settings) and takes biomes from an {@code isekai_api:} source. Unchanged:
 * the stock dimensions, a non-Isekai source with own noise settings, a swapped dimension type,
 * and any extra dimension.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiDimensionStabilityGameTests {

    private IsekaiDimensionStabilityGameTests() {}

    private static final String NETHER_MULTI_NOISE =
            "{\"type\":\"minecraft:multi_noise\",\"preset\":\"minecraft:nether\"}";
    private static final String NETHER_OVERRIDE = "{\"type\":\"isekai_api:override\",\"base\":"
            + NETHER_MULTI_NOISE + ",\"overrides\":[{\"zone\":{\"type\":\"isekai_api:y_above\",\"y\":100},"
            + "\"biome\":\"minecraft:basalt_deltas\"}]}";

    private static LevelStem stem(GameTestHelper helper, String type, String settings, String biomeSource) {
        String json = "{\"type\":\"" + type + "\",\"generator\":{\"type\":\"minecraft:noise\","
                + "\"settings\":\"" + settings + "\",\"biome_source\":" + biomeSource + "}}";
        return LevelStem.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                        JsonParser.parseString(json))
                .getOrThrow(msg -> new AssertionError("dimension did not decode: " + msg));
    }

    private static void expect(GameTestHelper helper, String label, ResourceKey<LevelStem> key,
                               LevelStem stem, Lifecycle expected) {
        Lifecycle actual = WorldDimensionsInvoker.isekai$checkStability(key, stem);
        if (actual != expected) {
            helper.fail(label + ": expected " + expected + ", got " + actual);
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void netherAndEndOnIsekaiSourcesAreStable(GameTestHelper helper) {
        expect(helper, "stock nether", LevelStem.NETHER,
                stem(helper, "minecraft:the_nether", "minecraft:nether", NETHER_MULTI_NOISE),
                Lifecycle.stable());
        expect(helper, "override over the stock nether", LevelStem.NETHER,
                stem(helper, "minecraft:the_nether", "minecraft:nether", NETHER_OVERRIDE),
                Lifecycle.stable());
        // A mod's own noise settings are stood in for by another registered key.
        expect(helper, "override with own noise settings", LevelStem.NETHER,
                stem(helper, "minecraft:the_nether", "minecraft:overworld", NETHER_OVERRIDE),
                Lifecycle.stable());
        expect(helper, "rule source in the nether", LevelStem.NETHER,
                stem(helper, "minecraft:the_nether", "minecraft:nether",
                        "{\"type\":\"isekai_api:rule\",\"fallback\":\"minecraft:nether_wastes\",\"rules\":[]}"),
                Lifecycle.stable());
        expect(helper, "override over the stock end", LevelStem.END,
                stem(helper, "minecraft:the_end", "minecraft:end",
                        "{\"type\":\"isekai_api:override\",\"base\":{\"type\":\"minecraft:the_end\"},"
                                + "\"overrides\":[]}"),
                Lifecycle.stable());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void otherDeviationsStayExperimental(GameTestHelper helper) {
        expect(helper, "stock source with own noise settings", LevelStem.NETHER,
                stem(helper, "minecraft:the_nether", "minecraft:overworld", NETHER_MULTI_NOISE),
                Lifecycle.experimental());
        expect(helper, "override with a swapped dimension type", LevelStem.NETHER,
                stem(helper, "minecraft:overworld", "minecraft:nether", NETHER_OVERRIDE),
                Lifecycle.experimental());
        expect(helper, "override in an extra dimension", ResourceKey.create(Registries.LEVEL_STEM,
                        ResourceLocation.fromNamespaceAndPath(IsekaiApi.MODID, "extra")),
                stem(helper, "minecraft:the_nether", "minecraft:nether", NETHER_OVERRIDE),
                Lifecycle.experimental());
        helper.succeed();
    }
}
