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
 * height_rand_b} (plain ints) plus {@code min_height_for_leaves} (int, default 1),
 * {@code lean_length} (IntProvider 1-16), {@code slant_curve} (double 1.0-2.0, default 1.0),
 * {@code space_check} (bool, default true) and {@code min_height} (int 1-16, default 4).
 * Mirrors vanilla's bending trunk but exposed under {@code isekai_api:leaning} so consumers
 * compose from Isekai's own primitives.
 *
 * <p>{@code slant_curve} scales the horizontal stride of the stalk per step, giving the
 * exponential bend of a real palm instead of a constant-speed diagonal. A factor of 1.0 is the
 * old one-block-per-step behaviour, so existing datapacks that omit the field are unchanged.
 *
 * <p>{@code space_check} makes the lean stop where the next block is occupied, so the crown stays
 * on the vertical trunk instead of floating in mid-air next to a neighbour. Setting it to
 * {@code false} restores the old behaviour of attaching a crown even where no log could be placed.
 */
@ApiStatus.Internal
public class LeaningTrunkPlacer extends TrunkPlacer {

    /** Default {@code min_height}: shorter trees are skipped entirely. */
    private static final int DEFAULT_MIN_HEIGHT = 4;

    public static final MapCodec<LeaningTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
            // Ten fields exceed what Products can fold from trunkPlacerParts, so the shared
            // base-height parts are spelled out here in the same order and ranges.
            inst -> inst.group(
                            Codec.intRange(0, 32).fieldOf("base_height")
                                    .forGetter(p -> p.baseHeight),
                            Codec.intRange(0, 24).fieldOf("height_rand_a")
                                    .forGetter(p -> p.heightRandA),
                            Codec.intRange(0, 24).fieldOf("height_rand_b")
                                    .forGetter(p -> p.heightRandB),
                            ExtraCodecs.POSITIVE_INT.optionalFieldOf("min_height_for_leaves", 1)
                                    .forGetter(p -> p.minHeightForLeaves),
                            IntProvider.codec(1, 16).fieldOf("lean_length")
                                    .forGetter(p -> p.leanLength),
                            Codec.BOOL.optionalFieldOf("convert_ground", Boolean.TRUE)
                                    .forGetter(p -> p.convertGround),
                            Codec.BOOL.optionalFieldOf("tip_crown_only", Boolean.FALSE)
                                    .forGetter(p -> p.tipCrownOnly),
                            Codec.doubleRange(1.0, 2.0).optionalFieldOf("slant_curve", 1.0)
                                    .forGetter(p -> p.slantCurve),
                            Codec.BOOL.optionalFieldOf("space_check", Boolean.TRUE)
                                    .forGetter(p -> p.spaceCheck),
                            Codec.intRange(1, 16).optionalFieldOf("min_height", DEFAULT_MIN_HEIGHT)
                                    .forGetter(p -> p.minHeight))
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
    /**
     * Per-step multiplier for the horizontal stride of the stalk. 1.0 walks one block sideways per
     * step (the old shape); larger values lengthen each successive step so the bend accelerates.
     */
    private final double slantCurve;
    /**
     * When {@code true} (default), an occupied block stops the lean and the crown stays on the
     * vertical trunk. {@code false} keeps the old behaviour of attaching a crown to a position
     * where no log was placed.
     */
    private final boolean spaceCheck;
    /** Minimum {@code freeTreeHeight} for the tree to generate at all. */
    private final int minHeight;

    public LeaningTrunkPlacer(int baseHeight, int heightRandA, int heightRandB,
                              int minHeightForLeaves, IntProvider leanLength, boolean convertGround,
                              boolean tipCrownOnly, double slantCurve, boolean spaceCheck,
                              int minHeight) {
        super(baseHeight, heightRandA, heightRandB);
        this.minHeightForLeaves = minHeightForLeaves;
        this.leanLength = leanLength;
        this.convertGround = convertGround;
        this.tipCrownOnly = tipCrownOnly;
        this.slantCurve = slantCurve;
        this.spaceCheck = spaceCheck;
        this.minHeight = minHeight;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return IsekaiTreePlacers.LEANING.get();
    }

    /**
     * How many blocks sideways one stalk step moves. {@code slant_curve = 1.0} always returns 1 so
     * the old geometry is reproduced exactly; larger curves grow the stride exponentially.
     */
    private int horizontalStride(int step) {
        if (this.slantCurve <= 1.0) {
            return 1;
        }
        return Math.max(1, (int) Math.round(Math.pow(this.slantCurve, step)));
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
            LevelSimulatedReader level,
            BiConsumer<BlockPos, BlockState> blockSetter,
            RandomSource random,
            int freeTreeHeight,
            BlockPos pos,
            TreeConfiguration config) {
        List<FoliagePlacer.FoliageAttachment> attachments = Lists.newArrayList();
        // min_height is about the trunk itself, independent of where leaves start; checked before
        // the dirt conversion so a skipped tree leaves the ground untouched
        if (freeTreeHeight < this.minHeight) {
            return attachments;
        }

        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int top = freeTreeHeight - 1;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        if (this.convertGround) {
            setDirtAt(level, blockSetter, random, cursor.below(), config);
        }

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
        lean:
        for (int i = 0; i <= leanLen; i++) {
            if (!TreeFeature.validTreePos(level, cursor)) {
                if (this.spaceCheck) {
                    // blocked: end the stalk here, keeping the crown on what was actually grown
                    break;
                }
            } else {
                this.placeLog(level, blockSetter, random, cursor, config);
            }
            tip = new FoliagePlacer.FoliageAttachment(cursor.immutable(), 0, false);
            attachments.add(tip);

            int stride = horizontalStride(i);
            for (int step = 0; step < stride; step++) {
                cursor.move(lean);
                // the intermediate blocks of a long stride still carry logs, so the stalk stays
                // continuous; the final block is logged at the top of the next iteration
                if (step == stride - 1) {
                    continue;
                }
                if (!TreeFeature.validTreePos(level, cursor)) {
                    if (this.spaceCheck) {
                        break lean;
                    }
                } else {
                    this.placeLog(level, blockSetter, random, cursor, config);
                }
            }
        }

        // palm mode: one crown sprays from the bent tip rather than draping up the whole trunk
        if (this.tipCrownOnly) {
            if (tip != null) {
                return Lists.newArrayList(tip);
            }
            // the lean was blocked before its first log: crown the vertical trunk instead of
            // returning a floating tip (or nothing at all)
            if (!attachments.isEmpty()) {
                return Lists.newArrayList(attachments.get(attachments.size() - 1));
            }
        }
        return attachments;
    }
}
