package com.kuronami.isekaiapi.biomesource;

import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link BiomeSource} that assigns biomes by evaluating a list of {@link BiomeZone}
 * rules in order — the first zone whose condition matches the sample position wins, and a
 * {@code fallback} biome covers everything no rule claims. Registered into
 * {@code BuiltInRegistries.BIOME_SOURCE} as {@code isekai_api:rule}, so datapacks reference
 * it from a dimension's {@code biome_source} field.
 *
 * <p>This is the neutral, composable answer to "where do biomes go" — the biome-placement
 * analogue of Isekai's density-function primitives. Vertical layering, concentric rings,
 * region splits, and single-biome worlds are all expressible without writing a custom
 * Java {@code BiomeSource} per world.
 *
 * <p>JSON shape:
 * <pre>{@code
 * "biome_source": {
 *   "type": "isekai_api:rule",
 *   "fallback": "minecraft:plains",
 *   "rules": [
 *     { "zone": { "type": "isekai:y_below", "y": 20 }, "biome": "minecraft:deep_dark" },
 *     { "zone": { "type": "isekai:within_distance", "radius": 1000 }, "biome": "minecraft:desert" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Evaluation is pure (position → biome) at the rule layer: the rules are tried in order and
 * the first match wins, with no randomness of its own. Randomness lives in the zones, and the
 * zones get it from the world seed — see {@link #bindWorldSeed(long)}.
 */
@ApiStatus.Internal
public class RuleBiomeSource extends BiomeSource {

    /** One (condition → biome) entry. */
    public record Rule(BiomeZone zone, Holder<Biome> biome) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                BiomeZone.CODEC.fieldOf("zone").forGetter(Rule::zone),
                Biome.CODEC.fieldOf("biome").forGetter(Rule::biome)
        ).apply(i, Rule::new));
    }

    public static final MapCodec<RuleBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("fallback").forGetter(s -> s.fallback),
            Rule.CODEC.listOf().fieldOf("rules").forGetter(s -> s.rules)
    ).apply(i, RuleBiomeSource::new));

    private final Holder<Biome> fallback;
    private final List<Rule> rules;

    /**
     * The rule list actually evaluated: {@link #rules} with every zone bound to this level's
     * world seed. Starts as the unbound list so a source that never sees {@link #bindWorldSeed}
     * (a dimension created outside the normal server level lifecycle) still generates rather than
     * failing. Written once per level load, read from every worldgen worker — volatile for safe
     * publication of the freshly built zone tree.
     */
    private volatile List<Rule> activeRules;
    private volatile boolean bound;

    public RuleBiomeSource(Holder<Biome> fallback, List<Rule> rules) {
        this.fallback = fallback;
        this.rules = List.copyOf(rules);
        this.activeRules = this.rules;
    }

    /**
     * Rebuild every rule's zone against {@code worldSeed}, so noise-backed zones draw a pattern
     * unique to this world. Called once per level from the level-load hook, before any chunk is
     * generated; idempotent, and re-binding always re-derives from the datapack's own {@code seed}
     * fields rather than from the previously derived values.
     */
    public void bindWorldSeed(long worldSeed) {
        this.activeRules = rules.stream()
                .map(r -> new Rule(r.zone().withWorldSeed(worldSeed), r.biome()))
                .toList();
        this.bound = true;
    }

    /**
     * Whether {@link #bindWorldSeed} has run. Read by the server-started sweep, which warns about
     * any source that slipped past the level-load hook and would therefore draw one shared
     * pattern for every player.
     */
    public boolean isBound() {
        return bound;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        // The possible-biome set drives feature/structure eligibility and the locate command;
        // it must include the fallback plus every biome any rule can emit.
        return Stream.concat(Stream.of(fallback), rules.stream().map(Rule::biome));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return IsekaiBiomeSources.RULE.get();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        for (Rule rule : activeRules) {
            if (rule.zone().test(quartX, quartY, quartZ)) {
                return rule.biome();
            }
        }
        return fallback;
    }
}
