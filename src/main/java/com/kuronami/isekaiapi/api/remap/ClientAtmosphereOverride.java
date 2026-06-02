package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Dimension-wide client-side rendering overrides — fog colour and fog distance band — that
 * apply on top of any biome-level {@link AtmosphereOverride}. Where {@link AtmosphereOverride}
 * tunes per-biome {@code BiomeSpecialEffects}, this record tunes the values rendered
 * dimension-wide regardless of which biome the camera is in.
 *
 * <p>Hooked into vanilla rendering through NeoForge's
 * {@code ViewportEvent.ComputeFogColor} (fog colour) and
 * {@code ViewportEvent.RenderFog} (fog near / far distance). When the camera enters a
 * dimension whose worldshape declares non-empty values here, the corresponding render call
 * is overridden with the worldshape's value; absent fields preserve vanilla / biome
 * behaviour. Layered worlds resolve per camera Y via
 * {@link IsekaiRemap#getDescriptorAt(net.minecraft.resources.ResourceKey, int)}.
 *
 * <p>Notes / known limits (this record is intentionally minimal — only what NeoForge events
 * cover cleanly today):
 * <ul>
 *   <li><b>Sky colour</b> is biome-driven; use {@link AtmosphereOverride#skyColor()} on the
 *       per-biome atmosphere instead (or override every biome in the worldshape).
 *       Overriding sky colour dimension-wide would need a {@code ClientLevel} mixin and is
 *       intentionally out of scope for the current cut.</li>
 *   <li><b>Cloud rendering / cloud height</b> is set on the vanilla {@code DimensionType}'s
 *       {@code effects} field; choose {@code minecraft:overworld} / {@code minecraft:end} /
 *       {@code minecraft:nether} when authoring the dimension_type JSON.</li>
 *   <li><b>Custom skybox / sun / moon / weather particles</b> require a
 *       {@code DimensionSpecialEffects} subclass + client mixins and are not covered here.</li>
 * </ul>
 *
 * <p>Vanilla colour encoding: 24-bit RGB packed as a single int (0xRRGGBB).
 * Fog distances are in blocks (vanilla overworld is roughly near=0, far=192 at clear weather).
 */
public record ClientAtmosphereOverride(
        Optional<Integer> fogColor,
        Optional<Float> fogNearDistance,
        Optional<Float> fogFarDistance
) {

    public static final ClientAtmosphereOverride EMPTY = new ClientAtmosphereOverride(
            Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<ClientAtmosphereOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("fog_color").forGetter(ClientAtmosphereOverride::fogColor),
            Codec.FLOAT.optionalFieldOf("fog_near_distance").forGetter(ClientAtmosphereOverride::fogNearDistance),
            Codec.FLOAT.optionalFieldOf("fog_far_distance").forGetter(ClientAtmosphereOverride::fogFarDistance)
    ).apply(i, ClientAtmosphereOverride::new));

    public boolean isNoOp() {
        return fogColor.isEmpty() && fogNearDistance.isEmpty() && fogFarDistance.isEmpty();
    }
}
