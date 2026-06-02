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
 * A conical crown: at each Y layer the leaf disc has a radius that tapers from
 * {@code base_radius} at the bottom to 0 at the top. The taper shape is selected by
 * {@code taper}: {@code "linear"} (straight cone) or {@code "concave"} (radius shrinks
 * faster near the top — the conifer/cypress silhouette, modelling
 * {@code r = base * (1 - t)^2}). Edge-jitter via {@code jitter} drops fringe blocks for an
 * organic outline.
 *
 * <p>Neutral primitive — the leaf block is the {@code foliage_provider}. JSON fields: shared
 * {@code radius}/{@code offset} (IntProviders; {@code radius} can be 0 — geometry is driven
 * by the fields below) plus {@code base_radius} (int 1-8), {@code height} (int 1-16),
 * {@code taper} (enum: linear|concave, default linear) and {@code jitter} (float 0-1,
 * default 0.15). Exposed as {@code isekai_api:cone}.
 */
@ApiStatus.Internal
public class ConeFoliagePlacer extends FoliagePlacer {

    public enum Taper {
        LINEAR, CONCAVE;
        public static final Codec<Taper> CODEC = Codec.STRING.xmap(
                s -> "concave".equalsIgnoreCase(s) ? CONCAVE : LINEAR,
                t -> t == CONCAVE ? "concave" : "linear");
    }

    public static final MapCodec<ConeFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> foliagePlacerParts(inst)
                    .and(inst.group(
                            Codec.intRange(1, 8).fieldOf("base_radius").forGetter(p -> p.baseRadius),
                            Codec.intRange(1, 16).fieldOf("height").forGetter(p -> p.height),
                            Taper.CODEC.optionalFieldOf("taper", Taper.LINEAR).forGetter(p -> p.taper),
                            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("jitter", 0.15F)
                                    .forGetter(p -> p.jitter)))
                    .apply(inst, ConeFoliagePlacer::new));

    private final int baseRadius;
    private final int height;
    private final Taper taper;
    private final float jitter;

    public ConeFoliagePlacer(IntProvider radius, IntProvider offset,
                             int baseRadius, int height, Taper taper, float jitter) {
        super(radius, offset);
        this.baseRadius = baseRadius;
        this.height = height;
        this.taper = taper;
        this.jitter = jitter;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return IsekaiTreePlacers.CONE.get();
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
        int h = this.height;
        int br = this.baseRadius;
        for (int dy = 0; dy < h; dy++) {
            float t = (float) dy / (float) Math.max(1, h - 1);   // 0 at bottom, 1 at top
            float taperFactor = this.taper == Taper.CONCAVE ? (1.0f - t) * (1.0f - t) : (1.0f - t);
            int r = Math.max(0, Math.round(br * taperFactor));
            placeDisc(level, setter, random, config, base.above(dy), r);
        }
    }

    /** A filled disc of radius {@code r} at {@code center}'s Y; edge blocks may drop via jitter. */
    private void placeDisc(LevelSimulatedReader level, FoliagePlacer.FoliageSetter setter,
                           RandomSource random, TreeConfiguration config, BlockPos center, int r) {
        if (r <= 0) {
            // single-leaf peak — anchor via tryPlaceLeaf when it's the very top tip.
            tryPlaceLeaf(level, setter, random, config, center);
            return;
        }
        double edge = (r + 0.5) * (r + 0.5);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > edge) continue;
                if (d2 > (r - 0.5) * (r - 0.5) && random.nextFloat() < this.jitter) continue;
                BlockPos pos = center.offset(dx, 0, dz);
                if (dx == 0 && dz == 0) {
                    tryPlaceLeaf(level, setter, random, config, pos);
                } else {
                    LeafPlacing.placePinned(level, setter, random, config, pos);
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
        return false;
    }
}
