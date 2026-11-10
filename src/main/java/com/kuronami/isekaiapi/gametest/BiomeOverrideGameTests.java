package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.biomesource.OverrideBiomeSource;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code isekai_api:override} over a real registry: the source decodes through
 * {@code BuiltInRegistries.BIOME_SOURCE}, the box and chunk-box footprints resolve on the biome
 * grid in world coordinates, and everything outside them falls through to the wrapped source.
 *
 * <p>The base is {@code minecraft:fixed} so the expected answer at every point is unambiguous —
 * any position that is not jungle proves the override did not leak, and any position that is
 * jungle proves it fired.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class BiomeOverrideGameTests {

    private BiomeOverrideGameTests() {}

    private static final String BASE = "\"base\":{\"type\":\"minecraft:fixed\",\"biome\":\"minecraft:plains\"}";

    /** A 64x64 block column from y 0 to 63, plus one chunk far outside that box. */
    private static final String OVERRIDES = "\"overrides\":["
            + "{\"zone\":{\"type\":\"isekai_api:box\","
            + "\"min_x\":0,\"max_x\":63,\"min_z\":0,\"max_z\":63,\"min_y\":0,\"max_y\":63},"
            + "\"biome\":\"minecraft:jungle\"},"
            + "{\"zone\":{\"type\":\"isekai_api:chunk_box\","
            + "\"min_chunk_x\":5,\"max_chunk_x\":5,\"min_chunk_z\":5,\"max_chunk_z\":5},"
            + "\"biome\":\"minecraft:jungle\"}]";

    private static OverrideBiomeSource decode(GameTestHelper helper, String json) {
        BiomeSource source = BiomeSource.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                        JsonParser.parseString(json))
                .getOrThrow(msg -> new AssertionError("biome source did not decode: " + msg));
        if (!(source instanceof OverrideBiomeSource override)) {
            throw new AssertionError("expected an OverrideBiomeSource, got "
                    + source.getClass().getName());
        }
        return override;
    }

    private static Holder<Biome> at(OverrideBiomeSource source, GameTestHelper helper,
                                    int quartX, int quartY, int quartZ) {
        Climate.Sampler sampler = helper.getLevel().getChunkSource().randomState().sampler();
        return source.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    private static void assertBiome(GameTestHelper helper, Holder<Biome> biome, String where,
                                    String path) {
        String key = biome.unwrapKey()
                .map(k -> k.location().toString())
                .orElse("<direct>");
        if (!key.equals(path)) {
            helper.fail("at " + where + " expected " + path + " but got " + key,
                    net.minecraft.core.BlockPos.ZERO);
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void boxReplacesInsideItsBoundsAndDelegatesOutside(GameTestHelper helper) {
        OverrideBiomeSource source = decode(helper, "{\"type\":\"isekai_api:override\","
                + BASE + "," + OVERRIDES + "}");

        // Block (16,16,16) is inside; (64,16,16) is one block past max_x and in chunk 4, so it
        // falls through. y 80 is above max_y and y -4 is below min_y.
        assertBiome(helper, at(source, helper, 4, 4, 4), "quart (4,4,4) = block (16,16,16)",
                "minecraft:jungle");
        assertBiome(helper, at(source, helper, 4, 0, 4), "quart (4,0,4) = block (16,0,16)",
                "minecraft:jungle");
        assertBiome(helper, at(source, helper, 16, 4, 4), "quart (16,4,4) = block (64,16,16)",
                "minecraft:plains");
        assertBiome(helper, at(source, helper, 4, 20, 4), "quart (4,20,4) = block (16,80,16)",
                "minecraft:plains");
        assertBiome(helper, at(source, helper, 4, -1, 4), "quart (4,-1,4) = block (16,-4,16)",
                "minecraft:plains");

        // Chunk 5 spans blocks 80..95, so quart 20 (block 80) is already inside chunk_box even
        // though it is past the box; quart 19 (block 76, chunk 4) is outside both.
        assertBiome(helper, at(source, helper, 19, 0, 19), "quart (19,0,19) = block (76,0,76)",
                "minecraft:plains");
        assertBiome(helper, at(source, helper, 20, 0, 20), "quart (20,0,20) = block (80,0,80)",
                "minecraft:jungle");
        assertBiome(helper, at(source, helper, 21, 0, 21), "quart (21,0,21) = block (84,0,84)",
                "minecraft:jungle");
        assertBiome(helper, at(source, helper, 24, 0, 24), "quart (24,0,24) = block (96,0,96)",
                "minecraft:plains");

        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void possibleBiomesCoversBaseAndReplacements(GameTestHelper helper) {
        OverrideBiomeSource source = decode(helper, "{\"type\":\"isekai_api:override\","
                + BASE + "," + OVERRIDES + "}");

        boolean plains = false;
        boolean jungle = false;
        for (Holder<Biome> biome : source.possibleBiomes()) {
            String key = biome.unwrapKey().map(k -> k.location().toString()).orElse("<direct>");
            plains |= key.equals("minecraft:plains");
            jungle |= key.equals("minecraft:jungle");
        }
        if (!plains || !jungle) {
            helper.fail("possibleBiomes must include plains and jungle (plains=" + plains
                    + ", jungle=" + jungle + ")", net.minecraft.core.BlockPos.ZERO);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void bindingTheSeedMarksTheSourceBound(GameTestHelper helper) {
        OverrideBiomeSource source = decode(helper, "{\"type\":\"isekai_api:override\","
                + BASE + "," + OVERRIDES + "}");

        if (source.isBound()) {
            helper.fail("a freshly decoded source must not claim to be bound",
                    net.minecraft.core.BlockPos.ZERO);
        }
        source.bindWorldSeed(1234L);
        if (!source.isBound()) {
            helper.fail("bindWorldSeed must mark the source bound",
                    net.minecraft.core.BlockPos.ZERO);
        }
        assertBiome(helper, at(source, helper, 4, 4, 4), "quart (4,4,4) after bind",
                "minecraft:jungle");
        helper.succeed();
    }

    /**
     * Chunk -1 spans blocks -16..-1 (quarts -4..-1). An off-by-one in the floor of a negative
     * coordinate would move that footprint by one chunk; the cells on both sides of the 0 / -1
     * border and of the -1 / -2 border pin it.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void chunkBoxHoldsOnNegativeCoordinates(GameTestHelper helper) {
        OverrideBiomeSource source = decode(helper, "{\"type\":\"isekai_api:override\"," + BASE
                + ",\"overrides\":[{\"zone\":{\"type\":\"isekai_api:chunk_box\","
                + "\"min_chunk_x\":-1,\"max_chunk_x\":-1,\"min_chunk_z\":-1,\"max_chunk_z\":-1},"
                + "\"biome\":\"minecraft:jungle\"}]}");

        assertBiome(helper, at(source, helper, -1, 0, -1), "quart (-1,0,-1) = block (-4,0,-4)",
                "minecraft:jungle");
        assertBiome(helper, at(source, helper, -4, 0, -4), "quart (-4,0,-4) = block (-16,0,-16)",
                "minecraft:jungle");
        assertBiome(helper, at(source, helper, 0, 0, -1), "quart (0,0,-1) = block (0,0,-4)",
                "minecraft:plains");
        assertBiome(helper, at(source, helper, -5, 0, -1), "quart (-5,0,-1) = block (-20,0,-4)",
                "minecraft:plains");
        assertBiome(helper, at(source, helper, -1, 0, -5), "quart (-1,0,-5) = block (-4,0,-20)",
                "minecraft:plains");
        helper.succeed();
    }

    /** Overlapping footprints: the entry written first wins, the later one only fills the rest. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void theFirstMatchingOverrideWins(GameTestHelper helper) {
        OverrideBiomeSource source = decode(helper, "{\"type\":\"isekai_api:override\"," + BASE
                + ",\"overrides\":["
                + "{\"zone\":{\"type\":\"isekai_api:chunk_box\","
                + "\"min_chunk_x\":0,\"max_chunk_x\":0,\"min_chunk_z\":0,\"max_chunk_z\":0},"
                + "\"biome\":\"minecraft:cherry_grove\"},"
                + "{\"zone\":{\"type\":\"isekai_api:box\","
                + "\"min_x\":-64,\"max_x\":63,\"min_z\":-64,\"max_z\":63},"
                + "\"biome\":\"minecraft:jungle\"}]}");

        assertBiome(helper, at(source, helper, 1, 0, 1), "quart (1,0,1), inside both",
                "minecraft:cherry_grove");
        assertBiome(helper, at(source, helper, 8, 0, 8), "quart (8,0,8), box only",
                "minecraft:jungle");
        assertBiome(helper, at(source, helper, 40, 0, 40), "quart (40,0,40), neither",
                "minecraft:plains");
        helper.succeed();
    }

    /**
     * An override wrapping a rule source wrapping nothing Isekai: the seed has to reach the inner
     * rule (or the server-start sweep would warn about it), the inner rule has to answer where the
     * outer override does not claim, and the whole stack has to encode back to JSON and decode
     * into the same answers.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void aNestedStackIsSeededAndRoundTrips(GameTestHelper helper) {
        String json = "{\"type\":\"isekai_api:override\","
                + "\"base\":{\"type\":\"isekai_api:rule\",\"fallback\":\"minecraft:plains\","
                + "\"rules\":[{\"zone\":{\"type\":\"isekai_api:chunk_box\","
                + "\"min_chunk_x\":2,\"max_chunk_x\":2,\"min_chunk_z\":0,\"max_chunk_z\":0},"
                + "\"biome\":\"minecraft:desert\"}]},"
                + "\"overrides\":[{\"zone\":{\"type\":\"isekai_api:chunk_box\","
                + "\"min_chunk_x\":0,\"max_chunk_x\":0,\"min_chunk_z\":0,\"max_chunk_z\":0},"
                + "\"biome\":\"minecraft:jungle\"}]}";
        OverrideBiomeSource source = decode(helper, json);

        if (!(source.base() instanceof com.kuronami.isekaiapi.biomesource.RuleBiomeSource inner)) {
            helper.fail("the base did not decode as a rule source: " + source.base(),
                    net.minecraft.core.BlockPos.ZERO);
            return;
        }
        source.bindWorldSeed(42L);
        if (!inner.isBound() || !source.isBound()) {
            helper.fail("the seed did not reach the wrapped rule source (inner bound="
                    + inner.isBound() + ")", net.minecraft.core.BlockPos.ZERO);
        }

        RegistryOps<com.google.gson.JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        com.google.gson.JsonElement encoded = BiomeSource.CODEC.encodeStart(ops, source)
                .getOrThrow(msg -> new AssertionError("biome source did not encode: " + msg));
        OverrideBiomeSource again = decode(helper, encoded.toString());

        for (OverrideBiomeSource s : new OverrideBiomeSource[] {source, again}) {
            String which = s == source ? "decoded" : "re-decoded";
            assertBiome(helper, at(s, helper, 1, 0, 1), which + " quart (1,0,1), chunk 0",
                    "minecraft:jungle");
            assertBiome(helper, at(s, helper, 9, 0, 1), which + " quart (9,0,1), chunk 2",
                    "minecraft:desert");
            assertBiome(helper, at(s, helper, 5, 0, 1), which + " quart (5,0,1), chunk 1",
                    "minecraft:plains");
        }
        helper.succeed();
    }
}
