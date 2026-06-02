package com.kuronami.isekaiapi.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Place a connected blob of {@code size} blocks starting at the origin, growing by random walk
 * (BFS frontier with random pop). Each step places one block from {@code block} at a position
 * adjacent to an already-placed block, choosing among the 6 face neighbours uniformly at
 * random and skipping ones that are not {@code replaceable_state} (defaults to air-only via
 * the {@code can_replace_solid} flag).
 *
 * <p>Neutral primitive — geometric "blob of N connected blocks." No moss/mushroom/oasis
 * semantics. {@code block} is the consumer's choice of {@link BlockStateProvider}.
 *
 * <p>Use cases consumers compose from this: dirt patches, moss veins, ore clusters, fungus
 * spreads, leaf piles, dropped vegetation.
 *
 * <p>JSON: {@code {"type":"isekai_api:cluster", "block": {...provider...}, "size": {"type":"minecraft:uniform","value":{"min_inclusive":6,"max_inclusive":14}}, "can_replace_solid": false}}.
 */
@ApiStatus.Internal
public class ClusterFeature extends Feature<ClusterFeature.Config> {

    public record Config(BlockStateProvider block, IntProvider size,
                         boolean canReplaceSolid) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("block").forGetter(Config::block),
                IntProvider.codec(1, 4096).fieldOf("size").forGetter(Config::size),
                Codec.BOOL.optionalFieldOf("can_replace_solid", false).forGetter(Config::canReplaceSolid)
        ).apply(i, Config::new));
    }

    public ClusterFeature(Codec<Config> codec) {
        super(codec);
    }

    public ClusterFeature() {
        this(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource random = ctx.random();
        Config config = ctx.config();
        BlockPos origin = ctx.origin();
        int target = config.size().sample(random);
        if (target <= 0) return false;

        Set<BlockPos> placed = new HashSet<>(target);
        Deque<BlockPos> frontier = new ArrayDeque<>();
        if (!canPlace(level, origin, config.canReplaceSolid())) return false;
        level.setBlock(origin, config.block().getState(random, origin), 2);
        placed.add(origin);
        frontier.add(origin);

        while (placed.size() < target && !frontier.isEmpty()) {
            // pop a random frontier cell — gives a rounder blob than always-last LIFO/FIFO
            BlockPos current = popRandom(frontier, random);
            List<Direction> dirs = new ArrayList<>(6);
            for (Direction d : Direction.values()) dirs.add(d);
            // shuffle so we don't bias toward east etc.
            for (int i = dirs.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                Direction tmp = dirs.get(i); dirs.set(i, dirs.get(j)); dirs.set(j, tmp);
            }
            boolean grew = false;
            for (Direction d : dirs) {
                BlockPos neighbour = current.relative(d);
                if (placed.contains(neighbour)) continue;
                if (!canPlace(level, neighbour, config.canReplaceSolid())) continue;
                level.setBlock(neighbour, config.block().getState(random, neighbour), 2);
                placed.add(neighbour);
                frontier.add(neighbour);
                grew = true;
                break;
            }
            // if no neighbour was placeable, this cell is fully surrounded — drop it; loop again
            if (grew) frontier.add(current);
        }
        return placed.size() > 0;
    }

    private static boolean canPlace(WorldGenLevel level, BlockPos pos, boolean canReplaceSolid) {
        BlockState existing = level.getBlockState(pos);
        if (existing.isAir()) return true;
        if (canReplaceSolid) return true;
        return existing.canBeReplaced();
    }

    private static BlockPos popRandom(Deque<BlockPos> frontier, RandomSource random) {
        // O(n) pick — frontier sizes here stay small (<= target which is <= 256)
        int idx = random.nextInt(frontier.size());
        BlockPos[] arr = frontier.toArray(new BlockPos[0]);
        BlockPos chosen = arr[idx];
        frontier.remove(chosen);
        return chosen;
    }
}
