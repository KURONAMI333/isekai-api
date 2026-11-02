package com.kuronami.isekaiapi.impl.wood;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

/**
 * Chest boat entity for a wood set declared through the public builder.
 *
 * <p>See {@link WoodBoat} for why one shared class serves every wood set.
 */
@ApiStatus.Internal
public class WoodChestBoat extends ChestBoat {

    public WoodChestBoat(EntityType<? extends Boat> type, Level level) {
        super(type, level);
    }

    @Override
    public Item getDropItem() {
        return WoodSets.boatDrop(this.getType(), true);
    }

    @Override
    public ItemEntity spawnAtLocation(ItemLike item) {
        return super.spawnAtLocation(WoodSets.substitutePlanks(this.getType(), item));
    }
}
