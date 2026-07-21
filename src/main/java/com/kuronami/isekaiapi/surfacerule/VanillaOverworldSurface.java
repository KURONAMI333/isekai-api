package com.kuronami.isekaiapi.surfacerule;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

import org.jetbrains.annotations.ApiStatus;

/**
 * The vanilla overworld surface-rule tree, rebuilt from runtime worldgen factories only
 * ({@link SurfaceRules}, {@link Noises}, {@link VerticalAnchor}, {@link Biomes}, {@link Blocks}).
 *
 * <p>This is the same tree Mojang authors in {@code net.minecraft.data.worldgen.SurfaceRuleData}
 * — but that class lives in the {@code net.minecraft.data} (datagen) package, which is not a
 * guaranteed part of a running server's classpath, and it only exists to <em>emit</em> the
 * expanded 30&nbsp;KB {@code surface_rule} JSON baked into {@code overworld.json} at data-gen
 * time. Reconstructing it here from the always-present {@code net.minecraft.world.level.levelgen}
 * factories lets {@link VanillaOverworldSurfaceRule} hand a consumer the exact vanilla overworld
 * surface as a single {@code isekai_api:vanilla_overworld_surface} rule, with no per-consumer
 * copy of that JSON and no dependency on the datagen package.
 *
 * <p>The tree is stateless and pure (it reads nothing from any registry), so it is safe to use
 * inside a replaced {@code minecraft:overworld} noise_settings — unlike reading the loaded
 * overworld rule back out of the registry, which would recurse into itself.
 */
@ApiStatus.Internal
public final class VanillaOverworldSurface {

    private VanillaOverworldSurface() {}

    /** Built once (the tree is immutable). {@code overworldLike(true, false, true)} == vanilla overworld. */
    private static final SurfaceRules.RuleSource TREE = overworldLike(true, false, true);

    /** @return the vanilla overworld surface-rule tree (cached, immutable). */
    public static SurfaceRules.RuleSource tree() {
        return TREE;
    }

    private static SurfaceRules.RuleSource state(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }

    private static SurfaceRules.ConditionSource surfaceNoiseAbove(double value) {
        return SurfaceRules.noiseCondition(Noises.SURFACE, value / 8.25, Double.MAX_VALUE);
    }

    /**
     * Faithful port of {@code SurfaceRuleData.overworldLike}. Only symbol changes vs. vanilla:
     * the block-state constants are inlined via {@link #state(Block)} instead of static fields.
     */
    private static SurfaceRules.RuleSource overworldLike(boolean aboveGround, boolean bedrockRoof, boolean bedrockFloor) {
        SurfaceRules.RuleSource air = state(Blocks.AIR);
        SurfaceRules.RuleSource bedrock = state(Blocks.BEDROCK);
        SurfaceRules.RuleSource whiteTerracotta = state(Blocks.WHITE_TERRACOTTA);
        SurfaceRules.RuleSource orangeTerracotta = state(Blocks.ORANGE_TERRACOTTA);
        SurfaceRules.RuleSource terracotta = state(Blocks.TERRACOTTA);
        SurfaceRules.RuleSource redSand = state(Blocks.RED_SAND);
        SurfaceRules.RuleSource redSandstone = state(Blocks.RED_SANDSTONE);
        SurfaceRules.RuleSource stone = state(Blocks.STONE);
        SurfaceRules.RuleSource deepslate = state(Blocks.DEEPSLATE);
        SurfaceRules.RuleSource dirt = state(Blocks.DIRT);
        SurfaceRules.RuleSource podzol = state(Blocks.PODZOL);
        SurfaceRules.RuleSource coarseDirt = state(Blocks.COARSE_DIRT);
        SurfaceRules.RuleSource mycelium = state(Blocks.MYCELIUM);
        SurfaceRules.RuleSource grassBlock = state(Blocks.GRASS_BLOCK);
        SurfaceRules.RuleSource calcite = state(Blocks.CALCITE);
        SurfaceRules.RuleSource gravel = state(Blocks.GRAVEL);
        SurfaceRules.RuleSource sand = state(Blocks.SAND);
        SurfaceRules.RuleSource sandstone = state(Blocks.SANDSTONE);
        SurfaceRules.RuleSource packedIce = state(Blocks.PACKED_ICE);
        SurfaceRules.RuleSource snowBlock = state(Blocks.SNOW_BLOCK);
        SurfaceRules.RuleSource mud = state(Blocks.MUD);
        SurfaceRules.RuleSource powderSnow = state(Blocks.POWDER_SNOW);
        SurfaceRules.RuleSource ice = state(Blocks.ICE);
        SurfaceRules.RuleSource water = state(Blocks.WATER);

        SurfaceRules.ConditionSource yBelow97 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(97), 2);
        SurfaceRules.ConditionSource yBelow256 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(256), 0);
        SurfaceRules.ConditionSource yStart63 = SurfaceRules.yStartCheck(VerticalAnchor.absolute(63), -1);
        SurfaceRules.ConditionSource yStart74 = SurfaceRules.yStartCheck(VerticalAnchor.absolute(74), 1);
        SurfaceRules.ConditionSource yBelow60 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(60), 0);
        SurfaceRules.ConditionSource yBelow62 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(62), 0);
        SurfaceRules.ConditionSource yBelow63 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(63), 0);
        SurfaceRules.ConditionSource waterM1 = SurfaceRules.waterBlockCheck(-1, 0);
        SurfaceRules.ConditionSource water0 = SurfaceRules.waterBlockCheck(0, 0);
        SurfaceRules.ConditionSource waterStartM6 = SurfaceRules.waterStartCheck(-6, -1);
        SurfaceRules.ConditionSource hole = SurfaceRules.hole();
        SurfaceRules.ConditionSource frozenOcean = SurfaceRules.isBiome(Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN);
        SurfaceRules.ConditionSource steep = SurfaceRules.steep();

        SurfaceRules.RuleSource grassSurface = SurfaceRules.sequence(SurfaceRules.ifTrue(water0, grassBlock), dirt);
        SurfaceRules.RuleSource sandSurface = SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, sandstone), sand);
        SurfaceRules.RuleSource gravelSurface = SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, stone), gravel);
        SurfaceRules.ConditionSource beachBiomes = SurfaceRules.isBiome(Biomes.WARM_OCEAN, Biomes.BEACH, Biomes.SNOWY_BEACH);
        SurfaceRules.ConditionSource desert = SurfaceRules.isBiome(Biomes.DESERT);

        SurfaceRules.RuleSource stonyAndDesert = SurfaceRules.sequence(
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.STONY_PEAKS),
                SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.CALCITE, -0.0125, 0.0125), calcite), stone)
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.STONY_SHORE),
                SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.GRAVEL, -0.05, 0.05), gravelSurface), stone)
            ),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.WINDSWEPT_HILLS), SurfaceRules.ifTrue(surfaceNoiseAbove(1.0), stone)),
            SurfaceRules.ifTrue(beachBiomes, sandSurface),
            SurfaceRules.ifTrue(desert, sandSurface),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.DRIPSTONE_CAVES), stone)
        );
        SurfaceRules.RuleSource powderSnow45 = SurfaceRules.ifTrue(
            SurfaceRules.noiseCondition(Noises.POWDER_SNOW, 0.45, 0.58), SurfaceRules.ifTrue(water0, powderSnow)
        );
        SurfaceRules.RuleSource powderSnow35 = SurfaceRules.ifTrue(
            SurfaceRules.noiseCondition(Noises.POWDER_SNOW, 0.35, 0.6), SurfaceRules.ifTrue(water0, powderSnow)
        );
        SurfaceRules.RuleSource highSurface = SurfaceRules.sequence(
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.FROZEN_PEAKS),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(steep, packedIce),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.PACKED_ICE, -0.5, 0.2), packedIce),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.ICE, -0.0625, 0.025), ice),
                    SurfaceRules.ifTrue(water0, snowBlock)
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.SNOWY_SLOPES),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(steep, stone),
                    powderSnow45,
                    SurfaceRules.ifTrue(water0, snowBlock)
                )
            ),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.JAGGED_PEAKS), stone),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.GROVE), SurfaceRules.sequence(powderSnow45, dirt)),
            stonyAndDesert,
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.WINDSWEPT_SAVANNA), SurfaceRules.ifTrue(surfaceNoiseAbove(1.75), stone)),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.WINDSWEPT_GRAVELLY_HILLS),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(surfaceNoiseAbove(2.0), gravelSurface),
                    SurfaceRules.ifTrue(surfaceNoiseAbove(1.0), stone),
                    SurfaceRules.ifTrue(surfaceNoiseAbove(-1.0), dirt),
                    gravelSurface
                )
            ),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.MANGROVE_SWAMP), mud),
            dirt
        );
        SurfaceRules.RuleSource midSurface = SurfaceRules.sequence(
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.FROZEN_PEAKS),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(steep, packedIce),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.PACKED_ICE, 0.0, 0.2), packedIce),
                    SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.ICE, 0.0, 0.025), ice),
                    SurfaceRules.ifTrue(water0, snowBlock)
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.SNOWY_SLOPES),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(steep, stone),
                    powderSnow35,
                    SurfaceRules.ifTrue(water0, snowBlock)
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.JAGGED_PEAKS),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(steep, stone), SurfaceRules.ifTrue(water0, snowBlock)
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.GROVE),
                SurfaceRules.sequence(powderSnow35, SurfaceRules.ifTrue(water0, snowBlock))
            ),
            stonyAndDesert,
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.WINDSWEPT_SAVANNA),
                SurfaceRules.sequence(SurfaceRules.ifTrue(surfaceNoiseAbove(1.75), stone), SurfaceRules.ifTrue(surfaceNoiseAbove(-0.5), coarseDirt))
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.WINDSWEPT_GRAVELLY_HILLS),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(surfaceNoiseAbove(2.0), gravelSurface),
                    SurfaceRules.ifTrue(surfaceNoiseAbove(1.0), stone),
                    SurfaceRules.ifTrue(surfaceNoiseAbove(-1.0), grassSurface),
                    gravelSurface
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA),
                SurfaceRules.sequence(SurfaceRules.ifTrue(surfaceNoiseAbove(1.75), coarseDirt), SurfaceRules.ifTrue(surfaceNoiseAbove(-0.95), podzol))
            ),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.ICE_SPIKES), SurfaceRules.ifTrue(water0, snowBlock)),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.MANGROVE_SWAMP), mud),
            SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.MUSHROOM_FIELDS), mycelium),
            grassSurface
        );
        SurfaceRules.ConditionSource surfaceNoiseLow = SurfaceRules.noiseCondition(Noises.SURFACE, -0.909, -0.5454);
        SurfaceRules.ConditionSource surfaceNoiseMid = SurfaceRules.noiseCondition(Noises.SURFACE, -0.1818, 0.1818);
        SurfaceRules.ConditionSource surfaceNoiseHigh = SurfaceRules.noiseCondition(Noises.SURFACE, 0.5454, 0.909);
        SurfaceRules.RuleSource body = SurfaceRules.sequence(
            SurfaceRules.ifTrue(
                SurfaceRules.ON_FLOOR,
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.WOODED_BADLANDS),
                        SurfaceRules.ifTrue(
                            yBelow97,
                            SurfaceRules.sequence(
                                SurfaceRules.ifTrue(surfaceNoiseLow, coarseDirt),
                                SurfaceRules.ifTrue(surfaceNoiseMid, coarseDirt),
                                SurfaceRules.ifTrue(surfaceNoiseHigh, coarseDirt),
                                grassSurface
                            )
                        )
                    ),
                    SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.SWAMP),
                        SurfaceRules.ifTrue(
                            yBelow62,
                            SurfaceRules.ifTrue(
                                SurfaceRules.not(yBelow63), SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SWAMP, 0.0), water)
                            )
                        )
                    ),
                    SurfaceRules.ifTrue(
                        SurfaceRules.isBiome(Biomes.MANGROVE_SWAMP),
                        SurfaceRules.ifTrue(
                            yBelow60,
                            SurfaceRules.ifTrue(
                                SurfaceRules.not(yBelow63), SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SWAMP, 0.0), water)
                            )
                        )
                    )
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.isBiome(Biomes.BADLANDS, Biomes.ERODED_BADLANDS, Biomes.WOODED_BADLANDS),
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(
                        SurfaceRules.ON_FLOOR,
                        SurfaceRules.sequence(
                            SurfaceRules.ifTrue(yBelow256, orangeTerracotta),
                            SurfaceRules.ifTrue(
                                yStart74,
                                SurfaceRules.sequence(
                                    SurfaceRules.ifTrue(surfaceNoiseLow, terracotta),
                                    SurfaceRules.ifTrue(surfaceNoiseMid, terracotta),
                                    SurfaceRules.ifTrue(surfaceNoiseHigh, terracotta),
                                    SurfaceRules.bandlands()
                                )
                            ),
                            SurfaceRules.ifTrue(
                                waterM1, SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_CEILING, redSandstone), redSand)
                            ),
                            SurfaceRules.ifTrue(SurfaceRules.not(hole), orangeTerracotta),
                            SurfaceRules.ifTrue(waterStartM6, whiteTerracotta),
                            gravelSurface
                        )
                    ),
                    SurfaceRules.ifTrue(
                        yStart63,
                        SurfaceRules.sequence(
                            SurfaceRules.ifTrue(
                                yBelow63, SurfaceRules.ifTrue(SurfaceRules.not(yStart74), orangeTerracotta)
                            ),
                            SurfaceRules.bandlands()
                        )
                    ),
                    SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.ifTrue(waterStartM6, whiteTerracotta))
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.ON_FLOOR,
                SurfaceRules.ifTrue(
                    waterM1,
                    SurfaceRules.sequence(
                        SurfaceRules.ifTrue(
                            frozenOcean,
                            SurfaceRules.ifTrue(
                                hole,
                                SurfaceRules.sequence(
                                    SurfaceRules.ifTrue(water0, air), SurfaceRules.ifTrue(SurfaceRules.temperature(), ice), water
                                )
                            )
                        ),
                        midSurface
                    )
                )
            ),
            SurfaceRules.ifTrue(
                waterStartM6,
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(
                        SurfaceRules.ON_FLOOR, SurfaceRules.ifTrue(frozenOcean, SurfaceRules.ifTrue(hole, water))
                    ),
                    SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, highSurface),
                    SurfaceRules.ifTrue(beachBiomes, SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, sandstone)),
                    SurfaceRules.ifTrue(desert, SurfaceRules.ifTrue(SurfaceRules.VERY_DEEP_UNDER_FLOOR, sandstone))
                )
            ),
            SurfaceRules.ifTrue(
                SurfaceRules.ON_FLOOR,
                SurfaceRules.sequence(
                    SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS), stone),
                    SurfaceRules.ifTrue(SurfaceRules.isBiome(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN), sandSurface),
                    gravelSurface
                )
            )
        );
        ImmutableList.Builder<SurfaceRules.RuleSource> builder = ImmutableList.builder();
        if (bedrockRoof) {
            builder.add(
                SurfaceRules.ifTrue(SurfaceRules.not(SurfaceRules.verticalGradient("bedrock_roof", VerticalAnchor.belowTop(5), VerticalAnchor.top())), bedrock)
            );
        }
        if (bedrockFloor) {
            builder.add(SurfaceRules.ifTrue(SurfaceRules.verticalGradient("bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)), bedrock));
        }
        SurfaceRules.RuleSource aboveSurface = SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), body);
        builder.add(aboveGround ? aboveSurface : body);
        builder.add(SurfaceRules.ifTrue(SurfaceRules.verticalGradient("deepslate", VerticalAnchor.absolute(0), VerticalAnchor.absolute(8)), deepslate));
        return SurfaceRules.sequence(builder.build().toArray(SurfaceRules.RuleSource[]::new));
    }
}
