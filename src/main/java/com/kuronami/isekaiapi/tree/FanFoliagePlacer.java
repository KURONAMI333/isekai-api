package com.kuronami.isekaiapi.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import org.jetbrains.annotations.ApiStatus;

/**
 * A radial frond crown: a compact log-adjacent core at the tip with cardinal arms that reach
 * out {@code crown_radius} blocks and droop {@code hang} blocks past the tip — a fan/frond
 * silhouette. Spread leaves are placed via {@link LeafPlacing#placePinned} so they never decay;
 * the dense core uses vanilla {@code tryPlaceLeaf} since it resolves to a safe distance from
 * log adjacency.
 *
 * <p>Neutral primitive — the leaf block is the {@code foliage_provider}. JSON fields: shared
 * {@code radius}/{@code offset} (IntProviders; geometry is driven by the fields below so
 * {@code radius} can be 0) plus {@code crown_radius} (int 1-4, default 2 = arm reach) and
 * {@code hang} (int 0-3, default 1 = how far the drooping arm tips fall). Exposed as
 * {@code isekai_api:fan}.
 */
@ApiStatus.Internal
public class FanFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<FanFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> foliagePlacerParts(inst)
                    .and(inst.group(
                            Codec.intRange(1, 4).optionalFieldOf("crown_radius", 2)
                                    .forGetter(p -> p.crownRadius),
                            Codec.intRange(0, 3).optionalFieldOf("hang", 1)
                                    .forGetter(p -> p.hang)))
                    .apply(inst, FanFoliagePlacer::new));

    private final int crownRadius;
    private final int hang;

    public FanFoliagePlacer(IntProvider radius, IntProvider offset, int crownRadius, int hang) {
        super(radius, offset);
        this.crownRadius = crownRadius;
        this.hang = hang;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return IsekaiTreePlacers.FAN.get();
    }

    @Override
    protected void createFoliage(
            LevelSimulatedReader level,
            FoliagePlacer.FoliageSetter setter,
            RandomSource random,
            TreeConfiguration config,
            int maxFreeTreeHeight,
            FoliagePlacer.FoliageAttachment attachment,
            int foliageHeight,
            int foliageRadius,
            int offset) {
        BlockPos tip = attachment.pos().above(offset);
        int r = this.crownRadius;

        // --- dense core anchored to the tip log (vanilla tryPlaceLeaf is safe here) ---
        tryPlaceLeaf(level, setter, random, config, tip.above());
        tryPlaceLeaf(level, setter, random, config, tip);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            tryPlaceLeaf(level, setter, random, config, tip.relative(d));
        }

        // --- cardinal arms: reach out at the tip plane and one row up, then droop ---
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int reach = 1; reach <= r; reach++) {
                LeafPlacing.placePinned(level, setter, random, config, tip.relative(d, reach));
            }
            LeafPlacing.placePinned(level, setter, random, config, tip.above().relative(d, r));
            BlockPos tipOut = tip.relative(d, r);
            for (int h = 1; h <= this.hang; h++) {
                LeafPlacing.placePinned(level, setter, random, config, tipOut.below(h));
            }
        }

        // --- diagonal corners at crown level so the head reads round, not a bare cross ---
        for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            LeafPlacing.placePinned(level, setter, random, config, tip.offset(c[0], 0, c[1]));
            LeafPlacing.placePinned(level, setter, random, config, tip.above().offset(c[0], 0, c[1]));
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 2;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ,
                                         int range, boolean large) {
        return false;
    }
}
