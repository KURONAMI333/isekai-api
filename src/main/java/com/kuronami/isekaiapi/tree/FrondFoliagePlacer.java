package com.kuronami.isekaiapi.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import org.jetbrains.annotations.ApiStatus;

/**
 * A palm head made of eight fronds instead of a core with four arms.
 *
 * <p>Each of the eight horizontal directions (the four cardinals and the four diagonals) grows one
 * frond: it climbs {@code rise} blocks while it reaches out, extends {@code length} blocks, and lets
 * its last {@code droop} blocks fall. A diagonal frond advances one axis at a time, alternating, so
 * every leaf it writes shares a face with the previous one — the frond is a staircase, not a
 * diagonal-only line with visible gaps. Lengths and droops are sampled per frond, so the eight
 * directions are not the same size and the crown does not read as a cross from above.
 *
 * <p>Leaves are placed with {@link LeafPlacing#placePinned}, which keeps them through generation;
 * the ticker then resolves the real distance from the trunk, and a leaf further than six leaf steps
 * from a log decays. A frond therefore never reaches more than
 * {@value #MAX_FROND_REACH} cells from the crown attachment: {@code offset + rise + length + droop}
 * is trimmed at the tip when the JSON asks for more. Trimmed is deliberate — a longer frond would
 * place leaves that the first random tick removes, leaving a hole in the crown.
 *
 * <p>Leaf distance counts steps between blocks that share a <em>face</em>, so the crown has to touch
 * the trunk: a crown lifted one block too high hangs on a diagonal and every leaf in it is past the
 * sixth step. {@code offset} therefore keeps its shared meaning of "how far the crown sits above the
 * trunk top", and the cells between the top log and that height are filled with leaves, so the
 * fronds always start from wood contact. The lift is clamped to {@value #MAX_CROWN_LIFT} blocks
 * because those cells are a leaf column: a crown lifted higher would read as a pole, and the lift
 * comes out of the same six-step budget as the fronds.
 *
 * <p>Neutral primitive — the leaf block is the {@code foliage_provider}. JSON fields: the shared
 * {@code radius}/{@code offset} (IntProviders; {@code radius} may be 0 because the fronds drive the
 * geometry) plus {@code length} (IntProvider 1-5, required), {@code droop} (IntProvider 0-3, default
 * 0) and {@code rise} (int 0-2, default 1). Exposed as {@code isekai_api:frond}.
 */
@ApiStatus.Internal
public class FrondFoliagePlacer extends FoliagePlacer {

    /**
     * Longest leaf chain a frond may write: a leaf seven or more leaf steps from a log is at
     * {@code LeavesBlock.DECAY_DISTANCE} and is removed by its first random tick.
     */
    public static final int MAX_FROND_REACH = 6;

    /**
     * Most blocks {@code offset} may lift the crown above the trunk top.
     *
     * <p>The lift is filled with leaves so the crown keeps face contact with the trunk. Beyond two
     * blocks that column stops reading as the top of a palm, and every lifted block is one step the
     * fronds can no longer reach.
     */
    public static final int MAX_CROWN_LIFT = 2;

    /**
     * The eight horizontal directions, written as (x, z) unit steps rather than {@code Direction}
     * so a diagonal can advance one axis at a time and stay face-connected.
     */
    private static final int[][] DIRECTIONS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };

    public static final MapCodec<FrondFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> foliagePlacerParts(inst)
                    .and(inst.group(
                            IntProvider.codec(1, 5).fieldOf("length")
                                    .forGetter(p -> p.length),
                            IntProvider.codec(0, 3).optionalFieldOf("droop", ConstantInt.of(0))
                                    .forGetter(p -> p.droop),
                            Codec.intRange(0, 2).optionalFieldOf("rise", 1)
                                    .forGetter(p -> p.rise)))
                    .apply(inst, FrondFoliagePlacer::new));

    /** How far each frond reaches out; sampled per frond so the eight arms differ. */
    private final IntProvider length;
    /** How many blocks a frond's tip falls; sampled per frond. */
    private final IntProvider droop;
    /** How many of the outward steps also climb, which arches the frond before it flattens. */
    private final int rise;

    public FrondFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider length,
                              IntProvider droop, int rise) {
        super(radius, offset);
        this.length = length;
        this.droop = droop;
        this.rise = rise;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return IsekaiTreePlacers.FROND.get();
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
        BlockPos trunkTop = attachment.pos();
        int lift = Math.min(offset, MAX_CROWN_LIFT);
        // Fill the column between the top log and the crown so the fronds start from leaf-to-log
        // contact. Without it a lifted crown is diagonal-only to the trunk and the first tick that
        // reaches it turns the whole crown to distance 7.
        for (int i = 1; i <= lift; i++) {
            LeafPlacing.placePinned(level, setter, random, config, trunkTop.above(i));
        }
        BlockPos tip = trunkTop.above(lift);

        for (int[] direction : DIRECTIONS) {
            int reach = this.length.sample(random);
            int fall = this.droop.sample(random);
            // offset + rise + reach + fall is the frond's cell count, and its last cell is exactly
            // that many leaf steps from the trunk. Trim the tip (droop first, then reach) past the
            // reachable six so no leaf is placed only to decay.
            int budget = Math.max(1, MAX_FROND_REACH - lift - this.rise);
            reach = Math.min(reach, budget);
            fall = Math.min(fall, Math.max(0, budget - reach));
            placeFrond(level, setter, random, config, tip, direction[0], direction[1], reach, fall);
        }
    }

    /**
     * Writes one frond from {@code tip}.
     *
     * <p>Every step moves exactly one axis by one block, so each leaf touches the previous one on a
     * face. A diagonal alternates its two axes and picks which comes first at random, so the
     * staircases of the four diagonals do not all hug the same side of the line.
     */
    private void placeFrond(LevelSimulatedReader level, FoliagePlacer.FoliageSetter setter,
                            RandomSource random, TreeConfiguration config, BlockPos tip,
                            int x, int z, int reach, int fall) {
        BlockPos.MutableBlockPos cursor = tip.mutable();
        boolean diagonal = x != 0 && z != 0;
        boolean xNext = random.nextBoolean();

        for (int step = 0; step < reach; step++) {
            if (step < this.rise) {
                cursor.move(0, 1, 0);
                LeafPlacing.placePinned(level, setter, random, config, cursor);
            }
            if (diagonal) {
                if (xNext) {
                    cursor.move(x, 0, 0);
                } else {
                    cursor.move(0, 0, z);
                }
                xNext = !xNext;
            } else if (x != 0) {
                cursor.move(x, 0, 0);
            } else {
                cursor.move(0, 0, z);
            }
            LeafPlacing.placePinned(level, setter, random, config, cursor);
        }

        for (int step = 0; step < fall; step++) {
            cursor.move(0, -1, 0);
            LeafPlacing.placePinned(level, setter, random, config, cursor);
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
