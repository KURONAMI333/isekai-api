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
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Hangs a strand of blocks from the underside of leaves — hanging vines, moss, icicles, lichen.
 *
 * <p>Datapack type {@code isekai_api:hang_from_leaves}. Fields:
 * <ul>
 *   <li>{@code block_provider} (required) — the strand body.</li>
 *   <li>{@code tip_provider} (optional) — the last block of each strand, e.g. a fruit or a
 *       glow-tipped variant. Without it the body block is used all the way down.</li>
 *   <li>{@code length} (required, 1..16) — strand length in blocks, sampled per strand. A strand
 *       stops early when it hits a non-air block, and the tip provider then goes on the last block
 *       that fit.</li>
 *   <li>{@code chance} (optional, 0..1, default 1) — per candidate leaf.</li>
 *   <li>{@code only_on_rim} (optional, default false) — true restricts strands to leaves with at
 *       least one non-leaf horizontal neighbour, which keeps the inside of the crown bare and puts
 *       the strands on the silhouette.</li>
 * </ul>
 *
 * <p>A leaf counts as a candidate only when the block directly below it is air. Strands are placed
 * top-down from the leaf, so a strand never floats free of the crown.
 */
@ApiStatus.Internal
public final class HangFromLeavesDecorator extends TreeDecorator {

    public static final MapCodec<HangFromLeavesDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(decorator -> decorator.blockProvider),
            BlockStateProvider.CODEC.optionalFieldOf("tip_provider").forGetter(decorator -> decorator.tipProvider),
            IntProvider.codec(1, 16).fieldOf("length").forGetter(decorator -> decorator.length),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(decorator -> decorator.chance),
            Codec.BOOL.optionalFieldOf("only_on_rim", false).forGetter(decorator -> decorator.onlyOnRim)
    ).apply(instance, HangFromLeavesDecorator::new));

    private final BlockStateProvider blockProvider;
    private final Optional<BlockStateProvider> tipProvider;
    private final IntProvider length;
    private final float chance;
    private final boolean onlyOnRim;

    private HangFromLeavesDecorator(BlockStateProvider blockProvider, Optional<BlockStateProvider> tipProvider,
                                    IntProvider length, float chance, boolean onlyOnRim) {
        this.blockProvider = blockProvider;
        this.tipProvider = tipProvider;
        this.length = length;
        this.chance = chance;
        this.onlyOnRim = onlyOnRim;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return IsekaiTreeDecorators.HANG_FROM_LEAVES.get();
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource random = context.random();
        Set<BlockPos> leafSet = new HashSet<>(context.leaves());
        for (BlockPos leaf : Util.shuffledCopy(context.leaves(), random)) {
            if (this.onlyOnRim && !isRim(leaf, leafSet)) {
                continue;
            }
            if (random.nextFloat() >= this.chance) {
                continue;
            }
            List<BlockPos> cells = new ArrayList<>();
            BlockPos.MutableBlockPos cursor = leaf.below().mutable();
            int maxLength = this.length.sample(random);
            while (cells.size() < maxLength && context.isAir(cursor)) {
                cells.add(cursor.immutable());
                cursor.move(Direction.DOWN);
            }
            for (int i = 0; i < cells.size(); i++) {
                BlockPos pos = cells.get(i);
                boolean isTip = i == cells.size() - 1 && this.tipProvider.isPresent();
                BlockStateProvider provider = isTip ? this.tipProvider.get() : this.blockProvider;
                DecoratorPlacing.place(context, pos, provider.getState(random, pos));
            }
        }
    }

    /** A leaf is on the rim when at least one horizontal neighbour is not part of the crown. */
    private static boolean isRim(BlockPos leaf, Set<BlockPos> leafSet) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!leafSet.contains(leaf.relative(direction))) {
                return true;
            }
        }
        return false;
    }
}
