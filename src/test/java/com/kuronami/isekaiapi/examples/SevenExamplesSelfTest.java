package com.kuronami.isekaiapi.examples;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.api.predicate.EvaluationContext;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.BiomeMatcher;
import com.kuronami.isekaiapi.impl.RemapEngine;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Machine self-test of the seven worldshapes hand-checked on paper for v1
 * (砂漠+オアシス / マグマ+蟻巣 / 針山 / 水没 / 1チャンク1biome / 鏡写し / 球状), re-run against the
 * real v2 API as on-disk scratch datapacks under {@code examples/selftest_7/}.
 *
 * <p>This class covers the parts that need no server: descriptor decode + biome-selection
 * behaviour (item 1), remap adaptation math (items 2/3/6), and predicate delegation via a stub
 * {@link EvaluationContext} (items 4/6/7). The noise-driven density shapes (items 2/3/6/7) and
 * the rule biome source / sea_level fields (items 5/4) are asserted server-in-the-loop in
 * {@code gametest/IsekaiSelfTestGameTests}. Together every example carries at least one machine
 * assertion of its characteristic behaviour, not merely a decode.
 */
class SevenExamplesSelfTest {

    private static RegistryOps<JsonElement> ops;
    private static HolderLookup.RegistryLookup<Biome> biomes;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        biomes = lookup.lookupOrThrow(Registries.BIOME);
    }

    // ---- shared helpers ----

    private static Path selftestDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path p = dir; p != null; p = p.getParent()) {
            Path candidate = p.resolve("examples/selftest_7");
            if (Files.isDirectory(candidate)) return candidate;
        }
        return fail("examples/selftest_7 not found by walking up from " + dir);
    }

    private static JsonElement read(String relative) {
        try {
            return JsonParser.parseString(Files.readString(selftestDir().resolve(relative)));
        } catch (IOException e) {
            return fail("could not read " + relative + ": " + e);
        }
    }

    private static <T> T decode(String relative, Codec<T> codec) {
        DataResult<T> r = codec.parse(ops, read(relative));
        return r.getOrThrow(msg -> new AssertionError(relative + " did not decode: " + msg));
    }

    // ---- Item 1: desert + oasis — biome-differentiated adaptation via applies_to ----

    @Test
    void example1_appliesToDesertNotOcean_andAddsOasisFeature() {
        WorldshapeDescriptor d = decode("1_desert_oasis/worldshape.json", WorldshapeDescriptor.CODEC);
        assertFalse(d.appliesTo().isEmpty(), "applies_to must be a real selection");

        Holder<Biome> desert = biomes.getOrThrow(Biomes.DESERT);
        Holder<Biome> ocean = biomes.getOrThrow(Biomes.OCEAN);
        assertTrue(BiomeMatcher.matches(d, desert), "descriptor must apply to desert");
        assertFalse(BiomeMatcher.matches(d, ocean), "descriptor must NOT apply to ocean");

        assertEquals(1, d.additions().features().size(), "oasis feature injection expected");
    }

    // ---- Item 2: magma + ant-nest caves — reduced ore count via Pipe(Linear, CountScale) ----

    @Test
    void example2_oreStrategyIsPipeWithZeroPointSevenCountFactor() {
        WorldshapeDescriptor d = decode("2_magma_ant_caves/worldshape.json", WorldshapeDescriptor.CODEC);
        assertInstanceOf(RemapStrategy.Pipe.class, d.oreStrategy(), "ore_strategy must be a Pipe");
        // Pipe folds CountScale factors multiplicatively; Linear contributes 1.0 → 0.7 overall.
        assertEquals(0.7, RemapEngine.effectiveCountFactor(d.oreStrategy()), 1e-9);
        // And the carver addition (the ant-nest cave network) survived decode.
        assertEquals(1, d.additions().carvers().size(), "cave carver addition expected");
    }

    // ---- Item 3: needle mountains — Linear remap lifts vanilla ore bands into the tall range ----

    @Test
    void example3_linearRemapProjectsOresIntoTallPlayableBand() {
        WorldshapeDescriptor d = decode("3_needle_peaks/worldshape.json", WorldshapeDescriptor.CODEC);
        assertInstanceOf(RemapStrategy.Linear.class, d.oreStrategy());

        // A deep vanilla ore band, projected through Linear into the playable range, must land
        // proportionally inside the new [0,300] band and stay ordered.
        VerticalRange deepOre = new VerticalRange(-64, 16, HeightDistribution.UNIFORM);
        VerticalRange out = RemapEngine.apply(d.oreStrategy(), deepOre, d.playableRange(), -64, 319);
        assertTrue(out.minY() >= d.playableRange().minY(), "remapped min below playable floor: " + out);
        assertTrue(out.maxY() <= d.playableRange().maxY(), "remapped max above playable ceiling: " + out);
        assertTrue(out.minY() < out.maxY(), "remapped range collapsed: " + out);
        // Deep-and-narrow source stays low-and-narrow in the target (proportional, not full-range).
        assertTrue(out.maxY() < d.playableRange().maxY(), "narrow source should not fill the whole band");
    }

    // ---- Item 4: submerged — InFluid(water) adaptation (the Isekai-owned half; see gametest/README) ----

    @Test
    void example4_inFluidWaterPredicateGatesOnFluid() {
        SpatialPredicate p = decode("4_submerged/predicate.json", SpatialPredicate.CODEC);
        assertInstanceOf(SpatialPredicate.InFluid.class, p);
        assertTrue(p.test(stub(64, false, false, true)), "in_fluid true when the column is in fluid");
        assertFalse(p.test(stub(64, false, false, false)), "in_fluid false on dry land");
    }

    // ---- Item 6: mirror — Inverted remap + SolidCeiling gate (ceiling-relative placement) ----

    @Test
    void example6_invertedRemapAndSolidCeilingGate() {
        WorldshapeDescriptor d = decode("6_mirror/worldshape.json", WorldshapeDescriptor.CODEC);
        assertInstanceOf(RemapStrategy.Inverted.class, d.oreStrategy());
        assertInstanceOf(SpatialPredicate.SolidCeiling.class, d.defaultStructurePredicate());

        SpatialPredicate p = decode("6_mirror/predicate.json", SpatialPredicate.CODEC);
        assertInstanceOf(SpatialPredicate.SolidCeiling.class, p);
        assertTrue(p.test(stub(120, false, true, false)), "solid_ceiling true under a ceiling");
        assertFalse(p.test(stub(120, false, false, false)), "solid_ceiling false with open sky");
    }

    // ---- Item 7: spherical — SolidFloor gate (structures cling to the shell surface) ----

    @Test
    void example7_solidFloorGate() {
        SpatialPredicate p = decode("7_spherical/predicate.json", SpatialPredicate.CODEC);
        assertInstanceOf(SpatialPredicate.SolidFloor.class, p);
        assertTrue(p.test(stub(80, true, false, false)), "solid_floor true on solid ground");
        assertFalse(p.test(stub(80, false, false, false)), "solid_floor false over a void");
    }

    // ---- stub EvaluationContext (mirror of SpatialPredicateTest's) ----

    private static EvaluationContext stub(int y, boolean floor, boolean ceiling, boolean fluid) {
        return new EvaluationContext() {
            @Override public net.minecraft.core.BlockPos pos() { return new net.minecraft.core.BlockPos(0, y, 0); }
            @Override public boolean solidFloor(int minClearance) { return floor; }
            @Override public boolean solidCeiling(int minClearance) { return ceiling; }
            @Override public boolean inFluid(Fluid f) { return fluid; }
            @Override public boolean nearBlock(net.minecraft.core.HolderSet<net.minecraft.world.level.block.Block> t, int d) { return false; }
            @Override public boolean nearBiome(net.minecraft.resources.ResourceKey<Biome> b, int d) { return false; }
            @Override public boolean terrainSlope(double min, double max) { return false; }
        };
    }

    // Referenced to keep the water fluid import meaningful if the stub form changes.
    @SuppressWarnings("unused")
    private static final Fluid WATER = Fluids.WATER;
}
