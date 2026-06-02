package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.AmbientAdditionsSettings;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
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
 *   <li>Hell World — fog color red, sky color black, ambient particles for ash drift</li>
 *   <li>Crystal Cave — water/foliage color to crystal hues, ambient loop for resonance</li>
 *   <li>Custom mood — override music, mood/additions sound for a distinct sonic identity</li>
 * </ul>
 *
 * <p>Vanilla color encoding: 24-bit RGB packed as a single int (0xRRGGBB).
 *
 * <p>{@code hasPrecipitation} controls whether rain/snow/storm effects show; biomes with
 * downfall {@code > 0.85} count as humid and inhibit fire spread regardless of weather.
 *
 * <p>The {@code effects_extras} sub-object groups the grass-colour algorithm + ambient
 * particle / audio / music overrides — every {@code BiomeSpecialEffects} field not already
 * exposed at the top level. Nested to keep the codec under Mojang DataFixerUpper's 16-field
 * group limit; top-level fields are unchanged from earlier API versions.
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
        EffectsExtras effectsExtras,
        Optional<Float> creatureGenerationProbability,
        Map<EntityType<?>, MobSpawnSettings.MobSpawnCost> mobSpawnCosts
) {
    public AtmosphereOverride {
        mobSpawnCosts = Map.copyOf(mobSpawnCosts);
    }

    /**
     * Grass-colour algorithm plus ambient particle / audio / music overrides — the
     * remaining {@code BiomeSpecialEffects} fields not at {@link AtmosphereOverride}'s top
     * level. Nested to fit the codec field-group limit; conceptually all per-biome
     * sensory overrides.
     */
    public record EffectsExtras(
            Optional<BiomeSpecialEffects.GrassColorModifier> grassColorModifier,
            Optional<AmbientParticleSettings> ambientParticle,
            Optional<Holder<SoundEvent>> ambientLoopSound,
            Optional<AmbientMoodSettings> ambientMoodSound,
            Optional<AmbientAdditionsSettings> ambientAdditionsSound,
            Optional<Music> backgroundMusic
    ) {
        public static final EffectsExtras EMPTY = new EffectsExtras(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());

        public static final Codec<EffectsExtras> CODEC = RecordCodecBuilder.create(i -> i.group(
                BiomeSpecialEffects.GrassColorModifier.CODEC.optionalFieldOf("grass_color_modifier")
                        .forGetter(EffectsExtras::grassColorModifier),
                AmbientParticleSettings.CODEC.optionalFieldOf("particle").forGetter(EffectsExtras::ambientParticle),
                SoundEvent.CODEC.optionalFieldOf("ambient_sound").forGetter(EffectsExtras::ambientLoopSound),
                AmbientMoodSettings.CODEC.optionalFieldOf("mood_sound").forGetter(EffectsExtras::ambientMoodSound),
                AmbientAdditionsSettings.CODEC.optionalFieldOf("additions_sound").forGetter(EffectsExtras::ambientAdditionsSound),
                Music.CODEC.optionalFieldOf("music").forGetter(EffectsExtras::backgroundMusic)
        ).apply(i, EffectsExtras::new));

        public boolean isNoOp() {
            return grassColorModifier.isEmpty() && ambientParticle.isEmpty()
                    && ambientLoopSound.isEmpty() && ambientMoodSound.isEmpty()
                    && ambientAdditionsSound.isEmpty() && backgroundMusic.isEmpty();
        }
    }

    public static final AtmosphereOverride EMPTY = new AtmosphereOverride(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(),
            EffectsExtras.EMPTY,
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
            EffectsExtras.CODEC.optionalFieldOf("effects_extras", EffectsExtras.EMPTY)
                    .forGetter(AtmosphereOverride::effectsExtras),
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
                && effectsExtras.isNoOp()
                && creatureGenerationProbability.isEmpty()
                && mobSpawnCosts.isEmpty();
    }
}
