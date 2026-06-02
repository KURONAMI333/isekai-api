package com.kuronami.isekaiapi.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import org.jetbrains.annotations.ApiStatus;

/**
 * An analytic ellipsoid leaf crown: a sphere of horizontal radius {@code radius} and vertical
 * half-extent {@code height}, centered {@code offset} above the trunk's foliage attachment.
 * Edge blocks are randomly dropped by {@code jitter} so the crown reads organic rather than a
 * perfect ball (the "perfect sphere looks artificial" lesson). Setting {@code height} small
 * relative to {@code radius} gives a flat disc; large gives a tall round canopy.
 *
 * <p>Neutral primitive — the leaf block is the {@code foliage_provider}. JSON fields: shared
 * {@code radius}/{@code offset} (IntProviders) plus {@code height} (int 0-16) and {@code jitter}
 * (float 0-1, default 0.2). Exposed as {@code isekai_api:sphere}.
 */
@ApiStatus.Internal
public class SphereFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<SphereFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> foliagePlacerParts(inst)
                    .and(inst.group(
                            Codec.intRange(0, 16).fieldOf("height").forGetter(p -> p.height),
                            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("jitter", 0.2F)
                                    .forGetter(p -> p.jitter)))
                    .apply(inst, SphereFoliagePlacer::new));

    private final int height;
    private final float jitter;

    public SphereFoliagePlacer(IntProvider radius, IntProvider offset, int height, float jitter) {
        super(radius, offset);
        this.height = height;
        this.jitter = jitter;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return IsekaiTreePlacers.SPHERE.get();
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
        BlockPos center = attachment.pos().above(offset);
        int r = foliageRadius + attachment.radiusOffset();
        int h = Math.max(1, this.height);
        double rx = r + 0.5;
        double ry = h + 0.5;
        for (int dy = -h; dy <= h; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double nx = dx / rx;
                    double ny = dy / ry;
                    double nz = dz / rx;
                    double dist = nx * nx + ny * ny + nz * nz;
                    if (dist > 1.0) {
                        continue;
                    }
                    if (dist > 0.55 && random.nextFloat() < this.jitter) {
                        continue;
                    }
                    tryPlaceLeaf(level, setter, random, config, center.offset(dx, dy, dz));
                }
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.height;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ,
                                         int range, boolean large) {
        // Unused: this placer fills its own ellipsoid volume directly.
        return false;
    }
}
