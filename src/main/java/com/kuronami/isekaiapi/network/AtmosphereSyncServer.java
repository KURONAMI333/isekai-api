package com.kuronami.isekaiapi.network;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.ApiStatus;

/**
 * Pushes the active {@code client_atmosphere} to each connected client.
 *
 * <ul>
 *   <li>{@link OnDatapackSyncEvent} — fires on login and after every datapack reload, which is
 *       exactly when the server-side worldshape registry changes.</li>
 *   <li>{@link PlayerEvent.PlayerChangedDimensionEvent} — the payload is scoped to one
 *       dimension, so a player crossing into another one gets the new bands.</li>
 *   <li>{@link PlayerEvent.PlayerRespawnEvent} — respawning in another dimension does not
 *       fire the dimension-change event.</li>
 * </ul>
 *
 * <p>A client that never registered the optional channel (vanilla, or NeoForge without Isekai
 * API) is skipped; it keeps vanilla colours and its connection is unaffected.
 * @since 3.0.0
 */
@ApiStatus.Internal
@EventBusSubscriber(modid = IsekaiApi.MODID)
public final class AtmosphereSyncServer {

    private AtmosphereSyncServer() {}

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(AtmosphereSyncServer::sync);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    /** Respawning in another dimension (death, leaving the End) does not fire a dimension change. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    private static void sync(ServerPlayer player) {
        if (player.connection == null) return;
        if (!player.connection.hasChannel(AtmosphereSyncPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player,
                AtmosphereSyncPayload.of(player.serverLevel().dimension(), Isekai.remap()));
    }
}
