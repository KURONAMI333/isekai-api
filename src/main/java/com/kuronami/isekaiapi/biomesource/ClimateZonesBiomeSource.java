package com.kuronami.isekaiapi.biomesource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link BiomeSource} that assigns biomes by matching the vanilla climate axes
 * (temperature / humidity / continentalness / erosion / weirdness / depth) against
 * per-rule range constraints. Rules are evaluated in declaration order; the first whose
 * constraint contains the sample wins, and a {@code fallback} biome covers everything
 * unmatched.
 *
 * <p>Same purpose as vanilla's {@code minecraft:multi_noise} biome source but with a
 * compact JSON: each rule lists only the axes it cares about (the rest default to the
 * full {@code [-1, 1]} range), and ordering is explicit instead of vanilla's nearest-point
 * matching. Suitable for "Overworld-style" multi-biome worlds without hand-writing 6-axis
 * + offset boilerplate per biome.
 *
 * <p>Neutral: axis names are the vanilla {@link Climate} axes — no theme slots ("tropical",
 * "alpine", etc.) baked in. A consumer can build their own slot vocabulary on top.
 *
 * <p>Registered as {@code isekai_api:climate_zones}. JSON shape:
 *
 * <pre>{@code
 * "biome_source": {
 *   "type": "isekai_api:climate_zones",
 *   "fallback": "minecraft:plains",
 *   "rules": [
 *     { "biome": "minecraft:warm_ocean",   "continentalness": [-1.0,  0.05] },
 *     { "biome": "minecraft:desert",       "temperature":     [0.55,  1.0], "humidity": [-1.0, -0.1] },
 *     { "biome": "minecraft:jungle",       "temperature":     [0.55,  1.0], "humidity": [ 0.1,  1.0] },
 *     { "biome": "minecraft:plains" }
 *   ]
 * }
 * }</pre>
 *
 * <p>An axis omitted from a rule = no constraint on that axis. Ranges use the vanilla
 * {@link Climate.Parameter} codec — either {@code [min, max]} or a single value.
 */
@ApiStatus.Internal
public class ClimateZonesBiomeSource extends BiomeSource {

    /** Vanilla parameter representing "no constraint": the full sampled range, {@code [-1, 1]}. */
    private static final Climate.Parameter UNCONSTRAINED = Climate.Parameter.span(-1.0F, 1.0F);

    public record Rule(
            Holder<Biome> biome,
            Optional<Climate.Parameter> temperature,
            Optional<Climate.Parameter> humidity,
            Optional<Climate.Parameter> continentalness,
            Optional<Climate.Parameter> erosion,
            Optional<Climate.Parameter> weirdness,
            Optional<Climate.Parameter> depth) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Biome.CODEC.fieldOf("biome").forGetter(Rule::biome),
                Climate.Parameter.CODEC.optionalFieldOf("temperature").forGetter(Rule::temperature),
                Climate.Parameter.CODEC.optionalFieldOf("humidity").forGetter(Rule::humidity),
                Climate.Parameter.CODEC.optionalFieldOf("continentalness").forGetter(Rule::continentalness),
                Climate.Parameter.CODEC.optionalFieldOf("erosion").forGetter(Rule::erosion),
                Climate.Parameter.CODEC.optionalFieldOf("weirdness").forGetter(Rule::weirdness),
                Climate.Parameter.CODEC.optionalFieldOf("depth").forGetter(Rule::depth)
        ).apply(i, Rule::new));

        /** True when the sample falls inside every axis this rule constrains. */
        boolean matches(Climate.TargetPoint p) {
            return contains(temperature, p.temperature())
                    && contains(humidity, p.humidity())
                    && contains(continentalness, p.continentalness())
                    && contains(erosion, p.erosion())
                    && contains(weirdness, p.weirdness())
                    && contains(depth, p.depth());
        }

        private static boolean contains(Optional<Climate.Parameter> opt, long value) {
            Climate.Parameter p = opt.orElse(UNCONSTRAINED);
            return value >= p.min() && value <= p.max();
        }
    }

    public static final MapCodec<ClimateZonesBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("fallback").forGetter(s -> s.fallback),
            Rule.CODEC.listOf().fieldOf("rules").forGetter(s -> s.rules)
    ).apply(i, ClimateZonesBiomeSource::new));

    private final Holder<Biome> fallback;
    private final List<Rule> rules;

    public ClimateZonesBiomeSource(Holder<Biome> fallback, List<Rule> rules) {
        this.fallback = fallback;
        this.rules = List.copyOf(rules);
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(Stream.of(fallback), rules.stream().map(Rule::biome));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return IsekaiBiomeSources.CLIMATE_ZONES.get();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Climate.TargetPoint sample = sampler.sample(x, y, z);
        for (Rule r : rules) {
            if (r.matches(sample)) return r.biome();
        }
        return fallback;
    }
}
