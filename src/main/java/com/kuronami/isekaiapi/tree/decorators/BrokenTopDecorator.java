package com.kuronami.isekaiapi.tree.decorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Snaps the top off a tree — the blunt, uneven finish of a dead, burnt or storm-broken trunk.
 *
 * <p>No trunk placer can express this: a placer decides a height and fills it top to bottom, so
 * "the same tree, one or two logs shorter, sometimes with a different block at the break" is not a
 * parameter any of them has. A decorator can, because it runs after the trunk exists and may
 * overwrite it.
 *
 * <p>Datapack type {@code isekai_api:broken_top}. Fields:
 * <ul>
 *   <li>{@code count} (required, IntProvider 1..12) — how many of the highest logs to remove. At
 *       least one log always survives, so a short trunk is shortened rather than deleted.</li>
 *   <li>{@code fill_provider} (optional) — what to leave in place of a removed log instead of air,
 *       e.g. a darker block for a charred break. Without it the removed logs become air.</li>
 *   <li>{@code fill_chance} (optional, 0..1, default 1) — per removed log, whether the fill block is
 *       used. Below {@code 1} some breaks are open air and some are plugged, so a stand of trees
 *       does not all break the same way.</li>
 * </ul>
 *
 * <p>The removed logs are the highest entries of {@link TreeDecorator.Context#logs()}, so a 2x2
 * trunk loses single logs rather than whole layers — a jagged, unequal break, which is what a
 * snapped trunk looks like. Within one column the fill block can only sit at the bottom of the
 * break: once a log there is opened to air, every removed log above it is air as well. A fill block with an {@code axis} is set to {@code y} so it matches the trunk it
 * replaces instead of inheriting the provider's default.
 *
 * <p>Decorators run in list order. List this before {@code isekai_api:branch_stubs} and the other
 * decorators that hang things off the trunk, so they only see the logs that survive the break; a
 * branch added first would be left floating beside the removed logs.
 *
 * <p>Leaves are placed before decorators run, so a leafy tree keeps its crown after its top log is
 * gone; pair this with {@code isekai_api:none} or a lower crown for a bare snag.
 */
@ApiStatus.Internal
public final class BrokenTopDecorator extends TreeDecorator {

    public static final MapCodec<BrokenTopDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IntProvider.codec(1, 12).fieldOf("count").forGetter(decorator -> decorator.count),
            BlockStateProvider.CODEC.optionalFieldOf("fill_provider").forGetter(decorator -> decorator.fillProvider),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("fill_chance", 1.0F)
                    .forGetter(decorator -> decorator.fillChance)
    ).apply(instance, BrokenTopDecorator::new));

    private final IntProvider count;
    private final Optional<BlockStateProvider> fillProvider;
    private final float fillChance;

    private BrokenTopDecorator(IntProvider count, Optional<BlockStateProvider> fillProvider, float fillChance) {
        this.count = count;
        this.fillProvider = fillProvider;
        this.fillChance = fillChance;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return IsekaiTreeDecorators.BROKEN_TOP.get();
    }

    @Override
    public void place(TreeDecorator.Context context) {
        List<BlockPos> logs = DecoratorPlacing.logs(context);
        if (logs.size() < 2) {
            return;
        }
        RandomSource random = context.random();
        int wanted = Math.min(this.count.sample(random), logs.size() - 1);
        // Walk the removed logs bottom-up, one column at a time: once a column has broken open to
        // air, everything above it in that column is air too. Choosing air or fill independently
        // per log would leave a fill block floating over an air gap.
        Set<Long> openColumns = new HashSet<>();
        for (int i = logs.size() - wanted; i < logs.size(); i++) {
            BlockPos pos = logs.get(i);
            long column = ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
            BlockState state = openColumns.contains(column)
                    ? Blocks.AIR.defaultBlockState()
                    : breakState(context, random, pos);
            if (state.isAir()) {
                openColumns.add(column);
            }
            context.setBlock(pos, state);
        }
    }

    /** The block left at a broken log: air, or the fill block turned to stand like the trunk. */
    private BlockState breakState(TreeDecorator.Context context, RandomSource random, BlockPos pos) {
        if (this.fillProvider.isPresent() && random.nextFloat() < this.fillChance) {
            BlockState state = this.fillProvider.get().getState(random, pos);
            if (state.hasProperty(BlockStateProperties.AXIS)) {
                return state.setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y);
            }
            return state;
        }
        return Blocks.AIR.defaultBlockState();
    }
}
