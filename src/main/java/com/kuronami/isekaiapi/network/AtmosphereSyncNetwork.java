package com.kuronami.isekaiapi.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers the {@link AtmosphereSyncPayload} channel.
 *
 * <p>The channel is <b>optional</b>: a vanilla client, or a NeoForge client without Isekai API,
 * still completes the handshake. The server side checks
 * {@code player.connection.hasChannel(AtmosphereSyncPayload.TYPE)} before sending, so an
 * absent channel costs nothing. Registration and the cache handler contain no client-only
 * types, so this class is safe to load on a dedicated server.
 * @since 3.0.0
 */
@ApiStatus.Internal
public final class AtmosphereSyncNetwork {

    /** Protocol version of the channel; bump when the payload shape changes. @since 3.0.0 */
    private static final String VERSION = "1";

    private AtmosphereSyncNetwork() {}

    /** Mod-bus listener; called from {@code IsekaiApi}'s constructor. */
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(VERSION)
                .optional()
                .playToClient(AtmosphereSyncPayload.TYPE, AtmosphereSyncPayload.STREAM_CODEC,
                        (payload, context) -> AtmosphereSyncCache.accept(payload));
    }
}
