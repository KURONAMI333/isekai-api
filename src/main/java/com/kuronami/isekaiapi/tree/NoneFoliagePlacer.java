package com.kuronami.isekaiapi.tree;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import org.jetbrains.annotations.ApiStatus;

/**
 * A foliage placer that places no leaves at all — the missing half of a bare tree.
 *
 * <p>Vanilla's {@code minecraft:tree} requires a {@code foliage_placer} and a
 * {@code foliage_provider}; there is no way to say "trunk only". A dead, burnt or pollarded tree
 * therefore had to be faked with a foliage placer that happens to miss every position, or with a
 * {@code foliage_provider} pointing at air — both of which break as soon as the tree is taller than
 * the fake. This placer simply does nothing, so the trunk placer and the decorators decide the whole
 * silhouette.
 *
 * <p>Datapack type {@code isekai_api:none}. It needs no fields; {@code radius} and {@code offset}
 * exist only because {@link FoliagePlacer} carries them, and both default to {@code 0}, so the
 * minimal JSON is {@code {"type": "isekai_api:none"}}. A {@code foliage_provider} is still required
 * by the tree codec; point it at any leaf block — nothing reads it. Pair this with
 * {@code isekai_api:branch_stubs} and {@code isekai_api:broken_top} for the bare shape.
 *
 * <p>Because no leaf is placed, {@link LeafPlacing} is never involved: there is nothing to decay.
 */
@ApiStatus.Internal
public final class NoneFoliagePlacer extends FoliagePlacer {

    public static final MapCodec<NoneFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IntProvider.codec(0, 16).optionalFieldOf("radius", ConstantInt.of(0))
                    .forGetter(placer -> placer.radius),
            IntProvider.codec(0, 16).optionalFieldOf("offset", ConstantInt.of(0))
                    .forGetter(placer -> placer.offset)
    ).apply(instance, NoneFoliagePlacer::new));

    public NoneFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return IsekaiTreePlacers.NONE.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter blockSetter,
                                 RandomSource random, TreeConfiguration config, int maxFreeTreeHeight,
                                 FoliagePlacer.FoliageAttachment attachment, int foliageHeight,
                                 int foliageRadius, int offset) {
        // Deliberately empty: a bare tree is the point.
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 0;
    }

    @Override
    public int foliageRadius(RandomSource random, int radius) {
        return 0;
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ,
                                         int range, boolean large) {
        return true;
    }

    /** The offset overload is the one {@code TreeFeature} calls; it stays empty too. */
    @Override
    public void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter blockSetter,
                              RandomSource random, TreeConfiguration config, int maxFreeTreeHeight,
                              FoliagePlacer.FoliageAttachment attachment, int foliageHeight,
                              int foliageRadius) {
        // No leaves, no offset sampling.
    }
}
