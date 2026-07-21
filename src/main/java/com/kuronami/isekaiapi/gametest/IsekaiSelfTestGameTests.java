package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.biomesource.RuleBiomeSource;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-in-the-loop half of the seven-worldshape self-test (SPEC §1-1). These read the on-disk
 * scratch datapacks under {@code examples/selftest_7/} and decode them against the live registry,
 * then assert the characteristic behaviour — density shape (SinglePointContext evaluation), rule
 * biome source zoning, and the sea_level noise-settings field. The remap/predicate/applies_to
 * halves live in the {@code SevenExamplesSelfTest} unit test.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiSelfTestGameTests {

    private IsekaiSelfTestGameTests() {}

    private static Path selftest(String relative) {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path c = p.resolve("examples/selftest_7").resolve(relative);
            if (Files.isRegularFile(c)) return c;
        }
        return null;
    }

    private static DensityFunction density(GameTestHelper helper, String relative) {
        Path path = selftest(relative);
        if (path == null) { helper.fail("scratch datapack not found: " + relative); return null; }
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
            return DensityFunction.DIRECT_CODEC
                    .parse(ops, JsonParser.parseString(Files.readString(path)))
                    .getOrThrow(msg -> new AssertionError(relative + " density decode: " + msg));
        } catch (Exception e) {
            helper.fail(relative + " could not be read/decoded: " + e);
            return null;
        }
    }

    private static double at(DensityFunction f, int x, int y, int z) {
        return f.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    // ---- Item 2: density subtraction carves the ant-nest cave band out of the terrain ----

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void example2_subtractionCarvesCaveBand(GameTestHelper helper) {
        DensityFunction f = density(helper, "2_magma_ant_caves/final_density.json");
        if (f == null) return;
        double terrain = at(f, 0, 90, 0);   // in the solid band, outside the cave Y-range
        double carved = at(f, 0, 55, 0);    // in the solid band, inside the cave Y-range (subtracted)
        if (!(terrain > 0.5)) { helper.fail("terrain not solid at Y=90 (got " + terrain + ")"); return; }
        if (!(carved < 0.0)) { helper.fail("cave not carved at Y=55 (got " + carved + ")"); return; }
        if (Math.abs((terrain - carved) - 3.0) > 1e-6) {
            helper.fail("subtraction did not remove the 3.0 cave mask (delta " + (terrain - carved) + ")");
            return;
        }
        helper.succeed();
    }

    // ---- Item 3: the sharp-peak density decodes and resolves against the live registry ----

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void example3_needleDensityResolves(GameTestHelper helper) {
        DensityFunction f = density(helper, "3_needle_peaks/final_density.json");
        if (f == null) return;
        // Noise-driven (blended_noise) — its aesthetic sharpness is a runClient concern; here we
        // only prove the multiply-of-noise composition is a valid, registry-resolved terrain DF.
        if (f.minValue() > f.maxValue()) {
            helper.fail("needle density has an inverted value envelope [" + f.minValue() + "," + f.maxValue() + "]");
            return;
        }
        helper.succeed();
    }

    // ---- Item 6: mirror — solid floor AND solid ceiling with a void gap between (the signature) ----

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void example6_mirrorHasCeilingTerrain(GameTestHelper helper) {
        DensityFunction f = density(helper, "6_mirror/final_density.json");
        if (f == null) return;
        double floor = at(f, 0, 30, 0);     // below the void band → solid ground
        double gap = at(f, 0, 130, 0);      // inside the inverted band → void
        double ceiling = at(f, 0, 220, 0);  // above the void band → solid ceiling (a normal world never has this)
        if (!(floor > 0.0)) { helper.fail("mirror world has no solid floor at Y=30 (got " + floor + ")"); return; }
        if (!(gap < 0.0)) { helper.fail("mirror world not void in the gap at Y=130 (got " + gap + ")"); return; }
        if (!(ceiling > 0.0)) { helper.fail("mirror world has no solid ceiling at Y=220 (got " + ceiling + ")"); return; }
        helper.succeed();
    }

    // ---- Item 7: spherical — solid inside the shell radius, void outside ----

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void example7_sphericalSolidInsideVoidOutside(GameTestHelper helper) {
        DensityFunction f = density(helper, "7_spherical/final_density.json");
        if (f == null) return;
        double center = at(f, 0, 80, 0);     // sphere centre → deep solid
        double justInside = at(f, 0, 130, 0); // r=50 < 60 → still solid
        double outside = at(f, 200, 80, 0);   // r=200 > 60 → void
        if (!(center > 0.0)) { helper.fail("sphere not solid at centre (got " + center + ")"); return; }
        if (!(justInside > 0.0)) { helper.fail("sphere not solid inside the radius (got " + justInside + ")"); return; }
        if (!(outside < 0.0)) { helper.fail("sphere not void outside the radius (got " + outside + ")"); return; }
        helper.succeed();
    }

    // ---- Item 5: rule biome source zones biomes deterministically by position ----

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void example5_ruleBiomeSourceZonesByPosition(GameTestHelper helper) {
        Path path = selftest("5_chunk_biomes/biome_source.json");
        if (path == null) { helper.fail("5_chunk_biomes/biome_source.json not found"); return; }
        BiomeSource source;
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
            source = BiomeSource.CODEC.parse(ops, JsonParser.parseString(Files.readString(path)))
                    .getOrThrow(msg -> new AssertionError("biome_source decode: " + msg));
        } catch (Exception e) {
            helper.fail("biome_source could not be read/decoded: " + e);
            return;
        }
        if (!(source instanceof RuleBiomeSource)) {
            helper.fail("decoded biome source is not isekai_api:rule: " + source.getClass().getSimpleName());
            return;
        }
        Holder<Biome> center = source.getNoiseBiome(0, 0, 0, null);
        Holder<Biome> far = source.getNoiseBiome(1_000_000, 0, 0, null);
        if (!center.is(Biomes.DESERT)) { helper.fail("centre zone should be desert, got " + center); return; }
        if (!far.is(Biomes.PLAINS)) { helper.fail("far region should fall back to plains, got " + far); return; }
        helper.succeed();
    }

    // ---- Item 4: the flooded-world noise_settings decodes and carries sea_level = 200 ----

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void example4_seaLevelFieldIsTwoHundred(GameTestHelper helper) {
        Path path = selftest("4_submerged/noise_settings.json");
        if (path == null) { helper.fail("4_submerged/noise_settings.json not found"); return; }
        NoiseGeneratorSettings settings;
        try {
            ServerLevel level = helper.getLevel();
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
            settings = NoiseGeneratorSettings.DIRECT_CODEC
                    .parse(ops, JsonParser.parseString(Files.readString(path)))
                    .getOrThrow(msg -> new AssertionError("noise_settings decode: " + msg));
        } catch (Exception e) {
            helper.fail("noise_settings could not be read/decoded: " + e);
            return;
        }
        if (settings.seaLevel() != 200) {
            helper.fail("sea_level should be 200, got " + settings.seaLevel());
            return;
        }
        if (settings.noiseRouter().finalDensity() == null) {
            helper.fail("flooded preset final_density (hook reference) did not resolve");
            return;
        }
        helper.succeed();
    }
}
