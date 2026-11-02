package com.kuronami.isekaiapi.impl.wood;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

/**
 * Boat entity for a wood set declared through the public builder.
 *
 * <p>One shared class serves every wood set: the set is resolved from the entity type at runtime,
 * so a species needs no entity subclass of its own.
 *
 * <p>Vanilla derives the drop item, the display name and the pickup result from
 * {@code Boat.Type}; this entity keeps the default type and answers from its wood set instead.
 * {@code Boat#setVariant} is never called on it, so nothing inherits another species' item.
 */
@ApiStatus.Internal
public class WoodBoat extends Boat {

    public WoodBoat(EntityType<? extends Boat> type, Level level) {
        super(type, level);
    }

    @Override
    public Item getDropItem() {
        return WoodSets.boatDrop(this.getType(), false);
    }

    /**
     * Vanilla breaks a boat that falls from more than three blocks into three of its variant's
     * planks and two sticks. Those planks come from {@code Boat.Type}, which stays oak here, so the
     * plank item is substituted on the way out; the fall itself keeps vanilla's private state
     * handling.
     */
    @Override
    public ItemEntity spawnAtLocation(ItemLike item) {
        return super.spawnAtLocation(WoodSets.substitutePlanks(this.getType(), item));
    }
}
