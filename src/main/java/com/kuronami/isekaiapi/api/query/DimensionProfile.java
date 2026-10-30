package com.kuronami.isekaiapi.api.query;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.Fluid;

import java.util.Optional;

/**
 * The worldgen-relevant shape of one dimension, read from its {@code LevelStem} and
 * {@code DimensionType} at scan time.
 *
 * <p>Before 3.0.0 the remap path assumed the overworld envelope
 * (Y&nbsp;{@code -64..319}) for every dimension. The nether is
 * {@code 0..255} with a ceiling, no skylight and a lava sea; the end is
 * {@code 0..255} without a ceiling. Building the remap source envelope from this profile
 * makes a worldshape's {@code playable_range} mean the same thing in every dimension.
 *
 * <p>Instances are immutable and reflect the datapack that was loaded when the snapshot was
 * scanned. {@link #seaLevel()}, {@link #defaultBlock()} and {@link #defaultFluid()} are
 * empty/undefined when the dimension does not use a {@code NoiseBasedChunkGenerator}
 * (superflat, custom generators).
 *
 * @param dimension            the level this profile describes
 * @param minY                 inclusive bottom build height
 * @param height               total build height in blocks
 * @param logicalHeight        the height the game treats as the playable column (portal/chorus rules)
 * @param hasCeiling           {@code true} for the nether: skylight is blocked and rain never falls
 * @param hasSkylight          {@code true} when the dimension has a sky
 * @param ultrawarm            {@code true} for the nether: water evaporates, lava flows faster
 * @param natural              {@code true} when compasses/clocks work and beds are usable per dimension type
 * @param coordinateScale      portal coordinate ratio (nether = 8)
 * @param ambientLight         base light level before block light
 * @param bedWorks             {@code true} when beds can be slept in
 * @param respawnAnchorWorks   {@code true} when respawn anchors can be charged
 * @param piglinSafe           {@code true} when piglins do not zombify
 * @param hasRaids             {@code true} when raids can start
 * @param seaLevel             the generator's sea level, or {@link Integer#MIN_VALUE} when unknown
 * @param dimensionType        the {@code dimension_type} this level stem uses
 * @param defaultBlock         the generator's default fill block, if it has one
 * @param defaultFluid         the generator's default fluid, if it has one
 * @since 3.0.0
 */
public record DimensionProfile(
        ResourceKey<Level> dimension,
        ResourceKey<DimensionType> dimensionType,
        int minY,
        int height,
        int logicalHeight,
        boolean hasCeiling,
        boolean hasSkylight,
        boolean ultrawarm,
        boolean natural,
        double coordinateScale,
        float ambientLight,
        boolean bedWorks,
        boolean respawnAnchorWorks,
        boolean piglinSafe,
        boolean hasRaids,
        int seaLevel,
        Optional<Block> defaultBlock,
        Optional<Fluid> defaultFluid) {

    /** {@link #seaLevel()} sentinel for generators that do not expose one. */
    public static final int UNKNOWN_SEA_LEVEL = Integer.MIN_VALUE;

    /** Inclusive top build height. */
    public int maxY() {
        return minY + height - 1;
    }

    /** The dimension's build envelope as a {@link VerticalRange}. */
    public VerticalRange buildRange() {
        return new VerticalRange(minY, maxY(), HeightDistribution.UNIFORM);
    }

    /** {@code true} when the dimension shares the vanilla overworld's build envelope. */
    public boolean hasOverworldBounds() {
        return minY == -64 && height == 384;
    }
}
