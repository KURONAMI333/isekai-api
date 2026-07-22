package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.ModifyPhase;
import com.kuronami.isekaiapi.biomesource.RuleBiomeSource;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Acceptance test for the "planets in the void" world — the blind test of whether v2 can express
 * a cosmos of multiple distinct spheres floating in empty space with datapack JSON only, no API
 * change. The scratch datapack lives under {@code data/isekai_verify/} (dev-loaded, jar-excluded);
 * these asserts read the <em>registry-loaded</em> objects (not standalone files) so any silent
 * contamination — e.g. wrapping the sphere union in {@code squeeze}, which would clamp planet
 * interiors to air — is caught in situ.
 *
 * <p><b>Coverage ceiling.</b> The GameTestServer only builds ServerLevels for overworld/nether/end,
 * so it cannot generate chunks of the {@code isekai_verify:cosmos} dimension. These tests prove the
 * <em>shape formula</em> (multi-sphere density evaluated through the real noise_settings router) and
 * the <em>zoning/atmosphere data</em>; the block-for-block reification of the planets is the
 * dedicated-server RCON probe / kura visual gate (same ceiling as W5).
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiCosmosGameTests {

    private IsekaiCosmosGameTests() {}

    private static final ResourceLocation COSMOS_SETTINGS =
            ResourceLocation.fromNamespaceAndPath("isekai_verify", "cosmos_settings");

    private static Path verifyFile(String relative) {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path c = p.resolve("src/main/resources/data/isekai_verify").resolve(relative);
            if (Files.isRegularFile(c)) return c;
        }
        return null;
    }

    private static double at(DensityFunction f, int x, int y, int z) {
        return f.compute(new DensityFunction.SinglePointContext(x, y, z));
    }

    // =====================================================================
    // Planets exist: multiple spheres of varied size are solid at their centres and void between.
    // Evaluated through the registry-loaded noise_settings' final_density (raw union, no squeeze).
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void planetsSolidAtCentresVoidBetween(GameTestHelper helper) {
        NoiseGeneratorSettings settings = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS)
                .get(ResourceKey.create(Registries.NOISE_SETTINGS, COSMOS_SETTINGS))
                .map(Holder.Reference::value)
                .orElse(null);
        if (settings == null) { helper.fail("cosmos_settings not registered"); return; }
        DensityFunction f = settings.noiseRouter().finalDensity();

        // Three planet centres -> solid (positive). Verdant r48 @ (0,96,0), Ember r64 @ (480,128,240),
        // Frost r28 @ (-360,88,400).
        double verdantCore = at(f, 0, 96, 0);
        double emberCore = at(f, 480, 128, 240);
        double frostCore = at(f, -360, 88, 400);
        if (!(verdantCore > 0.0)) { helper.fail("Verdant planet not solid at its centre (got " + verdantCore + ")"); return; }
        if (!(emberCore > 0.0)) { helper.fail("Ember planet not solid at its centre (got " + emberCore + ")"); return; }
        if (!(frostCore > 0.0)) { helper.fail("Frost planet not solid at its centre (got " + frostCore + ")"); return; }

        // The void between planets and the far empty space -> air (negative).
        double gap = at(f, 240, 112, 120);      // midway between Verdant and Ember
        double deepSpace = at(f, 2000, 200, 2000);
        if (!(gap < 0.0)) { helper.fail("space between planets is not void (got " + gap + ")"); return; }
        if (!(deepSpace < 0.0)) { helper.fail("deep space is not void (got " + deepSpace + ")"); return; }

        // The squeeze-trap discriminator: a raw sphere union yields +48 at Verdant's core. If the
        // union were wrapped in squeeze, x=48 would clamp to about -1 (interior flips to air). Assert
        // the interior carries the un-squeezed magnitude, proving the planet is a solid body, not a shell.
        if (!(verdantCore > 40.0)) {
            helper.fail("Verdant core density " + verdantCore + " is squeezed/clamped — planet is a hollow shell, not a body");
            return;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void planetsHaveVariedSize(GameTestHelper helper) {
        NoiseGeneratorSettings settings = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS)
                .get(ResourceKey.create(Registries.NOISE_SETTINGS, COSMOS_SETTINGS))
                .map(Holder.Reference::value)
                .orElse(null);
        if (settings == null) { helper.fail("cosmos_settings not registered"); return; }
        DensityFunction f = settings.noiseRouter().finalDensity();

        // At radial distance 55 from a centre: solid for the big planet (Ember r64), void for the
        // small one (Verdant r48). Same distance, opposite verdict -> the planets are genuinely
        // different sizes, not clones ("たち").
        double bigAt55 = at(f, 535, 128, 240);   // 55 blocks +x from Ember's centre
        double smallAt55 = at(f, 55, 96, 0);     // 55 blocks +x from Verdant's centre
        if (!(bigAt55 > 0.0)) { helper.fail("big planet (Ember r64) not solid at radius 55 (got " + bigAt55 + ")"); return; }
        if (!(smallAt55 < 0.0)) { helper.fail("small planet (Verdant r48) should be void at radius 55 (got " + smallAt55 + ")"); return; }

        // The shell edge is crisp: just inside Verdant's r48 is solid, just outside is void.
        double justInside = at(f, 0, 140, 0);    // r=44 < 48
        double justOutside = at(f, 0, 150, 0);   // r=54 > 48
        if (!(justInside > 0.0)) { helper.fail("Verdant not solid just inside its shell (got " + justInside + ")"); return; }
        if (!(justOutside < 0.0)) { helper.fail("Verdant not void just outside its shell (got " + justOutside + ")"); return; }
        helper.succeed();
    }

    // =====================================================================
    // Per-planet identity: the isekai_api:rule biome source zones each planet's region to its own
    // biome by XZ, deep space falls back to the void biome. Read from the loaded dimension's generator.
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void eachPlanetGetsItsOwnBiome(GameTestHelper helper) {
        // The dimension JSON loaded at server start (validateNamespace reports 0 errors), but the
        // GameTestServer prunes custom-dimension level stems, so we decode the dimension's own
        // biome_source through the real BiomeSource.CODEC against the live registry — the same
        // registry-resolved zoning the world would use.
        Path path = verifyFile("dimension/cosmos.json");
        if (path == null) { helper.fail("cosmos dimension JSON not found on disk"); return; }
        BiomeSource source;
        try {
            JsonObject dim = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonElement biomeSourceJson = dim.getAsJsonObject("generator").get("biome_source");
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
            source = BiomeSource.CODEC.parse(ops, biomeSourceJson)
                    .getOrThrow(msg -> new AssertionError("cosmos biome_source decode: " + msg));
        } catch (Exception e) {
            helper.fail("cosmos biome_source could not be read/decoded: " + e);
            return;
        }
        if (!(source instanceof RuleBiomeSource)) {
            helper.fail("cosmos biome source is not isekai_api:rule: " + source.getClass().getSimpleName());
            return;
        }

        // getNoiseBiome takes quart (biome-grid) coords; zones are authored in block space (x4).
        Holder<Biome> verdant = source.getNoiseBiome(0, 24, 0, null);            // block (0,_,0)  -> Verdant zone
        Holder<Biome> ember = source.getNoiseBiome(120, 32, 60, null);           // block (480,_,240) -> Ember zone
        Holder<Biome> frost = source.getNoiseBiome(-90, 22, 100, null);          // block (-360,_,400) -> Frost zone
        Holder<Biome> deepSpace = source.getNoiseBiome(250_000, 50, 0, null);    // block (1,000,000) -> void fallback

        if (!isBiome(verdant, "planet_verdant")) { helper.fail("Verdant region biome wrong: " + keyOf(verdant)); return; }
        if (!isBiome(ember, "planet_ember")) { helper.fail("Ember region biome wrong: " + keyOf(ember)); return; }
        if (!isBiome(frost, "planet_frost")) { helper.fail("Frost region biome wrong: " + keyOf(frost)); return; }
        if (!isBiome(deepSpace, "planet_void")) { helper.fail("deep space biome should be planet_void: " + keyOf(deepSpace)); return; }
        helper.succeed();
    }

    private static boolean isBiome(Holder<Biome> holder, String path) {
        return holder.is(ResourceLocation.fromNamespaceAndPath("isekai_verify", path));
    }

    private static String keyOf(Holder<Biome> holder) {
        return holder.unwrapKey().map(k -> k.location().toString()).orElse("<unnamed>");
    }

    // =====================================================================
    // Cosmic atmosphere: the worldshape's atmosphere override applies its dark-space colours to a
    // biome's special effects. Decoded from the on-disk cosmos descriptor (the real datapack).
    // (Whether the_end sky renderer *displays* biome sky_color is a client concern — the visual is a
    // kura gate; here we prove the data is applied.)
    // =====================================================================

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void cosmicAtmosphereApplies(GameTestHelper helper) {
        Path path = verifyFile("isekai/worldshape/cosmos.json");
        if (path == null) { helper.fail("cosmos worldshape descriptor not found on disk"); return; }
        WorldshapeDescriptor descriptor;
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
            descriptor = WorldshapeDescriptor.CODEC
                    .parse(ops, JsonParser.parseString(Files.readString(path)))
                    .getOrThrow(msg -> new AssertionError("cosmos descriptor decode: " + msg));
        } catch (Exception e) {
            helper.fail("cosmos descriptor could not be read/decoded: " + e);
            return;
        }

        Biome plains = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value();
        var builder = ModifiableBiomeInfo.BiomeInfo.Builder.copyOf(plains.modifiableBiomeInfo().getOriginalBiomeInfo());
        ModifyPhase.atmosphereOverride(descriptor, builder);
        var effects = builder.build().effects();

        if (effects.getSkyColor() != 0) {
            helper.fail("cosmic sky_color override not applied: wanted 0x000000, got 0x" + Integer.toHexString(effects.getSkyColor()));
            return;
        }
        if (effects.getFogColor() != 526344) {
            helper.fail("cosmic fog_color override not applied: wanted 526344, got " + effects.getFogColor());
            return;
        }
        helper.succeed();
    }
}
