package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.placementmodifier.BiomeFalloffModifier;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * {@code isekai_api:biome_falloff} against the dimension's own biome source.
 *
 * <p>A unit test can only check the geometry against a hand-written "is this cell the biome?"
 * function. Three things can still go wrong in a real world and are checked here: the biome source
 * may not be readable from a placement modifier at all, the biome it reports may not be the biome
 * the chunk was generated with, and the holder set the JSON decoded may not match the holders the
 * source hands back — which would silently make every position fail.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class BiomeFalloffGameTests {

    private BiomeFalloffGameTests() {}

    /** How far the absence scan reaches; comfortably past the filter it has to witness. */
    private static final int SCAN_RADIUS = 32;
    private static final int SCAN_VERTICAL = 8;

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void theRealBiomeSourceIsReadAndHonoured(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        PlacementContext ctx = new PlacementContext(level,
                level.getChunkSource().getGenerator(), Optional.empty());
        BiomeSource source = ctx.generator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();

        // What the source says about the arena, and whether the chunk agrees. A falloff reading a
        // different world than the one being generated would be measuring nothing.
        Holder<Biome> here = source.getNoiseBiome(
                pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2, sampler);
        ResourceKey<Biome> hereKey = here.unwrapKey().orElse(null);
        if (hereKey == null) {
            helper.fail("the biome source returned a holder without a key at " + pos);
            return;
        }
        if (!level.getBiome(pos).is(hereKey)) {
            helper.fail("the biome source reports " + hereKey.location() + " at " + pos
                    + " but the chunk holds " + level.getBiome(pos));
            return;
        }

        // A hard radius with the target biome under the candidate always passes, whatever the draw.
        BiomeFalloffModifier inside = new BiomeFalloffModifier(
                HolderSet.direct(here), 16, 16, 1.0f,
                BiomeFalloffModifier.VerticalSpan.NONE, 4);
        if (!accepts(inside, ctx, pos)) {
            helper.fail("a hard 16-block radius rejected the biome the candidate stands in");
            return;
        }

        // The JSON contract, through the dispatch codec a placed feature actually uses.
        BiomeFalloffModifier decoded = decode(helper, level, """
                {"type":"isekai_api:biome_falloff","biome":"%s","max_distance":48,\
                "full_distance":8,"exponent":1.5,"vertical_range":{"below":4,"above":4}}"""
                .formatted(hereKey.location()));
        if (decoded == null) {
            return;
        }
        if (decoded.maxDistance() != 48 || decoded.fullDistance() != 8
                || decoded.exponent() != 1.5f || decoded.step() != 4) {
            helper.fail("the decoded modifier does not match its JSON: max "
                    + decoded.maxDistance() + " full " + decoded.fullDistance()
                    + " exponent " + decoded.exponent() + " step " + decoded.step());
            return;
        }
        if (!decoded.biomes().stream().allMatch(holder -> holder.is(hereKey))) {
            helper.fail("the decoded biome set holds something other than " + hereKey.location());
            return;
        }
        if (!roundTrip(helper, level, decoded)) {
            return;
        }

        // A biome that this world does not produce anywhere inside the scanned box must never pass a
        // 16-block hard filter. Without this, "accepts the right biome" could be satisfied by a
        // modifier that accepts everything.
        Set<ResourceKey<Biome>> present = keysWithin(source, sampler, pos);
        Optional<Holder.Reference<Biome>> absent = level.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .listElements()
                .filter(holder -> !present.contains(holder.key()))
                .findFirst();
        if (absent.isEmpty()) {
            // A single-biome world: there is no second biome to prove the negative with.
            helper.succeed();
            return;
        }
        BiomeFalloffModifier never = new BiomeFalloffModifier(
                HolderSet.direct(absent.get()), 16, 16, 1.0f,
                new BiomeFalloffModifier.VerticalSpan(4, 4), 4);
        if (accepts(never, ctx, pos)) {
            helper.fail("a hard 16-block radius accepted " + absent.get().key().location()
                    + ", which the source does not produce within " + SCAN_RADIUS + " blocks");
            return;
        }
        helper.succeed();
    }

    private static boolean accepts(BiomeFalloffModifier modifier, PlacementContext ctx, BlockPos pos) {
        return modifier.getPositions(ctx, RandomSource.create(1L), pos).findAny().isPresent();
    }

    /** Decode through {@link PlacementModifier#CODEC}, so the {@code type} id is checked too. */
    private static BiomeFalloffModifier decode(GameTestHelper helper, ServerLevel level, String json) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        var parsed = PlacementModifier.CODEC.parse(ops, JsonParser.parseString(json));
        if (parsed.error().isPresent()) {
            helper.fail("biome_falloff JSON did not decode: " + parsed.error().get().message());
            return null;
        }
        if (!(parsed.result().orElseThrow() instanceof BiomeFalloffModifier modifier)) {
            helper.fail("isekai_api:biome_falloff decoded to "
                    + parsed.result().orElseThrow().getClass().getSimpleName());
            return null;
        }
        return modifier;
    }

    /** Encode, decode again, and require the same values: a pack can be written back out. */
    private static boolean roundTrip(GameTestHelper helper, ServerLevel level,
                                     BiomeFalloffModifier modifier) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        var encoded = PlacementModifier.CODEC.encodeStart(ops, modifier);
        if (encoded.error().isPresent()) {
            helper.fail("biome_falloff did not encode: " + encoded.error().get().message());
            return false;
        }
        var again = PlacementModifier.CODEC.parse(ops, encoded.result().orElseThrow());
        if (again.error().isPresent()) {
            helper.fail("the encoded biome_falloff did not decode: "
                    + again.error().get().message());
            return false;
        }
        BiomeFalloffModifier second = (BiomeFalloffModifier) again.result().orElseThrow();
        if (second.maxDistance() != modifier.maxDistance()
                || second.fullDistance() != modifier.fullDistance()
                || second.exponent() != modifier.exponent()
                || second.step() != modifier.step()
                || !second.biomes().unwrapKey().equals(modifier.biomes().unwrapKey())
                || second.verticalRange().below() != modifier.verticalRange().below()
                || second.verticalRange().above() != modifier.verticalRange().above()) {
            helper.fail("biome_falloff changed when written out and read back");
            return false;
        }
        return true;
    }

    /** Every biome key the source produces in the scanned box around {@code pos}. */
    private static Set<ResourceKey<Biome>> keysWithin(BiomeSource source,
                                                      Climate.Sampler sampler, BlockPos pos) {
        Set<ResourceKey<Biome>> keys = new HashSet<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx += 4) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz += 4) {
                for (int dy = -SCAN_VERTICAL; dy <= SCAN_VERTICAL; dy += 4) {
                    source.getNoiseBiome((pos.getX() + dx) >> 2, (pos.getY() + dy) >> 2,
                                    (pos.getZ() + dz) >> 2, sampler)
                            .unwrapKey()
                            .ifPresent(keys::add);
                }
            }
        }
        return keys;
    }
}
