package com.kuronami.isekaiapi.mixin;

import com.kuronami.isekaiapi.client.IsekaiClientAtmosphere;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Applies a worldshape's {@code client_atmosphere.sky_color} to the vanilla sky-colour path.
 *
 * <p>Vanilla derives the sky colour by gaussian-sampling the sky colour of the biomes around
 * the camera, then applies the day/night factor, rain and thunder desaturation and the
 * lightning flash. A biome's sky colour is shared by every dimension that uses that biome, so a
 * dimension-wide override cannot be expressed through {@code AtmosphereOverride}. This
 * substitutes only the sampled base colour; every later step stays vanilla's.
 *
 * <p>Only loaded on the client: the class is listed under {@code client} in
 * {@code isekai.mixins.json}.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyColorMixin {

    @Redirect(method = "getSkyColor",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 isekai$overrideSkySample(Vec3 samplePos, CubicSampler.Vec3Fetcher fetcher) {
        ClientLevel level = (ClientLevel) (Object) this;
        // getSkyColor passes pos.subtract(2,2,2).scale(0.25); invert it to recover the camera Y
        // so bands resolve on the same Y as the fog hooks (RenderFog uses the camera position).
        double cameraY = samplePos.y() * 4.0 + 2.0;
        Integer rgb = IsekaiClientAtmosphere.activeSkyColor(level.dimension(), cameraY);
        if (rgb != null) {
            return Vec3.fromRGB24(rgb);
        }
        return CubicSampler.gaussianSampleVec3(samplePos, fetcher);
    }
}
