package com.kuronami.isekaiapi.impl.wood;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Dispenser behaviour for a wood set's boat items. Vanilla's {@code BoatDispenseItemBehavior}
 * constructs a vanilla boat and assigns a {@code Boat.Type}, so it cannot place a wood-set boat;
 * this repeats its placement rules (in front of the dispenser, on water or on air above water,
 * otherwise drop the item) and spawns the set's own entity type.
 */
@ApiStatus.Internal
public final class WoodBoatDispenseBehavior extends DefaultDispenseItemBehavior {

    private final DefaultDispenseItemBehavior fallback = new DefaultDispenseItemBehavior();
    private final Supplier<? extends EntityType<? extends Boat>> entityType;

    public WoodBoatDispenseBehavior(Supplier<? extends EntityType<? extends Boat>> entityType) {
        this.entityType = entityType;
    }

    @Override
    public ItemStack execute(BlockSource source, ItemStack stack) {
        Direction direction = source.state().getValue(DispenserBlock.FACING);
        ServerLevel level = source.level();
        Vec3 center = source.center();
        double reach = 0.5625D + (double) EntityType.BOAT.getWidth() / 2.0D;
        double x = center.x() + (double) direction.getStepX() * reach;
        double y = center.y() + (double) ((float) direction.getStepY() * 1.125F);
        double z = center.z() + (double) direction.getStepZ() * reach;
        BlockPos front = source.pos().relative(direction);

        Boat boat = entityType.get().create(level);
        if (boat == null) {
            return fallback.dispense(source, stack);
        }
        EntityType.<Boat>createDefaultStackConfig(level, stack, null).accept(boat);
        boat.setYRot(direction.toYRot());

        double lift;
        if (boat.canBoatInFluid(level.getFluidState(front))) {
            lift = 1.0D;
        } else if (level.getBlockState(front).isAir() && boat.canBoatInFluid(level.getFluidState(front.below()))) {
            lift = 0.0D;
        } else {
            return fallback.dispense(source, stack);
        }

        boat.setPos(x, y + lift, z);
        level.addFreshEntity(boat);
        stack.shrink(1);
        return stack;
    }

    @Override
    protected void playSound(BlockSource source) {
        source.level().levelEvent(1000, source.pos(), 0);
    }
}
