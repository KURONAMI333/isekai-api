package com.kuronami.isekaiapi.impl.wood;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * Boat / chest-boat item for a wood set declared through the public builder.
 *
 * <p>{@code BoatItem} constructs {@code new Boat} / {@code new ChestBoat} itself and then assigns a
 * vanilla {@code Boat.Type}, so it cannot produce a wood-set boat. This item repeats the placement
 * rules (eye-to-block ray, overlapping-entity veto, collision check, stack consumption) and spawns
 * the wood set's own entity type instead.
 */
@ApiStatus.Internal
public class WoodBoatItem extends Item {

    private static final Predicate<Entity> ENTITY_PREDICATE = EntitySelector.NO_SPECTATORS.and(Entity::isPickable);

    private final Supplier<? extends EntityType<? extends Boat>> entityType;

    public WoodBoatItem(Supplier<? extends EntityType<? extends Boat>> entityType, Item.Properties properties) {
        super(properties);
        this.entityType = entityType;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }

        Vec3 view = player.getViewVector(1.0F);
        List<Entity> overlapping = level.getEntities(player,
                player.getBoundingBox().expandTowards(view.scale(5.0D)).inflate(1.0D), ENTITY_PREDICATE);
        if (!overlapping.isEmpty()) {
            Vec3 eye = player.getEyePosition();
            for (Entity entity : overlapping) {
                if (entity.getBoundingBox().inflate(entity.getPickRadius()).contains(eye)) {
                    return InteractionResultHolder.pass(stack);
                }
            }
        }

        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        Boat boat = spawn(level, stack, player);
        if (boat == null) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 location = hit.getLocation();
        boat.setPos(location.x, location.y, location.z);
        boat.xo = location.x;
        boat.yo = location.y;
        boat.zo = location.z;
        boat.setYRot(player.getYRot());
        if (!level.noCollision(boat, boat.getBoundingBox())) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide) {
            level.addFreshEntity(boat);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, hit.getLocation());
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Nullable
    private Boat spawn(Level level, ItemStack stack, Player player) {
        Boat boat = entityType.get().create(level);
        if (boat == null) {
            return null;
        }
        if (level instanceof ServerLevel serverLevel) {
            EntityType.<Boat>createDefaultStackConfig(serverLevel, stack, player).accept(boat);
        }
        return boat;
    }
}
