package com.kuronami.isekaiapi.tree;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import org.jetbrains.annotations.ApiStatus;

/**
 * A trunk that rises from the root and drifts sideways while it climbs, so the whole stem is one
 * curve instead of {@code isekai_api:leaning}'s vertical shaft followed by a horizontal arm.
 *
 * <p>Every level of the trunk places its vertical log first, then — with a probability that grows
 * with height — one more log on the lean side of the same level. The result is a staircase that
 * never runs horizontally: at most one block sideways per block up, and near the top the stem is a
 * "one across, one up" diagonal. Horizontal logs take the lean axis ({@code x} or {@code z}) and the
 * vertical ones keep {@code y}, so the bend reads as wood turning rather than as a shaft with
 * detached stubs. Both logs of a bent level share a face, and so do consecutive levels, so the whole
 * trunk is six-connected — no diagonal-only contact as in vanilla's bending trunk.
 *
 * <p>Neutral primitive: the wood block is the {@code trunk_provider}; this class only decides
 * geometry. JSON fields: the shared {@code base_height}/{@code height_rand_a}/{@code height_rand_b}
 * (plain ints) plus {@code bend} (double 0.0-2.0, default 1.0), {@code convert_ground} (bool,
 * default true), {@code space_check} (bool, default true) and {@code min_height} (int 1-16, default
 * 4). Exposed as {@code isekai_api:curved}.
 *
 * <p>{@code bend} is the chance of a sideways step at the very top, so it sets how hard the stem
 * curves: at level {@code y} of {@code top} the chance is {@code bend * (y / top)^2}. A value of
 * 1.0 always steps sideways at the top and almost never near the root; 0.0 is a straight trunk;
 * 2.0 saturates the upper third. {@code convert_ground} and {@code min_height} mean what they mean
 * on {@link LeaningTrunkPlacer} — {@code false} keeps a beach palm's sand, and a tree whose trunk
 * would be shorter than {@code min_height} places nothing at all. {@code space_check} stops the
 * trunk at the last log it could actually place, so the crown stays on wood.
 *
 * <p>The trunk emits a single crown attachment, at its top log: a palm's crown sprays from one
 * point, so this placer is always "tip only" and has no {@code tip_crown_only} switch.
 *
 * <p><b>Horizontal travel is capped at {@value #MAX_HORIZONTAL_TRAVEL} blocks from the origin</b>,
 * the same budget {@code isekai_api:leaning} uses. The feature stage hands a generator a 3x3 chunk
 * region, which always covers 16 blocks in each direction from an origin in the middle chunk, so 12
 * can be walked without a bounds check. The cap also bounds how far a crown can reach past the
 * trunk: an origin plus 12 plus a frond still stays inside the region.
 */
@ApiStatus.Internal
public class CurvedTrunkPlacer extends TrunkPlacer {

    /** Default {@code min_height}: shorter trees are skipped entirely. */
    private static final int DEFAULT_MIN_HEIGHT = 4;

    /**
     * Horizontal blocks one tree may travel from its origin.
     *
     * <p>Same number and same reason as {@link LeaningTrunkPlacer#MAX_HORIZONTAL_TRAVEL}: the
     * feature stage gives a generator a 3x3 chunk region, which always covers 16 blocks in every
     * direction from an origin inside the middle chunk. Kept as one constant so a palm cannot walk
     * past what the region holds, whichever trunk made it.
     */
    public static final int MAX_HORIZONTAL_TRAVEL = LeaningTrunkPlacer.MAX_HORIZONTAL_TRAVEL;

    public static final MapCodec<CurvedTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                            Codec.intRange(0, 32).fieldOf("base_height")
                                    .forGetter(p -> p.baseHeight),
                            Codec.intRange(0, 24).fieldOf("height_rand_a")
                                    .forGetter(p -> p.heightRandA),
                            Codec.intRange(0, 24).fieldOf("height_rand_b")
                                    .forGetter(p -> p.heightRandB),
                            Codec.doubleRange(0.0, 2.0).optionalFieldOf("bend", 1.0)
                                    .forGetter(p -> p.bend),
                            Codec.BOOL.optionalFieldOf("convert_ground", Boolean.TRUE)
                                    .forGetter(p -> p.convertGround),
                            Codec.BOOL.optionalFieldOf("space_check", Boolean.TRUE)
                                    .forGetter(p -> p.spaceCheck),
                            Codec.intRange(1, 16).optionalFieldOf("min_height", DEFAULT_MIN_HEIGHT)
                                    .forGetter(p -> p.minHeight))
                    .apply(inst, CurvedTrunkPlacer::new));

    /**
     * Chance of a sideways step at the top of the trunk. The chance at any level is
     * {@code bend * (level / top)^2}, so a larger value bends earlier and further.
     */
    private final double bend;
    /**
     * When {@code false}, the trunk does NOT convert the block beneath it to dirt — the tree roots
     * on (and keeps) whatever ground it grows from, sand included, so beach palms stay sandy.
     */
    private final boolean convertGround;
    /**
     * When {@code true} (default), the trunk stops at the last log it could place instead of
     * carrying on through an occupied cell, so the single crown site always names real wood.
     */
    private final boolean spaceCheck;
    /** Minimum {@code freeTreeHeight} for the tree to generate at all. */
    private final int minHeight;

    public CurvedTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, double bend,
                             boolean convertGround, boolean spaceCheck, int minHeight) {
        super(baseHeight, heightRandA, heightRandB);
        this.bend = bend;
        this.convertGround = convertGround;
        this.spaceCheck = spaceCheck;
        this.minHeight = minHeight;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return IsekaiTreePlacers.CURVED.get();
    }

    /** Forces the pillar axis of a placed log, leaving a non-pillar trunk block untouched. */
    private static Function<BlockState, BlockState> withAxis(Direction.Axis axis) {
        return state -> state.hasProperty(RotatedPillarBlock.AXIS)
                ? state.setValue(RotatedPillarBlock.AXIS, axis)
                : state;
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
        // min_height is about the trunk itself; checked before the dirt conversion so a skipped
        // tree leaves the ground untouched
        if (freeTreeHeight < this.minHeight) {
            return attachments;
        }

        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int top = freeTreeHeight - 1;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        // Horizontal blocks travelled so far, bounded by MAX_HORIZONTAL_TRAVEL
        int travelled = 0;
        if (this.convertGround) {
            setDirtAt(level, blockSetter, random, cursor.below(), config);
        }

        // The crown site is the highest log that actually exists; a straight or truncated trunk
        // simply ends on its last vertical log.
        BlockPos tip = cursor.immutable();
        for (int y = 0; y <= top; y++) {
            if (this.placeLog(level, blockSetter, random, cursor, config,
                    withAxis(Direction.Axis.Y))) {
                tip = cursor.immutable();
            } else if (this.spaceCheck) {
                break;
            }

            // The sideways chance climbs with height (0 at the root, `bend` at the top), so the stem
            // leans where a palm leans and stays vertical where it is thick.
            double reach = top <= 0 ? 1.0 : (double) y / (double) top;
            double sideways = this.bend * reach * reach;
            if (travelled < MAX_HORIZONTAL_TRAVEL && random.nextFloat() < sideways) {
                cursor.move(lean);
                travelled++;
                if (this.placeLog(level, blockSetter, random, cursor, config,
                        withAxis(lean.getAxis()))) {
                    tip = cursor.immutable();
                } else {
                    // blocked sideways: step back so the rest of the stem stays on placed wood
                    cursor.move(lean.getOpposite());
                    if (this.spaceCheck) {
                        break;
                    }
                }
            }
            cursor.move(Direction.UP);
        }

        attachments.add(new FoliagePlacer.FoliageAttachment(tip, 0, false));
        return attachments;
    }
}
