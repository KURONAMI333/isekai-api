package com.kuronami.isekaiapi.tree;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A trunk that grows straight up, then emits N branches near its top. Each branch extends
 * {@code branch_length} blocks outward in a horizontal direction (chosen from the four cardinal
 * directions), placing log blocks along the way and yielding one {@link FoliagePlacer.FoliageAttachment}
 * at its tip. The main trunk also contributes one attachment at its very top, so a configuration
 * with {@code branch_count=4} produces five distinct crown sites — the geometry behind forked,
 * umbrella, and many wide-canopy silhouettes.
 *
 * <p>Neutral primitive: the wood block is the {@code trunk_provider} (this placer only decides
 * geometry); the consumer pairs it with any foliage placer (e.g. {@code isekai_api:sphere} per
 * tip for a broad-canopy look, {@code isekai_api:disc} for an umbrella). JSON fields: shared
 * {@code base_height}/{@code height_rand_a}/{@code height_rand_b} (plain ints) plus
 * {@code branch_count} (int 1-6), {@code branch_length} (IntProvider 1-8) and
 * {@code branch_start_offset_from_top} (IntProvider 0-4, default 0; how far below the trunk's
 * top the branches sprout — 0 keeps them at the very top, larger spreads them down the upper
 * trunk). Exposed as {@code isekai_api:branching}.
 */
@ApiStatus.Internal
public class BranchingTrunkPlacer extends TrunkPlacer {

    public static final MapCodec<BranchingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
            inst -> trunkPlacerParts(inst)
                    .and(inst.group(
                            Codec.intRange(1, 6).fieldOf("branch_count").forGetter(p -> p.branchCount),
                            IntProvider.codec(1, 8).fieldOf("branch_length").forGetter(p -> p.branchLength),
                            IntProvider.codec(0, 4)
                                    .optionalFieldOf("branch_start_offset_from_top", ConstantInt.of(0))
                                    .forGetter(p -> p.branchStartOffsetFromTop)))
                    .apply(inst, BranchingTrunkPlacer::new));

    private final int branchCount;
    private final IntProvider branchLength;
    private final IntProvider branchStartOffsetFromTop;

    public BranchingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB,
                                int branchCount, IntProvider branchLength,
                                IntProvider branchStartOffsetFromTop) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchCount = branchCount;
        this.branchLength = branchLength;
        this.branchStartOffsetFromTop = branchStartOffsetFromTop;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return IsekaiTreePlacers.BRANCHING.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
            LevelSimulatedReader level,
            BiConsumer<BlockPos, BlockState> blockSetter,
            RandomSource random,
            int freeTreeHeight,
            BlockPos pos,
            TreeConfiguration config) {
        setDirtAt(level, blockSetter, random, pos.below(), config);
        List<FoliagePlacer.FoliageAttachment> attachments = Lists.newArrayList();

        // straight vertical trunk
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int y = 0; y < freeTreeHeight; y++) {
            if (TreeFeature.validTreePos(level, cursor)) {
                this.placeLog(level, blockSetter, random, cursor, config);
            }
            cursor.move(Direction.UP);
        }
        BlockPos top = pos.above(freeTreeHeight - 1);
        // top of the trunk is one crown site
        attachments.add(new FoliagePlacer.FoliageAttachment(top.above(), 0, false));

        // branches sprout from the upper trunk, each ending in its own crown site
        int startOffset = this.branchStartOffsetFromTop.sample(random);
        BlockPos branchOrigin = top.below(Math.min(startOffset, freeTreeHeight - 1));
        Direction[] cardinals = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
        for (int i = 0; i < this.branchCount; i++) {
            Direction dir = cardinals[(i + random.nextInt(cardinals.length)) % cardinals.length];
            int len = this.branchLength.sample(random);
            BlockPos.MutableBlockPos branchCursor = branchOrigin.mutable();
            BlockPos branchTip = branchOrigin;
            for (int step = 1; step <= len; step++) {
                branchCursor.move(dir);
                // every other step the branch rises one block, giving an upward-arcing branch
                if ((step & 1) == 0) {
                    branchCursor.move(Direction.UP);
                }
                if (TreeFeature.validTreePos(level, branchCursor)) {
                    this.placeLog(level, blockSetter, random, branchCursor, config);
                }
                branchTip = branchCursor.immutable();
            }
            attachments.add(new FoliagePlacer.FoliageAttachment(branchTip.above(), 0, false));
        }
        return attachments;
    }
}
