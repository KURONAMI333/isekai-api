package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.util.Map;
import java.util.Optional;

/**
 * Optional atmospheric / visual / climate overrides for biomes matched by a
 * {@link WorldshapeDescriptor}. Each field is independently optional — leaving a field
 * empty means "keep the biome's current value". The biome modifier MODIFY phase applies
 * any set values to the builder's {@code ClimateSettings} and {@code BiomeSpecialEffects}.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Sky World — override sky/fog color to make the void below visible</li>
 *   <li>Submerged World — temperature 0 + downfall 1 for permanent rain</li>
 *   <li>Hell World — fog color red, sky color black</li>
 *   <li>Crystal Cave — water/foliage color to crystal hues</li>
 * </ul>
 *
 * <p>Vanilla color encoding: 24-bit RGB packed as a single int (0xRRGGBB).
 *
 * <p>{@code hasPrecipitation} controls whether rain/snow/storm effects show; biomes with
 * downfall {@code > 0.85} count as humid and inhibit fire spread regardless of weather.
 */
public record AtmosphereOverride(
        Optional<Boolean> hasPrecipitation,
        Optional<Float> temperature,
        Optional<Float> downfall,
        Optional<Integer> skyColor,
        Optional<Integer> fogColor,
        Optional<Integer> waterColor,
        Optional<Integer> waterFogColor,
        Optional<Integer> foliageColor,
        Optional<Integer> grassColor,
        Optional<Float> creatureGenerationProbability,
        Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts
) {
    public AtmosphereOverride {
        mobSpawnCosts = Map.copyOf(mobSpawnCosts);
    }

    public static final AtmosphereOverride EMPTY = new AtmosphereOverride(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Map.of());

    public static final Codec<AtmosphereOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("has_precipitation").forGetter(AtmosphereOverride::hasPrecipitation),
            // Vanilla biome temperature is unbounded (snow biomes negative, nether ≥2 to suppress rain).
            Codec.FLOAT.optionalFieldOf("temperature").forGetter(AtmosphereOverride::temperature),
            // Downfall is normalised to [0,1] by vanilla (0 = none, 1 = max); clamped at decode time.
            Codec.floatRange(0f, 1f).optionalFieldOf("downfall").forGetter(AtmosphereOverride::downfall),
            Codec.INT.optionalFieldOf("sky_color").forGetter(AtmosphereOverride::skyColor),
            Codec.INT.optionalFieldOf("fog_color").forGetter(AtmosphereOverride::fogColor),
            Codec.INT.optionalFieldOf("water_color").forGetter(AtmosphereOverride::waterColor),
            Codec.INT.optionalFieldOf("water_fog_color").forGetter(AtmosphereOverride::waterFogColor),
            Codec.INT.optionalFieldOf("foliage_color").forGetter(AtmosphereOverride::foliageColor),
            Codec.INT.optionalFieldOf("grass_color").forGetter(AtmosphereOverride::grassColor),
            Codec.floatRange(0f, 1f).optionalFieldOf("creature_generation_probability")
                    .forGetter(AtmosphereOverride::creatureGenerationProbability),
            Codec.unboundedMap(BuiltInRegistries.ENTITY_TYPE.byNameCodec(), MobSpawnSettings.MobSpawnCost.CODEC)
                    .optionalFieldOf("mob_spawn_costs", Map.of())
                    .forGetter(AtmosphereOverride::mobSpawnCosts)
    ).apply(i, AtmosphereOverride::new));

    /** {@code true} when every field is empty — biome's atmosphere stays untouched. */
    public boolean isNoOp() {
        return hasPrecipitation.isEmpty() && temperature.isEmpty() && downfall.isEmpty()
                && skyColor.isEmpty() && fogColor.isEmpty()
                && waterColor.isEmpty() && waterFogColor.isEmpty()
                && foliageColor.isEmpty() && grassColor.isEmpty()
                && creatureGenerationProbability.isEmpty()
                && mobSpawnCosts.isEmpty();
    }
}
