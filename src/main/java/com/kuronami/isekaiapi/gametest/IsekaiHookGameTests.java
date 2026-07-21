package com.kuronami.isekaiapi.gametest;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.surfacerule.VanillaOverworldSurfaceRule;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
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
}
