package com.kuronami.isekaiapi.lifecycle;

import com.kuronami.isekaiapi.IsekaiApi;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Server lifecycle hooks per spec §5.6. v0.1 logs the trigger points only;
 * vanilla rule scan + WorldshapeSnapshot cache build / datapack reload pipeline
 * land in v0.2.
 */
@EventBusSubscriber(modid = IsekaiApi.MODID)
public final class IsekaiLifecycle {

    private IsekaiLifecycle() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        IsekaiApi.LOGGER.info("[Isekai v0.1] ServerAboutToStartEvent: vanilla rule snapshot cache build deferred to v0.2");
        // TODO v0.2: scan PlacedFeature / Structure / MobSpawn / DensityFunction registries
        // TODO v0.2: build immutable WorldshapeSnapshot per dimension
        // TODO v0.2: trigger IsekaiQueryImpl to cache results
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        IsekaiApi.LOGGER.info("[Isekai v0.1] AddReloadListenerEvent: datapack reload pipeline deferred to v0.2");
        // TODO v0.2: register a PreparableReloadListener that scans data/<ns>/isekai/ JSON
        // TODO v0.2: re-build consumer descriptors on /reload
    }
}
