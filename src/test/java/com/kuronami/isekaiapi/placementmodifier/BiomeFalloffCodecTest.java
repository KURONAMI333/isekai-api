package com.kuronami.isekaiapi.placementmodifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The datapack contract for {@code isekai_api:biome_falloff}: which fields a placed feature may
 * write, what leaving them out means, and which values are refused instead of quietly reinterpreted.
 *
 * <p>Defaults matter more than usual here. A pack that writes only a biome and a maximum distance
 * has to get a plain linear falloff down to that distance — no hidden minimum band, no hidden cost
 * knob — and a pack that writes {@code full_distance} equal to {@code max_distance} has to get the
 * hard radius, because that is the documented way to get one.
 *
 * <p>Decoding only. Writing a {@code HolderSet<Biome>} back needs the registry a running server
 * owns; the generated vanilla lookup a unit test can build refuses to serialise its own holders
 * and cannot dereference a biome tag during construction. The save direction, and the tag's
 * contents, are checked in {@code BiomeFalloffGameTests} where a server exists.
 */
class BiomeFalloffCodecTest {

    private static DynamicOps<JsonElement> ops;

    private static final TagKey<Biome> FOREST_TAG =
            TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("is_forest"));

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
    }

    private static BiomeFalloffModifier decode(String json) {
        return BiomeFalloffModifier.CODEC.codec().parse(ops, JsonParser.parseString(json))
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    private static String reject(String json) {
        DataResult<BiomeFalloffModifier> result =
                BiomeFalloffModifier.CODEC.codec().parse(ops, JsonParser.parseString(json));
        assertTrue(result.error().isPresent(), "should have been rejected: " + json);
        return result.error().orElseThrow().message();
    }

    // ===== the whole shape, and what the defaults mean =====

    @Test
    void everyFieldDecodes() {
        BiomeFalloffModifier decoded = decode("""
                {"biome":"#minecraft:is_forest","max_distance":64,"full_distance":12,\
                "exponent":1.5,"vertical_range":{"below":24,"above":8},"step":8}""");
        assertEquals(FOREST_TAG, decoded.biomes().unwrapKey().orElseThrow());
        assertEquals(64, decoded.maxDistance());
        assertEquals(12, decoded.fullDistance());
        assertEquals(1.5f, decoded.exponent());
        assertEquals(24, decoded.verticalRange().below());
        assertEquals(8, decoded.verticalRange().above());
        assertEquals(8, decoded.step());
    }

    @Test
    void omittedFieldsTakeTheDocumentedDefaults() {
        BiomeFalloffModifier decoded = decode(
                "{\"biome\":\"minecraft:crimson_forest\",\"max_distance\":48}");
        assertEquals(0, decoded.fullDistance(),
                "full_distance defaults to 0 — the falloff starts at the biome's edge");
        assertEquals(1.0f, decoded.exponent(), "exponent defaults to 1 — linear");
        assertEquals(0, decoded.verticalRange().below());
        assertEquals(0, decoded.verticalRange().above());
        assertEquals(4, decoded.step(), "step defaults to 4 — one biome cell");
    }

    // ===== naming the biome three ways =====

    @Test
    void biomeAcceptsAnIdAListAndATag() {
        HolderSet<Biome> single = decode(
                "{\"biome\":\"minecraft:crimson_forest\",\"max_distance\":48}").biomes();
        assertEquals(List.of("minecraft:crimson_forest"), ids(single),
                "a bare id should be a one-biome set");

        HolderSet<Biome> listed = decode(
                "{\"biome\":[\"minecraft:plains\",\"minecraft:desert\"],"
                        + "\"max_distance\":48}").biomes();
        assertEquals(List.of("minecraft:plains", "minecraft:desert"), ids(listed));

        HolderSet<Biome> tagged = decode(
                "{\"biome\":\"#minecraft:is_forest\",\"max_distance\":48}").biomes();
        assertEquals(FOREST_TAG, tagged.unwrapKey().orElseThrow(),
                "a #tag should stay a tag, not be resolved into a list");
    }

    // ===== what is refused =====

    @Test
    void outOfRangeValuesAreRefused() {
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":3}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":129}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":48,\"full_distance\":-1}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":48,\"full_distance\":200}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":48,\"exponent\":0.1}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":48,\"exponent\":9.0}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":48,\"step\":6}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":48,\"step\":2}");
        reject("{\"biome\":\"minecraft:plains\",\"max_distance\":48,"
                + "\"vertical_range\":{\"below\":65}}");
        reject("{\"max_distance\":48}");
        reject("{\"biome\":\"minecraft:not_a_biome\",\"max_distance\":48}");
    }

    @Test
    void aFullBandWiderThanTheRadiusIsRefusedWithAReason() {
        String message = reject(
                "{\"biome\":\"minecraft:plains\",\"max_distance\":32,\"full_distance\":40}");
        assertTrue(message.contains("full_distance"),
                "the error should name the field: " + message);
    }

    /** The resource locations of a resolved set, in the order the pack wrote them. */
    private static List<String> ids(HolderSet<Biome> set) {
        return set.stream()
                .map(holder -> holder.unwrapKey().orElseThrow().location().toString())
                .toList();
    }
}
