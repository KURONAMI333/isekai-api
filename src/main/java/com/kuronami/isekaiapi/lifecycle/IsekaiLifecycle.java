package com.kuronami.isekaiapi.lifecycle;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Server lifecycle hooks. v0.2 wires up the vanilla rule snapshot scanner;
 * the datapack reload pipeline ships in v0.3 alongside the JSON schema validator.
 */
@EventBusSubscriber(modid = IsekaiApi.MODID)
public final class IsekaiLifecycle {

    private IsekaiLifecycle() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        IsekaiApi.LOGGER.info("[Isekai] ServerAboutToStartEvent: scanning vanilla worldgen rules");
        var snapshot = VanillaRuleSnapshot.scan(event.getServer());
        Isekai.publishSnapshot(snapshot);
        IsekaiApi.LOGGER.info("[Isekai] Snapshot published (empty={}); query API now backed by cache",
                snapshot.isEmpty());
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        IsekaiApi.LOGGER.info("[Isekai v0.2] AddReloadListenerEvent: datapack reload pipeline deferred to v0.3");
        // TODO v0.3: register a PreparableReloadListener that scans data/<ns>/isekai/ JSON,
        // validates each WorldshapeDescriptor / LayeredDescriptor against the codec,
        // and rebuilds consumer-side state without a full server restart.
    }
}
