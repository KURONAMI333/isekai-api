package com.kuronami.isekaiapi.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verifies that the {@code isekai_verify:surface_override} dimension wires
 * {@code isekai_api:worldshape_surface_top} <em>bare</em> — no external {@code minecraft:stone_depth}
 * wrapper — and that this bare wiring is a valid, round-tripping surface rule.
 *
 * <p>Why not probe generated blocks. The datapack dimension is not instantiated as a
 * {@link net.minecraft.server.level.ServerLevel} by the GameTest server (it boots only
 * overworld/nether/end), so a surface-rule-applied chunk of {@code surface_override} cannot be
 * generated here — the same GameTest-unreachability recorded for surface generation since W1. The
 * runtime behavioral proof (bare wiring paints the top block only: {@code red_concrete} at the
 * surface, {@code dirt} directly below) is the W5 RCON block-probe on a real dedicated server; it
 * remains the behavioral ceiling and is documented in {@code GAP_LOG.md}. This test guards the
 * datapack-facing half: the wrapper is gone and the bare rule is well-formed. The self-gate itself
 * (that {@code worldshape_surface_top} carries vanilla's {@code ON_FLOOR} condition internally) is
 * unit-tested in {@code WorldshapeSurfaceTopRuleTest}, and is byte-identical to the external
 * {@code stone_depth(offset 0, floor, add_surface_depth false)} wrapper W5 proved.
 */
@GameTestHolder(IsekaiApi.MODID)
public final class IsekaiSurfaceOverrideGameTests {

    private IsekaiSurfaceOverrideGameTests() {}

    private static final ResourceKey<NoiseGeneratorSettings> SURFACE_OVERRIDE_SETTINGS =
            ResourceKey.create(Registries.NOISE_SETTINGS,
                    ResourceLocation.fromNamespaceAndPath("isekai_verify", "surface_override_settings"));

    /**
     * The loaded {@code surface_override_settings} has, as the first element of its surface_rule
     * sequence, a bare {@code isekai_api:worldshape_surface_top} — not a {@code minecraft:condition}
     * wrapping it in {@code stone_depth}. Re-encoding the live rule and inspecting it proves the W5
     * wrapper was removed and the bare wiring still decodes and round-trips.
     */
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty3x3x3")
    public static void bareSurfaceTopWiringDecodesWithoutStoneDepthWrapper(GameTestHelper helper) {
        var regs = helper.getLevel().registryAccess();
        var holder = regs.lookupOrThrow(Registries.NOISE_SETTINGS).get(SURFACE_OVERRIDE_SETTINGS).orElse(null);
        if (holder == null) {
            helper.fail("isekai_verify:surface_override_settings not loaded");
            return;
        }
        SurfaceRules.RuleSource surfaceRule = holder.value().surfaceRule();

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, regs);
        JsonElement json = SurfaceRules.RuleSource.CODEC.encodeStart(ops, surfaceRule)
                .getOrThrow(msg -> new AssertionError("surface_rule re-encode failed: " + msg));

        JsonObject root = json.getAsJsonObject();
        String rootType = root.get("type").getAsString();
        if (!"minecraft:sequence".equals(rootType)) {
            helper.fail("surface_rule root is " + rootType + ", expected minecraft:sequence");
            return;
        }
        JsonElement firstEl = root.getAsJsonArray("sequence").get(0);
        String firstType = firstEl.getAsJsonObject().get("type").getAsString();
        if (!"isekai_api:worldshape_surface_top".equals(firstType)) {
            helper.fail("first surface_rule element is " + firstType
                    + ", expected a bare isekai_api:worldshape_surface_top (W5 stone_depth wrapper not removed?)");
            return;
        }
        // Belt and suspenders: the surface_rule carries no stone_depth wrapper anywhere.
        String all = json.toString();
        if (all.contains("stone_depth") || all.contains("minecraft:condition")) {
            helper.fail("surface_rule still contains a stone_depth / minecraft:condition wrapper: " + all);
            return;
        }
        helper.succeed();
    }
}
