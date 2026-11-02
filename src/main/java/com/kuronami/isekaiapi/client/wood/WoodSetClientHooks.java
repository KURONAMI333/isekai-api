package com.kuronami.isekaiapi.client.wood;

import net.minecraft.client.renderer.Sheets;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.wood.WoodSet;
import com.kuronami.isekaiapi.impl.wood.WoodSets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers the boat renderers for the wood sets declared through the public builder.
 *
 * <p>Client-only: a dedicated server never loads this class, and a wood set without boats
 * registers no renderer.
 */
@ApiStatus.Internal
@EventBusSubscriber(modid = IsekaiApi.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WoodSetClientHooks {

    private WoodSetClientHooks() {}

    /**
     * Adds every wood set's sign materials to {@link Sheets}. Its maps are filled from
     * {@code WoodType.values()} when the class first loads; this covers a load that happened
     * before a consumer declared its set. Re-adding an existing type is harmless.
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (WoodSet set : WoodSets.all()) {
                Sheets.addWoodType(set.woodType());
            }
        });
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (WoodSet set : WoodSets.all()) {
            set.boatEntity().ifPresent(holder -> event.registerEntityRenderer(holder.value(),
                    context -> new WoodBoatRenderer(context, set.id(), false)));
            set.chestBoatEntity().ifPresent(holder -> event.registerEntityRenderer(holder.value(),
                    context -> new WoodBoatRenderer(context, set.id(), true)));
        }
    }
}
