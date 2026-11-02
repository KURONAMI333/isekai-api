package com.kuronami.isekaiapi.tree.decorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Grows short bare branches out of the side of the trunk — the leafless limbs of a dead, burnt or
 * pollarded tree.
 *
 * <p>The existing {@code isekai_api:branching} trunk placer also makes branches, but it makes them
 * part of the crown: every branch tip becomes a leaf site. A tree with no leaves needs branches and
 * no leaf sites, which is a decorator, not a placer.
 *
 * <p>Datapack type {@code isekai_api:branch_stubs}. Fields:
 * <ul>
 *   <li>{@code count} (required, IntProvider 0..16) — how many stubs the tree tries to grow. A stub
 *       that finds no free space is dropped, so the visible count can be lower.</li>
 *   <li>{@code length} (required, IntProvider 1..8) — blocks per stub, sampled per stub.</li>
 *   <li>{@code start_fraction} (optional, 0..1, default 0.4) — stubs sprout only from logs at or
 *       above this fraction of the trunk's height, measured from the lowest log. {@code 0} uses the
 *       whole trunk; {@code 0.7} keeps the limbs in the upper third, which is what a tall bare snag
 *       looks like.</li>
 *   <li>{@code upward_chance} (optional, 0..1, default 0.5) — per stub, whether it climbs. A
 *       climbing stub alternates "one block out" and "one block up", so a length-4 stub ends two
 *       blocks above and two blocks out; a flat stub stays at its origin's height.</li>
 *   <li>{@code directions} (optional, default the four horizontals) — which sides may sprout.</li>
 *   <li>{@code block_provider} (optional) — the stub block. Omit it to reuse the block the tree's
 *       own lowest log was placed with, which is what "the same wood as the trunk" means for the
 *       common case.</li>
 * </ul>
 *
 * <p>Only positions that {@link TreeFeature#validTreePos} accepts are used, so a stub stops at the
 * first block that is neither air nor replaceable by a tree and never overwrites the trunk or
 * another stub. Each stub block's {@code axis} follows the direction it was stepped in, so a flat
 * stub is a run of horizontal logs and a climbing one alternates horizontal and vertical logs like a
 * real bent limb.
 *
 * <p>Not a leaf decorator: nothing here goes through {@code LeafPlacing}, because nothing here is a
 * leaf.
 */
@ApiStatus.Internal
public final class BranchStubsDecorator extends TreeDecorator {

    private static final List<Direction> DEFAULT_DIRECTIONS =
            List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    public static final MapCodec<BranchStubsDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IntProvider.codec(0, 16).fieldOf("count").forGetter(decorator -> decorator.count),
            IntProvider.codec(1, 8).fieldOf("length").forGetter(decorator -> decorator.length),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("start_fraction", 0.4F)
                    .forGetter(decorator -> decorator.startFraction),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("upward_chance", 0.5F)
                    .forGetter(decorator -> decorator.upwardChance),
            ExtraCodecs.nonEmptyList(Direction.CODEC.listOf())
                    .optionalFieldOf("directions", DEFAULT_DIRECTIONS)
                    .forGetter(decorator -> decorator.directions),
            BlockStateProvider.CODEC.optionalFieldOf("block_provider")
                    .forGetter(decorator -> decorator.blockProvider)
    ).apply(instance, BranchStubsDecorator::new));

    private final IntProvider count;
    private final IntProvider length;
    private final float startFraction;
    private final float upwardChance;
    private final List<Direction> directions;
    private final Optional<BlockStateProvider> blockProvider;

    private BranchStubsDecorator(IntProvider count, IntProvider length, float startFraction,
                                 float upwardChance, List<Direction> directions,
                                 Optional<BlockStateProvider> blockProvider) {
        this.count = count;
        this.length = length;
        this.startFraction = startFraction;
        this.upwardChance = upwardChance;
        this.directions = directions;
        this.blockProvider = blockProvider;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return IsekaiTreeDecorators.BRANCH_STUBS.get();
    }

    @Override
    public void place(TreeDecorator.Context context) {
        if (context.logs().isEmpty()) {
            return;
        }
        RandomSource random = context.random();
        LevelSimulatedReader level = context.level();

        // The trunk's lowest log is the base; stubs only sprout from the requested upper part.
        List<BlockPos> logs = DecoratorPlacing.logs(context);
        if (logs.isEmpty()) {
            return;
        }
        int baseY = logs.get(0).getY();
        int topY = logs.get(logs.size() - 1).getY();
        int lowestY = baseY + (int) Math.floor((topY - baseY) * this.startFraction);

        // Fallback block: whatever the tree's own lowest log actually is, read back from the level.
        BlockState inherited = this.blockProvider.isPresent()
                ? null
                : DecoratorPlacing.stateAt(level, logs.get(0));

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos log : logs) {
            if (log.getY() >= lowestY) {
                candidates.add(log);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Util.shuffle(candidates, random);

        int wanted = this.count.sample(random);
        if (wanted <= 0) {
            return;
        }

        Set<BlockPos> used = new HashSet<>();
        List<Direction> sideOrder = new ArrayList<>(this.directions);
        int grown = 0;
        for (BlockPos origin : candidates) {
            if (grown >= wanted) {
                break;
            }
            Util.shuffle(sideOrder, random);
            for (Direction direction : sideOrder) {
                if (grown >= wanted) {
                    break;
                }
                if (growStub(context, level, random, origin, direction, inherited, used)) {
                    grown++;
                }
            }
        }
    }

    /**
     * Extends one stub from {@code origin} and returns whether at least one block was placed.
     *
     * <p>Positions are reserved in {@code used} before they are written so a later stub cannot cross
     * an earlier one, and the loop stops at the first position the tree may not occupy.
     */
    private boolean growStub(TreeDecorator.Context context, LevelSimulatedReader level,
                             RandomSource random, BlockPos origin, Direction direction,
                             BlockState inherited, Set<BlockPos> used) {
        boolean climbing = random.nextFloat() < this.upwardChance;
        int length = this.length.sample(random);
        BlockPos.MutableBlockPos cursor = origin.mutable();
        int placed = 0;
        for (int step = 1; step <= length; step++) {
            Direction move = climbing && (step % 2 == 0) ? Direction.UP : direction;
            cursor.move(move);
            BlockPos pos = cursor.immutable();
            if (used.contains(pos) || !TreeFeature.validTreePos(level, pos)) {
                break;
            }
            BlockState state = this.blockProvider.isPresent()
                    ? this.blockProvider.get().getState(random, pos)
                    : inherited;
            if (state == null) {
                break;
            }
            DecoratorPlacing.place(context, pos, axisFor(state, move));
            used.add(pos);
            placed++;
        }
        return placed > 0;
    }

    /**
     * Sets {@code axis} to the axis the stub stepped along, when the block has that property.
     *
     * <p>An inherited trunk log carries {@code axis: y}; a flat stub is made of horizontal logs, so
     * leaving the trunk's own axis in place would render every branch as upright bark.
     */
    private static BlockState axisFor(BlockState state, Direction move) {
        if (!state.hasProperty(BlockStateProperties.AXIS)) {
            return state;
        }
        return state.setValue(BlockStateProperties.AXIS, move.getAxis());
    }
}
