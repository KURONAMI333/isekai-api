package com.kuronami.isekaiapi.client;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.remap.ClientAtmosphereOverride;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Applies the active worldshape's {@link ClientAtmosphereOverride} to vanilla rendering via
 * NeoForge's {@link ViewportEvent} hooks. Runs only on the client (the
 * {@link Dist#CLIENT}-annotated subscriber means this class is never loaded on a dedicated
 * server, so no Minecraft / GL references leak into server runtime).
 *
 * <p>For each fog event we read the active descriptor at the camera's current Y (so layered
 * worlds get per-band fog), and apply any non-empty fields:
 *
 * <ul>
 *   <li>{@link ClientAtmosphereOverride#fogColor()} overrides the rendered fog colour.</li>
 *   <li>{@link ClientAtmosphereOverride#fogNearDistance()} sets the start of the fog
 *       gradient (camera-near plane).</li>
 *   <li>{@link ClientAtmosphereOverride#fogFarDistance()} sets the end of the fog gradient
 *       (camera-far plane).</li>
 * </ul>
 *
 * <p>Fields not set on the descriptor leave vanilla / biome behaviour untouched.
 */
@ApiStatus.Internal
@EventBusSubscriber(modid = IsekaiApi.MODID, value = Dist.CLIENT)
public final class IsekaiClientAtmosphere {

    private IsekaiClientAtmosphere() {}

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        ClientAtmosphereOverride client = activeFor(event.getCamera().getPosition().y);
        if (client == null) return;
        client.fogColor().ifPresent(rgb -> {
            event.setRed(((rgb >> 16) & 0xFF) / 255.0f);
            event.setGreen(((rgb >> 8) & 0xFF) / 255.0f);
            event.setBlue((rgb & 0xFF) / 255.0f);
        });
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        ClientAtmosphereOverride client = activeFor(event.getCamera().getPosition().y);
        if (client == null) return;
        client.fogNearDistance().ifPresent(event::setNearPlaneDistance);
        client.fogFarDistance().ifPresent(event::setFarPlaneDistance);
    }

    /**
     * Resolve the {@link ClientAtmosphereOverride} active at the camera's Y. Returns
     * {@code null} when there's no world, no declared descriptor at that Y, or the
     * descriptor has no client-atmosphere overrides set — letting the caller short-circuit.
     */
    private static ClientAtmosphereOverride activeFor(double cameraY) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        int y = (int) Math.floor(cameraY);
        WorldshapeDescriptor desc = Isekai.remap().getDescriptorAt(level.dimension(), y).orElse(null);
        if (desc == null) return null;
        ClientAtmosphereOverride client = desc.clientAtmosphere();
        return client.isNoOp() ? null : client;
    }
}
