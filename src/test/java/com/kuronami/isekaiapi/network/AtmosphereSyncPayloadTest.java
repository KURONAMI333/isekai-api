package com.kuronami.isekaiapi.network;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.ClientAtmosphereOverride;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.TransitionRule;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.IsekaiRemapImpl;
import com.kuronami.isekaiapi.impl.LayerResolver;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire contract and band derivation for {@link AtmosphereSyncPayload}: every optional field
 * survives the codec, and the server sends the same Y intervals
 * {@link LayerResolver#resolveByY} resolves locally.
 */
class AtmosphereSyncPayloadTest {

    private static final ClientAtmosphereOverride UPPER_ATMOSPHERE = new ClientAtmosphereOverride(
            Optional.of(0x112233), Optional.empty(), Optional.of(0.5f), Optional.empty());
    private static final ClientAtmosphereOverride LOWER_ATMOSPHERE = new ClientAtmosphereOverride(
            Optional.empty(), Optional.of(0x445566), Optional.empty(), Optional.of(96.0f));

    @AfterEach
    void clearCache() {
        AtmosphereSyncCache.clear();
    }

    private static WorldshapeDescriptor descriptor(VerticalRange range,
                                                   ClientAtmosphereOverride atmosphere) {
        return WorldshapeDescriptor.builder()
                .dimension(Level.OVERWORLD)
                .playableRange(range)
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .clientAtmosphere(atmosphere)
                .build();
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    @Test
    void codecRoundTripsAllFields() {
        AtmosphereSyncPayload payload = new AtmosphereSyncPayload(Level.OVERWORLD, List.of(
                new AtmosphereSyncPayload.Band(-64, 0, UPPER_ATMOSPHERE),
                new AtmosphereSyncPayload.Band(0, 320, LOWER_ATMOSPHERE)));
        FriendlyByteBuf buf = buffer();

        AtmosphereSyncPayload.STREAM_CODEC.encode(buf, payload);

        assertEquals(payload, AtmosphereSyncPayload.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes(), "codec must consume exactly what it wrote");
    }

    @Test
    void codecRoundTripsEmptyBands() {
        AtmosphereSyncPayload payload = new AtmosphereSyncPayload(Level.OVERWORLD, List.of());
        FriendlyByteBuf buf = buffer();

        AtmosphereSyncPayload.STREAM_CODEC.encode(buf, payload);

        assertEquals(payload, AtmosphereSyncPayload.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void codecRejectsNegativeBandCount() {
        FriendlyByteBuf buf = buffer();
        buf.writeResourceLocation(Level.OVERWORLD.location());
        buf.writeVarInt(-1);

        assertThrows(DecoderException.class, () -> AtmosphereSyncPayload.STREAM_CODEC.decode(buf));
    }

    @Test
    void singleLayerSendsOneFullRangeBand() {
        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(descriptor(
                new VerticalRange(0, 256, HeightDistribution.UNIFORM), UPPER_ATMOSPHERE));

        AtmosphereSyncPayload payload = AtmosphereSyncPayload.of(Level.OVERWORLD, remap);

        assertEquals(Level.OVERWORLD, payload.dimension());
        assertEquals(List.of(new AtmosphereSyncPayload.Band(
                Integer.MIN_VALUE, Integer.MAX_VALUE, UPPER_ATMOSPHERE)), payload.bands());
    }

    @Test
    void singleLayerWithoutAtmosphereSendsNoBands() {
        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareWorldshape(descriptor(
                new VerticalRange(0, 256, HeightDistribution.UNIFORM),
                ClientAtmosphereOverride.EMPTY));

        assertTrue(AtmosphereSyncPayload.of(Level.OVERWORLD, remap).bands().isEmpty());
    }

    @Test
    void layeredBandsMatchLocalYOnlyResolution() {
        IsekaiRemapImpl remap = new IsekaiRemapImpl();
        remap.declareLayeredWorldshape(Level.OVERWORLD, List.of(
                new LayeredDescriptor(new VerticalRange(0, 64, HeightDistribution.UNIFORM),
                        descriptor(new VerticalRange(0, 64, HeightDistribution.UNIFORM),
                                LOWER_ATMOSPHERE),
                        new TransitionRule.Gap(3)),
                new LayeredDescriptor(new VerticalRange(64, 128, HeightDistribution.UNIFORM),
                        descriptor(new VerticalRange(64, 128, HeightDistribution.UNIFORM),
                                UPPER_ATMOSPHERE),
                        new TransitionRule.Hard())));

        AtmosphereSyncPayload payload = AtmosphereSyncPayload.of(Level.OVERWORLD, remap);

        // Gap(3) removes the top three blocks of the lower layer, exactly as a Y-only lookup.
        assertEquals(List.of(
                new AtmosphereSyncPayload.Band(0, 61, LOWER_ATMOSPHERE),
                new AtmosphereSyncPayload.Band(64, 128, UPPER_ATMOSPHERE)), payload.bands());

        AtmosphereSyncCache.accept(payload);
        List<LayeredDescriptor> layers = remap.getActiveLayers(Level.OVERWORLD);
        for (int y = -2; y < 131; y++) {
            ClientAtmosphereOverride expected = LayerResolver.resolveByY(layers, y)
                    .map(WorldshapeDescriptor::clientAtmosphere)
                    .filter(atmosphere -> !atmosphere.isNoOp())
                    .orElse(null);
            assertEquals(Optional.ofNullable(expected), AtmosphereSyncCache.at(Level.OVERWORLD, y),
                    "cache must resolve Y=" + y + " exactly like the local registry");
        }
    }
}
