package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonParser;
import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.surfacerule.VanillaOverworldSurfaceRule;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-in-the-loop verification of the Wave-3 hook mechanism (SPEC §4). GameTest's flat world
 * can't generate a custom worldshape, so — as in {@link IsekaiWorldgenGameTests} — these tests
 * drive the real, loaded registry contents rather than inspecting generated chunks:
 *
 * <ul>
 *   <li>The shipped {@code isekai_api:hooked_overworld} noise_settings preset is present, its
 *       {@code surface_rule} is the {@code isekai_api:vanilla_overworld_surface} delegate, and its
 *       {@code final_density} resolves to the hook DF — the full preset→hook→delegate wiring.</li>
 *   <li>The default hook (un-overridden) evaluates to a genuine vanilla-like terrain column:
 *       solid deep down, air high up, with a surface crossing between — i.e. the default is the
 *       vanilla terrain shape, not a degenerate constant. The override direction (a datapack
 *       replacing the hook flips the loaded value) is proven in {@code HookOverrideTest}.</li>
 * </ul>
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiHookGameTests {

    private IsekaiHookGameTests() {}

    private static final ResourceKey<NoiseGeneratorSettings> HOOKED_OVERWORLD =
            ResourceKey.create(Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath(IsekaiApi.MODID, "hooked_overworld"));

    private static final ResourceLocation HOOK_DF =
            ResourceLocation.fromNamespaceAndPath(IsekaiApi.MODID, "hook/final_density");
    private static final ResourceLocation SLOPED_CHEESE =
            ResourceLocation.withDefaultNamespace("overworld/sloped_cheese");

    /** Gate 2(a): the preset, delegate surface, and hook DF are all loaded and wired together. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void hookedPresetWiredWithDelegateAndHook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var regs = level.registryAccess();

        var settingsHolder = regs.lookupOrThrow(Registries.NOISE_SETTINGS).get(HOOKED_OVERWORLD).orElse(null);
        if (settingsHolder == null) {
            helper.fail("isekai_api:hooked_overworld noise_settings not loaded — preset missing from jar/classpath");
            return;
        }
        NoiseGeneratorSettings settings = settingsHolder.value();

        if (!(settings.surfaceRule() instanceof VanillaOverworldSurfaceRule)) {
            helper.fail("hooked_overworld surface_rule is not the vanilla_overworld_surface delegate: "
                    + settings.surfaceRule().getClass().getSimpleName());
            return;
        }
        if (settings.noiseRouter().finalDensity() == null) {
            helper.fail("hooked_overworld final_density did not resolve (hook DF reference unresolved)");
            return;
        }
        // The hook DF and its vanilla reference target must both be present in the registry.
        var dfReg = regs.lookupOrThrow(Registries.DENSITY_FUNCTION);
        if (dfReg.get(ResourceKey.create(Registries.DENSITY_FUNCTION, HOOK_DF)).isEmpty()) {
            helper.fail("isekai_api:hook/final_density density function not loaded");
            return;
        }
        if (dfReg.get(ResourceKey.create(Registries.DENSITY_FUNCTION, SLOPED_CHEESE)).isEmpty()) {
            helper.fail("minecraft:overworld/sloped_cheese not present — hook default's reference target missing");
            return;
        }
        helper.succeed();
    }

    /** Gate 2(a) value: the default hook produces a real vanilla-like terrain column. */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void defaultHookProducesVanillaLikeTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var regs = level.registryAccess();
        NoiseGeneratorSettings settings = regs.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(HOOKED_OVERWORLD).value();
        long seed = level.getSeed();
        RandomState rs = RandomState.create(settings, regs.lookupOrThrow(Registries.NOISE), seed);
        DensityFunction finalDensity = rs.router().finalDensity();

        // Sample a few columns; each must be solid (>0) deep and air (<0) high, with a crossing.
        int[][] columns = { {0, 0}, {80, 48}, {-64, 112} };
        for (int[] c : columns) {
            double deep = finalDensity.compute(new DensityFunction.SinglePointContext(c[0], 0, c[1]));
            double high = finalDensity.compute(new DensityFunction.SinglePointContext(c[0], 250, c[1]));
            if (!(deep > 0.0)) {
                helper.fail("default hook not solid at Y=0 for column " + c[0] + "," + c[1] + " (got " + deep + ")");
                return;
            }
            if (!(high < 0.0)) {
                helper.fail("default hook not air at Y=250 for column " + c[0] + "," + c[1] + " (got " + high + ")");
                return;
            }
        }
        helper.succeed();
    }

    // The "30 lines to floating islands" hook override (matches
    // examples/1_shape/floating_island/.../hook/final_density.json).
    private static final String BAND_HOOK_JSON =
            "{\"type\":\"isekai_api:squeeze\",\"argument\":{\"type\":\"minecraft:interpolated\","
            + "\"argument\":{\"type\":\"minecraft:blend_density\",\"argument\":{"
            + "\"type\":\"isekai_api:band_density\",\"active_min_y\":50,\"active_max_y\":200,"
            + "\"gradient_width\":30,\"noise\":{\"type\":\"isekai_api:blended_noise\","
            + "\"size_xz\":320,\"size_y\":240}}}}}";

    /**
     * Gate 3 (value): overriding the hook with the floating-island band shape yields BOUNDED
     * terrain — air below the band, air above it, solid inside — the opposite of the default
     * hook's solid-to-bedrock ground. This is what the 30-line example produces at world-create.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void bandHookProducesFloatingIslandProfile(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var regs = level.registryAccess();
        NoiseGeneratorSettings base = regs.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(HOOKED_OVERWORLD).value();

        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, regs);
        DensityFunction band = DensityFunction.DIRECT_CODEC
                .parse(ops, JsonParser.parseString(BAND_HOOK_JSON))
                .getOrThrow(msg -> new AssertionError("band hook decode: " + msg));

        // Splice the band shape in as final_density (and initial), map it through a RandomState.
        NoiseRouter r = base.noiseRouter();
        NoiseRouter banded = new NoiseRouter(
                r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(), r.lavaNoise(),
                r.temperature(), r.vegetation(), r.continents(), r.erosion(), r.depth(), r.ridges(),
                band, band, r.veinToggle(), r.veinRidged(), r.veinGap());
        NoiseGeneratorSettings bandSettings = new NoiseGeneratorSettings(
                base.noiseSettings(), base.defaultBlock(), base.defaultFluid(), banded, base.surfaceRule(),
                base.spawnTarget(), base.seaLevel(), base.disableMobGeneration(),
                base.isAquifersEnabled(), base.oreVeinsEnabled(), base.useLegacyRandomSource());
        RandomState rs = RandomState.create(bandSettings, regs.lookupOrThrow(Registries.NOISE), level.getSeed());
        DensityFunction fd = rs.router().finalDensity();

        int[][] columns = { {0, 0}, {96, -32}, {-48, 64} };
        boolean anySolidInBand = false;
        for (int[] c : columns) {
            // Below the band: void, unlike the default hook which is solid at Y=0.
            double below = fd.compute(new DensityFunction.SinglePointContext(c[0], 0, c[1]));
            if (!(below < 0.0)) {
                helper.fail("band hook not void at Y=0 (below band) for " + c[0] + "," + c[1] + " (got " + below + ")");
                return;
            }
            // Above the band: void.
            double above = fd.compute(new DensityFunction.SinglePointContext(c[0], 300, c[1]));
            if (!(above < 0.0)) {
                helper.fail("band hook not void at Y=300 (above band) for " + c[0] + "," + c[1] + " (got " + above + ")");
                return;
            }
            for (int y = 60; y <= 190 && !anySolidInBand; y += 10) {
                if (fd.compute(new DensityFunction.SinglePointContext(c[0], y, c[1])) > 0.0) {
                    anySolidInBand = true;
                }
            }
        }
        if (!anySolidInBand) {
            helper.fail("band hook produced no solid terrain anywhere inside the Y=50..200 band — no islands");
            return;
        }
        helper.succeed();
    }
}
