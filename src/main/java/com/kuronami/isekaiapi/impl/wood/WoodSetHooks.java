package com.kuronami.isekaiapi.impl.wood;

import com.kuronami.isekaiapi.api.wood.WoodSet;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import org.jetbrains.annotations.ApiStatus;

/**
 * The once-per-load wiring behind {@link WoodSet}: everything a wood set needs that cannot be
 * expressed on the blocks themselves.
 *
 * <p>Three hooks, one per mechanism:
 *
 * <ul>
 *   <li>sign and hanging-sign block entities — {@link BlockEntityTypeAddBlocksEvent} adds this
 *       set's sign blocks to vanilla's {@code SIGN} / {@code HANGING_SIGN} types.</li>
 *   <li>flammability — {@link FMLCommonSetupEvent} writes vanilla oak's fire odds into
 *       {@code FireBlock} (its odds map has no data-driven form in 1.21.1).</li>
 *   <li>compostability — {@link DataMapsUpdatedEvent} adds this set's leaves and sapling to the
 *       live {@code neoforge:compostables} map on the server, without replacing an entry the
 *       data map file or a datapack already set.</li>
 * </ul>
 *
 * <p>Nothing here runs unless a consumer declared a wood set, so a game without any wood set pays
 * three empty loops.
 */
@ApiStatus.Internal
public final class WoodSetHooks {

    /** Vanilla oak's compost chance for leaves and saplings. */
    private static final float COMPOST_CHANCE = 0.3F;

    private WoodSetHooks() {}

    /** Installs the handlers. Called once from the mod constructor. */
    public static void init(IEventBus modBus) {
        modBus.addListener(WoodSetHooks::addBlockEntities);
        modBus.addListener(WoodSetHooks::setFlammability);
        NeoForge.EVENT_BUS.addListener(WoodSetHooks::addCompostables);
    }

    private static void addBlockEntities(BlockEntityTypeAddBlocksEvent event) {
        for (WoodSet set : WoodSets.all()) {
            set.sign().ifPresent(block -> event.modify(BlockEntityType.SIGN, block.get()));
            set.wallSign().ifPresent(block -> event.modify(BlockEntityType.SIGN, block.get()));
            set.hangingSign().ifPresent(block -> event.modify(BlockEntityType.HANGING_SIGN, block.get()));
            set.wallHangingSign().ifPresent(block -> event.modify(BlockEntityType.HANGING_SIGN, block.get()));
        }
    }

    private static void setFlammability(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlock fire = (FireBlock) Blocks.FIRE;
            for (WoodSet set : WoodSets.all()) {
                // Same odds as vanilla oak: sawn blocks spread fire and burn out at 5/20, the
                // bark-covered pillar blocks and their stripped versions at 5/5, leaves at 30/60.
                set.planks().ifPresent(block -> fire.setFlammable(block.get(), 5, 20));
                set.slab().ifPresent(block -> fire.setFlammable(block.get(), 5, 20));
                set.stairs().ifPresent(block -> fire.setFlammable(block.get(), 5, 20));
                set.fence().ifPresent(block -> fire.setFlammable(block.get(), 5, 20));
                set.fenceGate().ifPresent(block -> fire.setFlammable(block.get(), 5, 20));
                set.log().ifPresent(block -> fire.setFlammable(block.get(), 5, 5));
                set.wood().ifPresent(block -> fire.setFlammable(block.get(), 5, 5));
                set.strippedLog().ifPresent(block -> fire.setFlammable(block.get(), 5, 5));
                set.strippedWood().ifPresent(block -> fire.setFlammable(block.get(), 5, 5));
                set.leaves().ifPresent(block -> fire.setFlammable(block.get(), 30, 60));
            }
        });
    }

    private static void addCompostables(DataMapsUpdatedEvent event) {
        // The server-side map is the mutable one; the client receives the finished map from the
        // server and its copy is unmodifiable.
        if (event.getCause() != DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD) {
            return;
        }
        event.ifRegistry(Registries.ITEM, registry -> {
            Map<ResourceKey<Item>, Compostable> compostables =
                    registry.getDataMap(NeoForgeDataMaps.COMPOSTABLES);
            for (WoodSet set : WoodSets.all()) {
                // putIfAbsent, not put: when the consumer ships the data map (WoodSetDataGen) or a
                // datapack decides the chance, that value is the one the game should use. This
                // hook is the fallback for a set whose mod does not run datagen at all.
                set.leavesItem().ifPresent(item ->
                        compostables.putIfAbsent(item.getKey(), new Compostable(COMPOST_CHANCE)));
                set.saplingItem().ifPresent(item ->
                        compostables.putIfAbsent(item.getKey(), new Compostable(COMPOST_CHANCE)));
            }
        });
    }
}
