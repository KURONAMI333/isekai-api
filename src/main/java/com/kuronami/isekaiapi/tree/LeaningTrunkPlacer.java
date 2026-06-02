package com.kuronami.isekaiapi.tree;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import org.jetbrains.annotations.ApiStatus;

/**
 * A trunk that rises straight, then leans to one side and continues as a near-horizontal
 * stalk of {@code lean_length} logs — the classic palm / wind-bent silhouette. Each log of the
 * leaning stalk (and the upper trunk, above {@code min_height_for_leaves}) becomes a foliage
 * attachment, so the crown drapes along the bent top rather than sitting in a single ball.
 *
 * <p>Neutral primitive: the wood block is the {@code trunk_provider}; this class only decides
 * geometry. JSON fields: the shared {@code base_height}/{@code height_rand_a}/{@code
 * height_rand_b} (plain ints) plus {@code min_height_for_leaves} (int, default 1) and
 * {@code lean_length} (IntProvider 1-16). Mirrors vanilla's bending trunk but exposed under
 * {@code isekai_api:leaning} so consumers compose from Isekai's own primitives.
 */
@ApiStatus.Internal
public class LeaningTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<LeaningTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> trunkPlacerParts(inst)
                    .and(inst.group(
                            ExtraCodecs.POSITIVE_INT.optionalFieldOf("min_height_for_leaves", 1)
                                    .forGetter(p -> p.minHeightForLeaves),
                            IntProvider.codec(1, 16).fieldOf("lean_length")
                                    .forGetter(p -> p.leanLength),
                            Codec.BOOL.optionalFieldOf("convert_ground", Boolean.TRUE)
                                    .forGetter(p -> p.convertGround),
                            Codec.BOOL.optionalFieldOf("tip_crown_only", Boolean.FALSE)
                                    .forGetter(p -> p.tipCrownOnly)))
                    .apply(inst, LeaningTrunkPlacer::new));

    private final int minHeightForLeaves;
    private final IntProvider leanLength;
    /**
     * When {@code false}, the trunk does NOT convert the block beneath it to dirt — the tree
     * roots on (and keeps) whatever ground it grows from, sand included. This overrides
     * vanilla's "trees sit on dirt" assumption so beach palms stay sandy. Defaults to
     * {@code true} (vanilla-like dirt conversion).
     */
    private final boolean convertGround;
    /**
     * When {@code true}, only the single attachment at the bent tip is returned, so a fan/tuft
     * crown sprays from one point (a real palm). When {@code false} (default), every upper-trunk
     * and lean log becomes an attachment so a draping crown follows the bend.
     */
    private final boolean tipCrownOnly;

    public LeaningTrunkPlacer(int baseHeight, int heightRandA, int heightRandB,
                              int minHeightForLeaves, IntProvider leanLength, boolean convertGround,
                              boolean tipCrownOnly) {
        super(baseHeight, heightRandA, heightRandB);
        this.minHeightForLeaves = minHeightForLeaves;
        this.leanLength = leanLength;
        this.convertGround = convertGround;
        this.tipCrownOnly = tipCrownOnly;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return IsekaiTreePlacers.LEANING.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
            LevelSimulatedReader level,
            BiConsumer<BlockPos, BlockState> blockSetter,
            RandomSource random,
            int freeTreeHeight,
            BlockPos pos,
            TreeConfiguration config) {
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int top = freeTreeHeight - 1;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        if (this.convertGround) {
            setDirtAt(level, blockSetter, random, cursor.below(), config);
        }
        List<FoliagePlacer.FoliageAttachment> attachments = Lists.newArrayList();

        // vertical trunk that begins to lean near the top
        for (int y = 0; y <= top; y++) {
            if (y + 1 >= top + random.nextInt(2)) {
                cursor.move(lean);
            }
            if (TreeFeature.validTreePos(level, cursor)) {
                this.placeLog(level, blockSetter, random, cursor, config);
            }
            if (y >= this.minHeightForLeaves) {
                attachments.add(new FoliagePlacer.FoliageAttachment(cursor.immutable(), 0, false));
            }
            cursor.move(Direction.UP);
        }

        // near-horizontal leaning stalk; crown drapes along it
        int leanLen = this.leanLength.sample(random);
        FoliagePlacer.FoliageAttachment tip = null;
        for (int i = 0; i <= leanLen; i++) {
            if (TreeFeature.validTreePos(level, cursor)) {
                this.placeLog(level, blockSetter, random, cursor, config);
            }
            tip = new FoliagePlacer.FoliageAttachment(cursor.immutable(), 0, false);
            attachments.add(tip);
            cursor.move(lean);
        }

        // palm mode: one crown sprays from the bent tip rather than draping up the whole trunk
        if (this.tipCrownOnly && tip != null) {
            return Lists.newArrayList(tip);
        }
        return attachments;
    }
}
