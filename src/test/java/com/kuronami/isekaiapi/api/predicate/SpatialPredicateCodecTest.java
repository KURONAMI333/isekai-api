package com.kuronami.isekaiapi.api.predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registry-backed dispatch round-trips for {@link SpatialPredicate}. Verifies all 12 built-in
 * variants decode under the canonical {@code isekai_api:} prefix, that the legacy {@code isekai:}
 * prefix and bare (namespace-less) ids are accepted as aliases, and that encoding emits the
 * canonical prefix. Requires a game bootstrap so the custom SPI registries exist and are populated.
 */
class SpatialPredicateCodecTest {

    /** Registry-aware ops so HolderSet payloads (NearBlock's block targets) resolve. */
    private static DynamicOps<JsonElement> ops;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ops = RegistryOps.create(JsonOps.INSTANCE, access);
    }

    private static SpatialPredicate decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        DataResult<SpatialPredicate> r = SpatialPredicate.CODEC.parse(ops, el);
        return r.getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    private static String encode(SpatialPredicate p) {
        return SpatialPredicate.CODEC.encodeStart(ops, p)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg)).toString();
    }

    // ===== canonical prefix decodes for every built-in variant =====

    @Test void allTwelveVariantsDecodeCanonical() {
        assertInstanceOf(SpatialPredicate.YInRange.class, decode("{\"type\":\"isekai_api:y_in_range\",\"min\":60,\"max\":200}"));
        assertInstanceOf(SpatialPredicate.SolidFloor.class, decode("{\"type\":\"isekai_api:solid_floor\",\"min_clearance\":3}"));
        assertInstanceOf(SpatialPredicate.SolidCeiling.class, decode("{\"type\":\"isekai_api:solid_ceiling\",\"min_clearance\":2}"));
        assertInstanceOf(SpatialPredicate.TerrainSlope.class, decode("{\"type\":\"isekai_api:terrain_slope\",\"min_slope\":0.0,\"max_slope\":0.5}"));
        assertInstanceOf(SpatialPredicate.NearBlock.class, decode("{\"type\":\"isekai_api:near_block\",\"targets\":\"minecraft:stone\",\"max_distance\":4}"));
        assertInstanceOf(SpatialPredicate.NearBiome.class, decode("{\"type\":\"isekai_api:near_biome\",\"biome\":\"minecraft:plains\",\"max_distance\":8}"));
        assertInstanceOf(SpatialPredicate.InFluid.class, decode("{\"type\":\"isekai_api:in_fluid\",\"fluid\":\"minecraft:water\"}"));
        assertInstanceOf(SpatialPredicate.Always.class, decode("{\"type\":\"isekai_api:always\"}"));
        assertInstanceOf(SpatialPredicate.Never.class, decode("{\"type\":\"isekai_api:never\"}"));
        assertInstanceOf(SpatialPredicate.And.class, decode("{\"type\":\"isekai_api:and\",\"all\":[]}"));
        assertInstanceOf(SpatialPredicate.Or.class, decode("{\"type\":\"isekai_api:or\",\"any\":[]}"));
        assertInstanceOf(SpatialPredicate.Not.class, decode("{\"type\":\"isekai_api:not\",\"inner\":{\"type\":\"isekai_api:always\"}}"));
    }

    // ===== legacy prefix + bare id accepted as aliases =====

    @Test void legacyPrefixDecodes() {
        SpatialPredicate p = decode("{\"type\":\"isekai:y_in_range\",\"min\":10,\"max\":20}");
        assertEquals(new SpatialPredicate.YInRange(10, 20), p);
    }

    @Test void bareIdDefaultsToIsekaiApiNamespace() {
        SpatialPredicate p = decode("{\"type\":\"y_in_range\",\"min\":10,\"max\":20}");
        assertEquals(new SpatialPredicate.YInRange(10, 20), p);
    }

    @Test void legacyAndCanonicalDecodeEqual() {
        assertEquals(
                decode("{\"type\":\"isekai:and\",\"all\":[{\"type\":\"isekai:always\"}]}"),
                decode("{\"type\":\"isekai_api:and\",\"all\":[{\"type\":\"isekai_api:always\"}]}"));
    }

    // ===== encode emits the canonical prefix =====

    @Test void encodeUsesCanonicalPrefix() {
        String json = encode(new SpatialPredicate.YInRange(60, 200));
        assertTrue(json.contains("isekai_api:y_in_range"), json);
        assertFalse(json.contains("\"isekai:"), json);
    }

    @Test void nestedRoundTrip() {
        SpatialPredicate original = new SpatialPredicate.And(java.util.List.of(
                new SpatialPredicate.YInRange(0, 128),
                new SpatialPredicate.Not(new SpatialPredicate.Never())));
        assertEquals(original, decode(encode(original)));
    }

    // ===== unknown type is rejected =====

    @Test void unknownTypeRejected() {
        JsonElement el = JsonParser.parseString("{\"type\":\"isekai_api:does_not_exist\"}");
        assertThrows(RuntimeException.class,
                () -> SpatialPredicate.CODEC.parse(JsonOps.INSTANCE, el).getOrThrow());
    }
}
