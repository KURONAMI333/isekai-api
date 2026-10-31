package com.kuronami.isekaiapi.network;

import com.kuronami.isekaiapi.api.remap.ClientAtmosphereOverride;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store of the last {@link AtmosphereSyncPayload} received per dimension.
 *
 * <p>{@link #knows(ResourceKey)} distinguishes "the server told us this dimension has no
 * client atmosphere" (entry present, empty band list) from "we have not heard from the server
 * yet" (entry absent). {@code IsekaiClientAtmosphere} only falls back to the local remap
 * registry in the second case, so a dedicated-server client never resurrects a stale local
 * declaration. Deliberately holds no Minecraft client types: it is safe to load on a dedicated
 * server, where it stays empty.
 * @since 3.0.0
 */
@ApiStatus.Internal
public final class AtmosphereSyncCache {

    private static final Map<ResourceKey<Level>, List<AtmosphereSyncPayload.Band>> BANDS =
            new ConcurrentHashMap<>();

    private AtmosphereSyncCache() {}

    /** Replace the cached entry for the payload's dimension (an empty list clears it). @since 3.0.0 */
    public static void accept(AtmosphereSyncPayload payload) {
        BANDS.put(payload.dimension(), payload.bands());
    }

    /** Forget every received dimension. Called when the client leaves the world/server. @since 3.0.0 */
    public static void clear() {
        BANDS.clear();
    }

    /** {@code true} once a payload for {@code dimension} has been received. @since 3.0.0 */
    public static boolean knows(ResourceKey<Level> dimension) {
        return BANDS.containsKey(dimension);
    }

    /** The override whose band contains {@code y}, or empty when none does. @since 3.0.0 */
    public static Optional<ClientAtmosphereOverride> at(ResourceKey<Level> dimension, int y) {
        List<AtmosphereSyncPayload.Band> bands = BANDS.get(dimension);
        if (bands == null) return Optional.empty();
        for (AtmosphereSyncPayload.Band band : bands) {
            if (band.contains(y)) return Optional.of(band.atmosphere());
        }
        return Optional.empty();
    }
}
