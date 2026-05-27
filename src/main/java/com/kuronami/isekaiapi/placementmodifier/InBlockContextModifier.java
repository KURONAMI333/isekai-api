package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Filters placement positions by block context: the block at the position must be one
 * of {@code match_blocks}, and optionally there must be {@code require_air_above} blocks
 * of air directly above it, and the position must (optionally) not be inside a fluid.
 *
 * <p>Useful for consumers that want ore/feature placement to be context-sensitive
 * (e.g., "only spawn this in stone or deepslate, with at least 1 block of air above").
 */
public class InBlockContextModifier extends PlacementModifier {
    public static final MapCodec<InBlockContextModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("match_blocks").forGetter(m -> m.matchBlocks),
            Codec.BOOL.optionalFieldOf("exclude_in_fluid", false).forGetter(m -> m.excludeInFluid),
            Codec.INT.optionalFieldOf("require_air_above", 0).forGetter(m -> m.requireAirAbove)
    ).apply(i, InBlockContextModifier::new));

    private final HolderSet<Block> matchBlocks;
    private final boolean excludeInFluid;
    private final int requireAirAbove;

    public InBlockContextModifier(HolderSet<Block> matchBlocks, boolean excludeInFluid, int requireAirAbove) {
        this.matchBlocks = matchBlocks;
        this.excludeInFluid = excludeInFluid;
        this.requireAirAbove = requireAirAbove;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        WorldGenLevel level = ctx.getLevel();
        BlockState state = level.getBlockState(pos);

        if (!matchBlocks.contains(state.getBlockHolder())) return Stream.empty();
        if (excludeInFluid && !state.getFluidState().isEmpty()) return Stream.empty();

        if (requireAirAbove > 0) {
            for (int i = 1; i <= requireAirAbove; i++) {
                if (!level.getBlockState(pos.above(i)).isAir()) return Stream.empty();
            }
        }
        return Stream.of(pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.IN_BLOCK_CONTEXT.get();
    }

    private static class RegistryCodecs {
        static <T> com.mojang.serialization.Codec<HolderSet<T>> homogeneousList(net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<T>> key) {
            return net.minecraft.core.RegistryCodecs.homogeneousList(key);
        }
    }
}
