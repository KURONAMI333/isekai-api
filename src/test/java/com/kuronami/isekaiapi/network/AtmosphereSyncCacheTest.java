package com.kuronami.isekaiapi.network;

import com.kuronami.isekaiapi.api.remap.ClientAtmosphereOverride;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cache semantics the client hooks depend on: know vs not-heard-yet, band resolution, and the
 * disconnect clear that keeps one server's atmosphere out of the next world.
 */
class AtmosphereSyncCacheTest {

    private static final ClientAtmosphereOverride LOWER = new ClientAtmosphereOverride(
            Optional.of(0x101010), Optional.empty(), Optional.empty(), Optional.empty());
    private static final ClientAtmosphereOverride UPPER = new ClientAtmosphereOverride(
            Optional.empty(), Optional.of(0x202020), Optional.empty(), Optional.empty());

    @AfterEach
    void clearCache() {
        AtmosphereSyncCache.clear();
    }

    @Test
    void unknownDimensionIsNotKnown() {
        assertFalse(AtmosphereSyncCache.knows(Level.OVERWORLD));
        assertTrue(AtmosphereSyncCache.at(Level.OVERWORLD, 0).isEmpty());
    }

    @Test
    void acceptsBandsAndResolvesByY() {
        AtmosphereSyncCache.accept(new AtmosphereSyncPayload(Level.OVERWORLD, List.of(
                new AtmosphereSyncPayload.Band(0, 64, LOWER),
                new AtmosphereSyncPayload.Band(64, 128, UPPER))));

        assertTrue(AtmosphereSyncCache.knows(Level.OVERWORLD));
        assertEquals(Optional.of(LOWER), AtmosphereSyncCache.at(Level.OVERWORLD, 63));
        assertEquals(Optional.of(UPPER), AtmosphereSyncCache.at(Level.OVERWORLD, 64));
        assertTrue(AtmosphereSyncCache.at(Level.OVERWORLD, 200).isEmpty());
        assertTrue(AtmosphereSyncCache.at(Level.NETHER, 64).isEmpty(),
                "another dimension stays unknown");
    }

    @Test
    void emptyPayloadMarksDimensionKnownButEmpty() {
        AtmosphereSyncCache.accept(new AtmosphereSyncPayload(Level.OVERWORLD, List.of()));

        assertTrue(AtmosphereSyncCache.knows(Level.OVERWORLD),
                "an empty payload still overrides a stale entry");
        assertTrue(AtmosphereSyncCache.at(Level.OVERWORLD, 64).isEmpty());
    }

    @Test
    void laterPayloadReplacesEarlierBands() {
        AtmosphereSyncCache.accept(new AtmosphereSyncPayload(Level.OVERWORLD, List.of(
                new AtmosphereSyncPayload.Band(0, 64, LOWER))));
        AtmosphereSyncCache.accept(new AtmosphereSyncPayload(Level.OVERWORLD, List.of(
                new AtmosphereSyncPayload.Band(0, 128, UPPER))));

        assertEquals(Optional.of(UPPER), AtmosphereSyncCache.at(Level.OVERWORLD, 63));
        assertEquals(Optional.of(UPPER), AtmosphereSyncCache.at(Level.OVERWORLD, 127));
    }

    @Test
    void clearForgetsEveryDimension() {
        AtmosphereSyncCache.accept(new AtmosphereSyncPayload(Level.OVERWORLD, List.of(
                new AtmosphereSyncPayload.Band(0, 64, LOWER))));

        AtmosphereSyncCache.clear();

        assertFalse(AtmosphereSyncCache.knows(Level.OVERWORLD));
        assertTrue(AtmosphereSyncCache.at(Level.OVERWORLD, 10).isEmpty());
    }
}
