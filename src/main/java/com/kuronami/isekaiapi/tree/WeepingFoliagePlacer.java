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
 * A weeping/draping crown: stacked horizontal discs at and just below the attachment, plus
 * vertical strands of {@code strand_length} leaves hanging straight down from random edge
 * blocks at probability {@code strand_chance}. The geometry behind willow/wisteria/sakura
 * silhouettes — flat-ish top, leaves dripping from the rim.
 *
 * <p>Neutral primitive — the leaf block is the {@code foliage_provider}. JSON fields: shared
 * {@code radius}/{@code offset} (IntProviders; {@code radius} can be 0) plus
 * {@code crown_radius} (int 2-6, default 3), {@code crown_thickness} (int 1-3, default 2),
 * {@code strand_length} (IntProvider 1-6) and {@code strand_chance} (float 0-1, default 0.4).
 * Exposed as {@code isekai_api:weeping}.
 */
@ApiStatus.Internal
public class WeepingFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<WeepingFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> foliagePlacerParts(inst)
                    .and(inst.group(
                            Codec.intRange(2, 6).optionalFieldOf("crown_radius", 3).forGetter(p -> p.crownRadius),
                            Codec.intRange(1, 3).optionalFieldOf("crown_thickness", 2).forGetter(p -> p.crownThickness),
                            IntProvider.codec(1, 6).fieldOf("strand_length").forGetter(p -> p.strandLength),
                            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("strand_chance", 0.4F)
                                    .forGetter(p -> p.strandChance)))
                    .apply(inst, WeepingFoliagePlacer::new));

    private final int crownRadius;
    private final int crownThickness;
    private final IntProvider strandLength;
    private final float strandChance;

    public WeepingFoliagePlacer(IntProvider radius, IntProvider offset,
                                int crownRadius, int crownThickness,
                                IntProvider strandLength, float strandChance) {
        super(radius, offset);
        this.crownRadius = crownRadius;
        this.crownThickness = crownThickness;
        this.strandLength = strandLength;
        this.strandChance = strandChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return IsekaiTreePlacers.WEEPING.get();
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
        int r = this.crownRadius;
        double edge = (r + 0.5) * (r + 0.5);
        double inner = (r - 0.5) * (r - 0.5);

        // crown: stacked discs (the top disc is the densest layer; lower discs slightly trim)
        for (int dy = 0; dy < this.crownThickness; dy++) {
            BlockPos layer = base.below(dy);             // discs descend from the attachment
            int layerR = Math.max(1, r - (dy > 0 ? 1 : 0));
            for (int dx = -layerR; dx <= layerR; dx++) {
                for (int dz = -layerR; dz <= layerR; dz++) {
                    double d2 = dx * dx + dz * dz;
                    double layerEdge = (layerR + 0.5) * (layerR + 0.5);
                    if (d2 > layerEdge) continue;
                    BlockPos pos = layer.offset(dx, 0, dz);
                    if (dx == 0 && dz == 0 && dy == 0) {
                        tryPlaceLeaf(level, setter, random, config, pos);
                    } else {
                        LeafPlacing.placePinned(level, setter, random, config, pos);
                    }
                }
            }
        }

        // strands: from edge cells of the BOTTOM disc, drop a vertical column of leaves
        BlockPos bottomDisc = base.below(this.crownThickness - 1);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 < inner || d2 > edge) continue;          // strands only from the rim ring
                if (random.nextFloat() >= this.strandChance) continue;
                int len = this.strandLength.sample(random);
                for (int h = 1; h <= len; h++) {
                    LeafPlacing.placePinned(level, setter, random, config,
                            bottomDisc.offset(dx, -h, dz));
                }
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.crownThickness;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ,
                                         int range, boolean large) {
        return false;
    }
}
