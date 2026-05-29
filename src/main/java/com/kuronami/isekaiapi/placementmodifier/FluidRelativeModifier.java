package com.kuronami.isekaiapi.placementmodifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.material.Fluids;

import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * Places features at a Y offset from the top or bottom of the column's
 * contiguous water body. Useful for Deep Sea / Island World style consumers
 * that want kelp/coral/etc. anchored to fluid boundaries rather than terrain.
 *
 * <p>Targeted fluid is {@link Fluids#WATER}; the anchor (FLUID_TOP / FLUID_BOTTOM) is
 * configurable via the codec.
 */
@ApiStatus.Internal
public class FluidRelativeModifier extends PlacementModifier {
    public static final MapCodec<FluidRelativeModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Anchor.CODEC.fieldOf("anchor").forGetter(m -> m.anchor),
            IntProvider.CODEC.fieldOf("offset").forGetter(m -> m.offset)
    ).apply(i, FluidRelativeModifier::new));

    private final Anchor anchor;
    private final IntProvider offset;

    public FluidRelativeModifier(Anchor anchor, IntProvider offset) {
        this.anchor = anchor;
        this.offset = offset;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        WorldGenLevel level = ctx.getLevel();
        int x = pos.getX();
        int z = pos.getZ();

        // Scan the column for the water body.
        int top = level.getMaxBuildHeight() - 1;
        int bottom = level.getMinBuildHeight();
        int fluidTop = Integer.MIN_VALUE;
        int fluidBottom = Integer.MAX_VALUE;
        for (int y = top; y >= bottom; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(p);
            if (state.getFluidState().getType() == Fluids.WATER) {
                if (y > fluidTop) fluidTop = y;
                if (y < fluidBottom) fluidBottom = y;
            }
        }
        if (fluidTop == Integer.MIN_VALUE) {
            return Stream.empty();  // No water in this column.
        }

        int anchorY = (anchor == Anchor.FLUID_TOP) ? fluidTop : fluidBottom;
        return Stream.of(new BlockPos(x, anchorY + offset.sample(rand), z));
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.FLUID_RELATIVE.get();
    }

    public enum Anchor implements StringRepresentable {
        FLUID_TOP("fluid_top"),
        FLUID_BOTTOM("fluid_bottom");

        public static final Codec<Anchor> CODEC = StringRepresentable.fromEnum(Anchor::values);
        private final String name;

        Anchor(String name) { this.name = name; }

        @Override public String getSerializedName() { return name; }
    }
}
