package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Places features at {@code WORLD_SURFACE_WG + offset(rand)}. Drop-in replacement for
 * vanilla {@code HeightRangePlacement.uniform(...)} when the consumer's playable range
 * is anchored to terrain surface rather than a fixed Y range.
 *
 * <p>v0.1: anchor is hardcoded to {@link Heightmap.Types#WORLD_SURFACE_WG}. v0.2 will
 * add the {@link com.kuronami.isekaiapi.api.remap.SurfaceAnchor} dispatch codec so the
 * anchor can be selected from datapack JSON.
 */
public class SurfaceRelativeModifier extends PlacementModifier {
    public static final MapCodec<SurfaceRelativeModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IntProvider.CODEC.fieldOf("offset").forGetter(m -> m.offset)
    ).apply(i, SurfaceRelativeModifier::new));

    private final IntProvider offset;

    public SurfaceRelativeModifier(IntProvider offset) {
        this.offset = offset;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        int surfaceY = ctx.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        int y = surfaceY + offset.sample(rand);
        return Stream.of(new BlockPos(pos.getX(), y, pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.SURFACE_RELATIVE.get();
    }
}
