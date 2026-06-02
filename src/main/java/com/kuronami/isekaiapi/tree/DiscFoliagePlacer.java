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
 * A flat wide horizontal disc of leaves at the attachment's level, of horizontal radius
 * {@code disc_radius} and vertical thickness {@code thickness}. Edge-jitter via {@code jitter}
 * drops fringe blocks. Use one disc with a {@code branching} trunk per branch tip to express
 * umbrella/savanna canopies, or with a single-attachment trunk for a flat parasol crown.
 *
 * <p>Neutral primitive — the leaf block is the {@code foliage_provider}. JSON fields: shared
 * {@code radius}/{@code offset} (IntProviders; {@code radius} can be 0) plus
 * {@code disc_radius} (int 1-8), {@code thickness} (int 1-4, default 1) and {@code jitter}
 * (float 0-1, default 0.2). Exposed as {@code isekai_api:disc}.
 */
@ApiStatus.Internal
public class DiscFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<DiscFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> foliagePlacerParts(inst)
                    .and(inst.group(
                            Codec.intRange(1, 8).fieldOf("disc_radius").forGetter(p -> p.discRadius),
                            Codec.intRange(1, 4).optionalFieldOf("thickness", 1).forGetter(p -> p.thickness),
                            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("jitter", 0.2F)
                                    .forGetter(p -> p.jitter)))
                    .apply(inst, DiscFoliagePlacer::new));

    private final int discRadius;
    private final int thickness;
    private final float jitter;

    public DiscFoliagePlacer(IntProvider radius, IntProvider offset,
                             int discRadius, int thickness, float jitter) {
        super(radius, offset);
        this.discRadius = discRadius;
        this.thickness = thickness;
        this.jitter = jitter;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return IsekaiTreePlacers.DISC.get();
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
        BlockPos base = attachment.pos().above(offset);
        int r = this.discRadius;
        double edge = (r + 0.5) * (r + 0.5);
        double inner = (r - 0.5) * (r - 0.5);
        for (int dy = 0; dy < this.thickness; dy++) {
            BlockPos layer = base.above(dy);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d2 = dx * dx + dz * dz;
                    if (d2 > edge) continue;
                    if (d2 > inner && random.nextFloat() < this.jitter) continue;
                    BlockPos pos = layer.offset(dx, 0, dz);
                    if (dx == 0 && dz == 0) {
                        tryPlaceLeaf(level, setter, random, config, pos);
                    } else {
                        LeafPlacing.placePinned(level, setter, random, config, pos);
                    }
                }
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.thickness;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ,
                                         int range, boolean large) {
        return false;
    }
}
