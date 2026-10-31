package com.kuronami.isekaiapi.network;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.ClientAtmosphereOverride;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.impl.LayerResolver;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-to-client copy of the {@code client_atmosphere} overrides that are active for the
 * player's current dimension.
 *
 * <p>A worldshape is declared from datapack JSON through a server-side reload listener, so a
 * client connected to a dedicated server has no declarations of its own and the fog / sky
 * hooks would see nothing. The server resolves what actually applies
 * ({@link IsekaiRemap#getActiveDescriptor} / {@link IsekaiRemap#getActiveLayers}) and sends
 * the result as Y bands; the client resolves the band under the camera exactly like
 * {@link LayerResolver#resolveByY(List, int)} resolves the local registry.
 *
 * <p>Every band carries the same {@link ClientAtmosphereOverride} the descriptor declares, and
 * only bands whose override is not a no-op are sent. An empty {@code bands} list means "this
 * dimension has no client atmosphere" — it still replaces a stale cache entry instead of
 * letting the previous payload linger.
 *
 * <p>The channel is registered as optional, so a client without Isekai API (vanilla or another
 * modpack) connects normally and simply renders vanilla colours.
 * @since 3.0.0
 */
@ApiStatus.Internal
public record AtmosphereSyncPayload(
        ResourceKey<Level> dimension,
        List<Band> bands
) implements CustomPacketPayload {

    /** Payload id. @since 3.0.0 */
    public static final Type<AtmosphereSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(IsekaiApi.MODID, "client_atmosphere"));

    /** Upper bound on decoded bands — a sanity limit, not a worldshape restriction. @since 3.0.0 */
    private static final int MAX_BANDS = 256;

    public AtmosphereSyncPayload {
        if (dimension == null) throw new IllegalArgumentException("dimension is required");
        bands = List.copyOf(bands);
    }

    @Override
    public Type<AtmosphereSyncPayload> type() {
        return TYPE;
    }

    /**
     * One half-open {@code [minY, maxY)} interval and the override that applies inside it.
     * A single-layer worldshape is sent as {@code [Integer.MIN_VALUE, Integer.MAX_VALUE)}.
     * @since 3.0.0
     */
    public record Band(int minY, int maxY, ClientAtmosphereOverride atmosphere) {
        public Band {
            if (minY >= maxY) {
                throw new IllegalArgumentException("empty band: " + minY + " >= " + maxY);
            }
            if (atmosphere == null) atmosphere = ClientAtmosphereOverride.EMPTY;
        }

        /** {@code true} when {@code y} falls inside this half-open band. @since 3.0.0 */
        public boolean contains(int y) {
            return y >= minY && y < maxY;
        }
    }

    /**
     * Build the payload for one dimension from the live remap registry. Uses layers when the
     * dimension is declared as a layered worldshape (matching
     * {@link IsekaiRemap#getDescriptorAt(ResourceKey, int)}), otherwise the single descriptor.
     *
     * <p>Bands are derived through {@link LayerResolver#yOnlyRange(LayeredDescriptor)}, so a
     * {@code Gap} transition leaves the same empty space on the client as in the local
     * registry. {@code Blend} is intentionally not resolved — Y-only lookups treat a blend as a
     * hard seam (see {@link LayerResolver#resolveByY}).
     * @since 3.0.0
     */
    public static AtmosphereSyncPayload of(ResourceKey<Level> dimension, IsekaiRemap remap) {
        List<LayeredDescriptor> layers = remap.getActiveLayers(dimension);
        if (!layers.isEmpty()) {
            List<Band> bands = new ArrayList<>(layers.size());
            for (LayeredDescriptor layer : layers) {
                ClientAtmosphereOverride override = layer.descriptor().clientAtmosphere();
                if (override.isNoOp()) continue;
                VerticalRange range = LayerResolver.yOnlyRange(layer);
                if (range.minY() >= range.maxY()) continue;
                bands.add(new Band(range.minY(), range.maxY(), override));
            }
            return new AtmosphereSyncPayload(dimension, bands);
        }

        ClientAtmosphereOverride single = remap.getActiveDescriptor(dimension)
                .map(WorldshapeDescriptor::clientAtmosphere)
                .orElse(ClientAtmosphereOverride.EMPTY);
        if (single.isNoOp()) return new AtmosphereSyncPayload(dimension, List.of());
        return new AtmosphereSyncPayload(dimension,
                List.of(new Band(Integer.MIN_VALUE, Integer.MAX_VALUE, single)));
    }

    /** Wire format: dimension id, then each band's bounds and override. @since 3.0.0 */
    public static final StreamCodec<FriendlyByteBuf, AtmosphereSyncPayload> STREAM_CODEC =
            StreamCodec.of(AtmosphereSyncPayload::write, AtmosphereSyncPayload::read);

    private static void write(FriendlyByteBuf buf, AtmosphereSyncPayload payload) {
        buf.writeResourceLocation(payload.dimension().location());
        buf.writeVarInt(payload.bands().size());
        for (Band band : payload.bands()) {
            buf.writeInt(band.minY());
            buf.writeInt(band.maxY());
            writeOptionalInt(buf, band.atmosphere().skyColor());
            writeOptionalInt(buf, band.atmosphere().fogColor());
            writeOptionalFloat(buf, band.atmosphere().fogNearDistance());
            writeOptionalFloat(buf, band.atmosphere().fogFarDistance());
        }
    }

    private static AtmosphereSyncPayload read(FriendlyByteBuf buf) {
        ResourceKey<Level> dimension =
                ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_BANDS) {
            throw new DecoderException("client_atmosphere band count out of range: " + size);
        }
        List<Band> bands = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int minY = buf.readInt();
            int maxY = buf.readInt();
            bands.add(new Band(minY, maxY, new ClientAtmosphereOverride(
                    readOptionalInt(buf),
                    readOptionalInt(buf),
                    readOptionalFloat(buf),
                    readOptionalFloat(buf))));
        }
        return new AtmosphereSyncPayload(dimension, bands);
    }

    private static void writeOptionalInt(FriendlyByteBuf buf, Optional<Integer> value) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(buf::writeInt);
    }

    private static Optional<Integer> readOptionalInt(FriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(buf.readInt()) : Optional.empty();
    }

    private static void writeOptionalFloat(FriendlyByteBuf buf, Optional<Float> value) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(buf::writeFloat);
    }

    private static Optional<Float> readOptionalFloat(FriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(buf.readFloat()) : Optional.empty();
    }
}
