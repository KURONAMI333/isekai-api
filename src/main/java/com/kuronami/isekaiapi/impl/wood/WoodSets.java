package com.kuronami.isekaiapi.impl.wood;

import com.kuronami.isekaiapi.api.wood.WoodSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registry of the wood sets declared through the public builder, plus the entity-type to wood-set
 * lookup the boat classes need at runtime.
 *
 * <p>A wood set's boat entity is a plain {@code EntityType} registered by the consumer, so the
 * entity has no field pointing back at the set it belongs to. Looking the set up by the entity's
 * registry id keeps that link without a per-set entity subclass and without overloading vanilla
 * {@code Boat.Type} ordinals.
 */
@ApiStatus.Internal
public final class WoodSets {

    /**
     * Every set declared so far, and the boat lookup.
     *
     * <p>Mod constructors do not all run on one thread: NeoForge dispatches
     * {@code FMLConstructModEvent} per mod and runs the batches in parallel, so two mods can call
     * {@link WoodSet.Builder#register} at the same time. Both collections are therefore concurrent,
     * and reads copy instead of iterating the live collection.
     */
    private static final List<WoodSet> ALL = new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, WoodSet> BY_BOAT_ENTITY = new ConcurrentHashMap<>();

    private WoodSets() {}

    /** Called by {@link WoodSet.Builder#register} once every part has been queued. */
    public static void add(WoodSet set) {
        ALL.add(set);
        set.boatEntity().ifPresent(holder -> BY_BOAT_ENTITY.put(holder.getKey().location(), set));
        set.chestBoatEntity().ifPresent(holder -> BY_BOAT_ENTITY.put(holder.getKey().location(), set));
    }

    /** Every wood set registered through the public API, in declaration order. */
    public static List<WoodSet> all() {
        return List.copyOf(ALL);
    }

    @Nullable
    private static WoodSet forBoat(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id == null ? null : BY_BOAT_ENTITY.get(id);
    }

    /** The item a broken boat of this entity type drops; vanilla oak as a last resort. */
    public static Item boatDrop(EntityType<?> type, boolean chest) {
        WoodSet set = forBoat(type);
        Item fallback = chest ? Items.OAK_CHEST_BOAT : Items.OAK_BOAT;
        if (set == null) {
            return fallback;
        }
        return (chest ? set.chestBoatItem() : set.boatItem())
                .map(holder -> (Item) holder.value())
                .orElse(fallback);
    }

    /** The planks a boat of this entity type spills when it breaks on a long fall. */
    public static ItemLike boatPlanks(EntityType<?> type) {
        WoodSet set = forBoat(type);
        if (set == null) {
            return Items.OAK_PLANKS;
        }
        return set.planks().map(holder -> (ItemLike) holder.value()).orElse(Items.OAK_PLANKS);
    }

    /**
     * The texture a wood set's boat or chest boat is rendered with, in the same layout vanilla
     * uses: {@code textures/entity/boat/<path>.png} and
     * {@code textures/entity/chest_boat/<path>.png}.
     */
    public static ResourceLocation boatTexture(ResourceLocation woodSet, boolean chest) {
        return ResourceLocation.fromNamespaceAndPath(woodSet.getNamespace(),
                "textures/entity/" + (chest ? "chest_boat" : "boat") + "/" + woodSet.getPath() + ".png");
    }

    /**
     * The texture a wood set's sign is rendered with, in the layout vanilla's
     * {@code Sheets#createSignMaterial} reads: {@code textures/entity/signs/<path>.png}, or
     * {@code textures/entity/signs/hanging/<path>.png} for a hanging sign.
     */
    public static ResourceLocation signTexture(ResourceLocation woodSet, boolean hanging) {
        return ResourceLocation.fromNamespaceAndPath(woodSet.getNamespace(),
                "textures/entity/signs/" + (hanging ? "hanging/" : "") + woodSet.getPath() + ".png");
    }

    /**
     * The texture a hanging sign's editor screen is drawn with:
     * {@code textures/gui/hanging_signs/<path>.png}.
     */
    public static ResourceLocation hangingSignGuiTexture(ResourceLocation woodSet) {
        return ResourceLocation.fromNamespaceAndPath(woodSet.getNamespace(),
                "textures/gui/hanging_signs/" + woodSet.getPath() + ".png");
    }

    /**
     * Boat breakage drops the planks of its {@code Boat.Type}, which stays oak for wood-set boats;
     * this swaps oak planks for the set's own. Every other item passes through untouched.
     */
    public static ItemLike substitutePlanks(EntityType<?> type, ItemLike item) {
        // Boat.Type#getPlanks() hands over the oak planks *block*; compare as items.
        return item.asItem() == Items.OAK_PLANKS ? boatPlanks(type) : item;
    }
}
