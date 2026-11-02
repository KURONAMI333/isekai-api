package com.kuronami.isekaiapi.impl.wood;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jetbrains.annotations.ApiStatus;

/**
 * A log/wood pillar that an axe strips into another block, keeping its axis.
 *
 * <p>Vanilla resolves stripping through {@code AxeItem.STRIPPABLES}, an immutable map built in a
 * static initialiser, and NeoForge 1.21.1 ships no strippables data map. The supported extension
 * point is therefore {@link net.neoforged.neoforge.common.extensions.IBlockExtension#getToolModifiedState},
 * which {@code AxeItem#useOn} consults for every right click; overriding it per block avoids
 * touching vanilla's map and keeps the mapping local to the wood set that declared it.
 */
@ApiStatus.Internal
public final class StrippablePillarBlock extends RotatedPillarBlock {

    @Nullable
    private final Supplier<? extends Block> stripped;

    public StrippablePillarBlock(BlockBehaviour.Properties properties, @Nullable Supplier<? extends Block> stripped) {
        super(properties);
        this.stripped = stripped;
    }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility itemAbility, boolean simulate) {
        if (itemAbility == ItemAbilities.AXE_STRIP) {
            Block target = stripped == null ? null : stripped.get();
            if (target != null) {
                return target.defaultBlockState().setValue(AXIS, state.getValue(AXIS));
            }
            return null;
        }
        return super.getToolModifiedState(state, context, itemAbility, simulate);
    }
}
