package com.kuronami.isekaiapi.tree.decorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import org.jetbrains.annotations.ApiStatus;

/**
 * Attaches blocks to the sides of the trunk — shelf fungus, moss, resin, bark growths — choosing
 * empty sides at random.
 *
 * <p>Datapack type {@code isekai_api:attach_to_logs}. Fields:
 * <ul>
 *   <li>{@code block_provider} (required) — what to attach.</li>
 *   <li>{@code chance} (required, 0..1) — per side, rolled separately, so one log can get zero to
 *       {@code max_per_log} attachments.</li>
 *   <li>{@code directions} (optional, default the four horizontals) — which sides may be used.</li>
 *   <li>{@code max_per_log} (optional, 1..16, default 1) — cap per log, applied before rolling, so
 *       the sides are a random sample rather than "always the first side".</li>
 * </ul>
 *
 * <p>The target position must be air; logs buried in foliage or in a neighbouring trunk are
 * skipped. The ground block the trunk placer writes below the base is not part of the trunk and is
 * skipped too, even though {@link TreeDecorator.Context#logs()} reports it. If the attached block
 * has a facing property (horizontal facing, six-way facing, or the four side booleans of a
 * vine-like block) it is turned to point away from the log.
 */
@ApiStatus.Internal
public final class AttachToLogsDecorator extends TreeDecorator {

    private static final List<Direction> DEFAULT_DIRECTIONS =
            List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    public static final MapCodec<AttachToLogsDecorator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(decorator -> decorator.blockProvider),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(decorator -> decorator.chance),
            ExtraCodecs.nonEmptyList(Direction.CODEC.listOf())
                    .optionalFieldOf("directions", DEFAULT_DIRECTIONS)
                    .forGetter(decorator -> decorator.directions),
            Codec.intRange(1, 16).optionalFieldOf("max_per_log", 1).forGetter(decorator -> decorator.maxPerLog)
    ).apply(instance, AttachToLogsDecorator::new));

    private final BlockStateProvider blockProvider;
    private final float chance;
    private final List<Direction> directions;
    private final int maxPerLog;

    private AttachToLogsDecorator(BlockStateProvider blockProvider, float chance,
                                  List<Direction> directions, int maxPerLog) {
        this.blockProvider = blockProvider;
        this.chance = chance;
        this.directions = directions;
        this.maxPerLog = maxPerLog;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return IsekaiTreeDecorators.ATTACH_TO_LOGS.get();
    }

    @Override
    public void place(TreeDecorator.Context context) {
        RandomSource random = context.random();
        List<BlockPos> logOrder = new ArrayList<>(DecoratorPlacing.logs(context));
        Util.shuffle(logOrder, random);
        List<Direction> sideOrder = new ArrayList<>(this.directions);
        for (BlockPos log : logOrder) {
            int placed = 0;
            Util.shuffle(sideOrder, random);
            for (Direction direction : sideOrder) {
                if (placed >= this.maxPerLog) {
                    break;
                }
                if (random.nextFloat() >= this.chance) {
                    continue;
                }
                BlockPos target = log.relative(direction);
                if (!context.isAir(target)) {
                    continue;
                }
                BlockState state = DecoratorPlacing.orient(
                        this.blockProvider.getState(random, target), direction);
                DecoratorPlacing.place(context, target, state);
                placed++;
            }
        }
    }
}
