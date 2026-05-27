package com.kuronami.isekaiapi.lifecycle;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Server lifecycle hooks:
 * <ul>
 *   <li>{@code ServerAboutToStartEvent} — scans the vanilla rule registries via
 *       {@link VanillaRuleSnapshot#scan} and publishes the result for {@code IsekaiQuery}.</li>
 *   <li>{@code AddReloadListenerEvent} — registers the two
 *       {@link IsekaiReloadListener} instances (worldshape / layered_worldshape JSON
 *       loading) plus {@link SnapshotRefreshListener} (rebuild the snapshot on every
 *       datapack reload so tag indices, biome step indices, and per-dim VerticalRange
 *       overrides stay current).</li>
 * </ul>
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
        // Register one listener per JSON directory so each runs on its own worker batch.
        event.addListener(IsekaiReloadListener.forSingleLayer());
        event.addListener(IsekaiReloadListener.forLayered());
        // Refresh the VanillaRuleSnapshot so tag indices, biome step indices, and
        // per-dim VerticalRange overrides reflect the post-reload registry state.
        event.addListener(new SnapshotRefreshListener());
        IsekaiApi.LOGGER.info("[Isekai] reload listeners registered: {} + {} + snapshot-refresh",
                IsekaiReloadListener.WORLDSHAPE_DIR, IsekaiReloadListener.LAYERED_DIR);
    }
}
