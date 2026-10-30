package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Dimension-wide client-side rendering overrides — sky colour, fog colour and fog distance
 * band — that apply on top of any biome-level {@link AtmosphereOverride}. Where {@link AtmosphereOverride}
 * tunes per-biome {@code BiomeSpecialEffects}, this record tunes the values rendered
 * dimension-wide regardless of which biome the camera is in.
 *
 * <p>Hooked into vanilla rendering through NeoForge's
 * {@code ViewportEvent.ComputeFogColor} (fog colour),
 * {@code ViewportEvent.RenderFog} (fog near / far distance) and a {@code ClientLevel}
 * mixin for the sky colour. When the camera enters a dimension whose worldshape declares
 * non-empty values here, the corresponding render call is overridden with the worldshape's
 * value; absent fields preserve vanilla / biome behaviour. Layered worlds resolve per
 * camera Y via {@link IsekaiRemap#getDescriptorAt(net.minecraft.resources.ResourceKey, int)}.
 *
 * <p>{@link #skyColor()} substitutes the biome colour sampled by
 * {@code ClientLevel#getSkyColor}; vanilla's day/night, rain/thunder and lightning-flash shading
 * still run on top of it. It applies to dimensions whose {@code effects} is
 * {@code minecraft:overworld} — the vanilla sky path — and is ignored by {@code the_end} /
 * {@code the_nether} effects, which render their own sky.
 *
 * <p>Notes / known limits:
 * <ul>
 *   <li><b>Sky colour</b> is also biome-driven by default; this field replaces the
 *       dimension-wide sky colour and takes precedence over the per-biome value while the
 *       descriptor is active. Use {@link AtmosphereOverride#skyColor()} when the colour
 *       must differ per biome inside one dimension.</li>
 *   <li><b>Cloud rendering / cloud height</b> is set on the vanilla {@code DimensionType}'s
 *       {@code effects} field; choose {@code minecraft:overworld} / {@code minecraft:end} /
 *       {@code minecraft:nether} when authoring the dimension_type JSON.</li>
 *   <li><b>Custom skybox / sun / moon / weather particles</b> require a
 *       {@code DimensionSpecialEffects} subclass + client mixins and are not covered here.</li>
 * </ul>
 *
 * <p>Vanilla colour encoding: 24-bit RGB packed as a single int (0xRRGGBB).
 * Fog distances are in blocks (vanilla overworld is roughly near=0, far=192 at clear weather).
 * @since 1.1.0
 */
public record ClientAtmosphereOverride(
        Optional<Integer> skyColor,
        Optional<Integer> fogColor,
        Optional<Float> fogNearDistance,
        Optional<Float> fogFarDistance
) {

    public static final ClientAtmosphereOverride EMPTY = new ClientAtmosphereOverride(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<ClientAtmosphereOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("sky_color").forGetter(ClientAtmosphereOverride::skyColor),
            Codec.INT.optionalFieldOf("fog_color").forGetter(ClientAtmosphereOverride::fogColor),
            Codec.FLOAT.optionalFieldOf("fog_near_distance").forGetter(ClientAtmosphereOverride::fogNearDistance),
            Codec.FLOAT.optionalFieldOf("fog_far_distance").forGetter(ClientAtmosphereOverride::fogFarDistance)
    ).apply(i, ClientAtmosphereOverride::new));

    /** {@code true} when no sky or fog override is set — rendering is left to vanilla. @since 1.1.0 */
    public boolean isNoOp() {
        return skyColor.isEmpty() && fogColor.isEmpty()
                && fogNearDistance.isEmpty() && fogFarDistance.isEmpty();
    }
}
