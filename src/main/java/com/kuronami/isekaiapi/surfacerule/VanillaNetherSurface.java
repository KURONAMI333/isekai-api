package com.kuronami.isekaiapi.surfacerule;

import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

import org.jetbrains.annotations.ApiStatus;

/**
 * The vanilla nether surface-rule tree, rebuilt from runtime worldgen factories only.
 *
 * <p>The companion of {@link VanillaOverworldSurface} for {@code minecraft:the_nether}: the
 * nether's surface (bedrock floor/roof, netherrack, basalt deltas, soul sand valleys, lava
 * sea, warped/crimson nylium, nether-wastes soul sand and gravel) is normally a ~450-line
 * inline block in {@code data/minecraft/worldgen/noise_settings/nether.json}. Rebuilding it
 * here lets a consumer's {@code noise_settings} say
 * {@code "surface_rule": {"type": "isekai_api:vanilla_nether_surface"}} instead of copying
 * that JSON.
 *
 * <p>The tree is a faithful port of {@code SurfaceRuleData.nether()} (1.21.1); only symbol
 * changes are applied: the block-state constants are inlined via {@link #state(Block)}.
 * It is stateless and reads no registry, so it is safe inside a replaced
 * {@code minecraft:nether} noise_settings.
 * @since 3.0.0
 */
@ApiStatus.Internal
public final class VanillaNetherSurface {

    private VanillaNetherSurface() {}

    private static final SurfaceRules.RuleSource TREE = nether();

    /** @return the vanilla nether surface-rule tree (cached, immutable). */
    public static SurfaceRules.RuleSource tree() {
        return TREE;
    }

    private static SurfaceRules.RuleSource state(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }

    private static SurfaceRules.RuleSource nether() {
        SurfaceRules.RuleSource bedrock = state(Blocks.BEDROCK);
        SurfaceRules.RuleSource netherrack = state(Blocks.NETHERRACK);
        SurfaceRules.RuleSource basalt = state(Blocks.BASALT);
        SurfaceRules.RuleSource blackstone = state(Blocks.BLACKSTONE);
        SurfaceRules.RuleSource soulSand = state(Blocks.SOUL_SAND);
        SurfaceRules.RuleSource soulSoil = state(Blocks.SOUL_SOIL);
        SurfaceRules.RuleSource gravel = state(Blocks.GRAVEL);
        SurfaceRules.RuleSource lava = state(Blocks.LAVA);
        SurfaceRules.RuleSource warpedWartBlock = state(Blocks.WARPED_WART_BLOCK);
        SurfaceRules.RuleSource warpedNylium = state(Blocks.WARPED_NYLIUM);
        SurfaceRules.RuleSource netherWartBlock = state(Blocks.NETHER_WART_BLOCK);
        SurfaceRules.RuleSource crimsonNylium = state(Blocks.CRIMSON_NYLIUM);

        SurfaceRules.ConditionSource below31 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(31), 0);
        SurfaceRules.ConditionSource below32 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(32), 0);
        SurfaceRules.ConditionSource above30 = SurfaceRules.yStartCheck(VerticalAnchor.absolute(30), 0);
        SurfaceRules.ConditionSource below35 = SurfaceRules.not(
                SurfaceRules.yStartCheck(VerticalAnchor.absolute(35), 0));
        SurfaceRules.ConditionSource belowTop5 = SurfaceRules.yBlockCheck(VerticalAnchor.belowTop(5), 0);
        SurfaceRules.ConditionSource hole = SurfaceRules.hole();
        SurfaceRules.ConditionSource soulSandLayer = SurfaceRules.noiseCondition(Noises.SOUL_SAND_LAYER, -0.012);
        SurfaceRules.ConditionSource gravelLayer = SurfaceRules.noiseCondition(Noises.GRAVEL_LAYER, -0.012);
        SurfaceRules.ConditionSource patch = SurfaceRules.noiseCondition(Noises.PATCH, -0.012);
        SurfaceRules.ConditionSource netherrackNoise = SurfaceRules.noiseCondition(Noises.NETHERRACK, 0.54);
        SurfaceRules.ConditionSource netherWartNoise = SurfaceRules.noiseCondition(Noises.NETHER_WART, 1.17);
        SurfaceRules.ConditionSource stateSelector = SurfaceRules.noiseCondition(Noises.NETHER_STATE_SELECTOR, 0.0);

        SurfaceRules.RuleSource gravelPatch = SurfaceRules.ifTrue(
                patch, SurfaceRules.ifTrue(above30, SurfaceRules.ifTrue(below35, gravel)));

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)),
                        bedrock),
                SurfaceRules.ifTrue(
                        SurfaceRules.not(SurfaceRules.verticalGradient("bedrock_roof",
                                VerticalAnchor.belowTop(5), VerticalAnchor.top())),
                        bedrock),
                SurfaceRules.ifTrue(belowTop5, netherrack),
                SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.BASALT_DELTAS),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_CEILING, basalt),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.UNDER_FLOOR,
                                        SurfaceRules.sequence(
                                                gravelPatch,
                                                SurfaceRules.ifTrue(stateSelector, basalt),
                                                blackstone)))),
                SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.SOUL_SAND_VALLEY),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(
                                        SurfaceRules.UNDER_CEILING,
                                        SurfaceRules.sequence(
                                                SurfaceRules.ifTrue(stateSelector, soulSand), soulSoil)),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.UNDER_FLOOR,
                                        SurfaceRules.sequence(
                                                gravelPatch,
                                                SurfaceRules.ifTrue(stateSelector, soulSand), soulSoil)))),
                SurfaceRules.ifTrue(
                        SurfaceRules.ON_FLOOR,
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(
                                        SurfaceRules.not(below32),
                                        SurfaceRules.ifTrue(hole, lava)),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.isBiome(Biomes.WARPED_FOREST),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.not(netherrackNoise),
                                                SurfaceRules.ifTrue(
                                                        below31,
                                                        SurfaceRules.sequence(
                                                                SurfaceRules.ifTrue(netherWartNoise, warpedWartBlock),
                                                                warpedNylium)))),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.isBiome(Biomes.CRIMSON_FOREST),
                                        SurfaceRules.ifTrue(
                                                SurfaceRules.not(netherrackNoise),
                                                SurfaceRules.ifTrue(
                                                        below31,
                                                        SurfaceRules.sequence(
                                                                SurfaceRules.ifTrue(netherWartNoise, netherWartBlock),
                                                                crimsonNylium)))))),
                SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.NETHER_WASTES),
                        SurfaceRules.sequence(
                                SurfaceRules.ifTrue(
                                        SurfaceRules.UNDER_FLOOR,
                                        SurfaceRules.ifTrue(
                                                soulSandLayer,
                                                SurfaceRules.sequence(
                                                        SurfaceRules.ifTrue(
                                                                SurfaceRules.not(hole),
                                                                SurfaceRules.ifTrue(above30,
                                                                        SurfaceRules.ifTrue(below35, soulSand))),
                                                        netherrack))),
                                SurfaceRules.ifTrue(
                                        SurfaceRules.ON_FLOOR,
                                        SurfaceRules.ifTrue(
                                                below31,
                                                SurfaceRules.ifTrue(
                                                        below35,
                                                        SurfaceRules.ifTrue(
                                                                gravelLayer,
                                                                SurfaceRules.sequence(
                                                                        SurfaceRules.ifTrue(below32, gravel),
                                                                        SurfaceRules.ifTrue(
                                                                                SurfaceRules.not(hole), gravel)))))))),
                netherrack);
    }
}
