package com.kuronami.isekaiapi.biomesource;

import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.kuronami.isekaiapi.api.biomesource.ZoneContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link BiomeSource} that wraps another source and replaces the biome inside
 * {@link BiomeZone} footprints, leaving every other position to the wrapped source. Registered
 * into {@code BuiltInRegistries.BIOME_SOURCE} as {@code isekai_api:override}, so a datapack can
 * put a forest patch, a jungle island, or one biome's ring into an otherwise vanilla terrain.
 *
 * <p>Unlike {@code isekai_api:rule}, which owns the whole biome map and needs a fallback, this
 * delegates: any position no zone claims is answered by {@code base}, so the base source's own
 * rules, seed, and climate sampling keep working untouched.
 *
 * <p>JSON shape:
 * <pre>{@code
 * "biome_source": {
 *   "type": "isekai_api:override",
 *   "base": { "type": "minecraft:multi_noise", "preset": "minecraft:overworld" },
 *   "overrides": [
 *     { "zone": { "type": "isekai_api:chunk_box",
 *                 "min_chunk_x": -3, "max_chunk_x": 3,
 *                 "min_chunk_z": -3, "max_chunk_z": 3 },
 *       "biome": "minecraft:jungle" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Biome assignment happens before terrain, so an override changes the surface biome — surface
 * blocks, vegetation, structure and feature eligibility, spawns, sky/fog colour — not the terrain
 * shape the base generator's climate noise produced. An area under an ocean stays ocean-shaped
 * even when its biome becomes jungle. Overrides also only affect newly generated chunks.
 *
 * <p>Zones are evaluated in declaration order and the first match wins; the first entry that
 * needs a tighter priority goes at the top. Noise-backed zones receive the world seed through
 * {@link #bindWorldSeed(long)}, which also forwards it to a wrapped rule/override source so a
 * nested stack is seeded all the way down.
 */
@ApiStatus.Internal
public class OverrideBiomeSource extends BiomeSource {

    /** One (footprint → replacement biome) entry. */
    public record Entry(BiomeZone zone, Holder<Biome> biome) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                BiomeZone.CODEC.fieldOf("zone").forGetter(Entry::zone),
                Biome.CODEC.fieldOf("biome").forGetter(Entry::biome)
        ).apply(i, Entry::new));
    }

    public static final MapCodec<OverrideBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("base").forGetter(s -> s.base),
            Entry.CODEC.listOf().fieldOf("overrides").forGetter(s -> s.overrides)
    ).apply(i, OverrideBiomeSource::new));

    private final BiomeSource base;
    private final List<Entry> overrides;

    /**
     * The entry list actually evaluated: {@link #overrides} with every zone bound to this level's
     * world seed. Starts unbound so a source that never sees {@link #bindWorldSeed} still
     * generates with the seed-0 pattern rather than failing. Volatile for safe publication to the
     * worldgen workers that read it.
     */
    private volatile List<Entry> activeOverrides;
    private volatile boolean bound;

    public OverrideBiomeSource(BiomeSource base, List<Entry> overrides) {
        this.base = base;
        this.overrides = List.copyOf(overrides);
        this.activeOverrides = this.overrides;
    }

    /**
     * Rebuild every override's zone against {@code worldSeed}, and forward the seed to the wrapped
     * source when it is one of Isekai's own sources. Idempotent, and re-binding always re-derives
     * from the datapack's {@code seed} fields rather than from previously derived values.
     */
    public void bindWorldSeed(long worldSeed) {
        this.activeOverrides = overrides.stream()
                .map(e -> new Entry(e.zone().withWorldSeed(worldSeed), e.biome()))
                .toList();
        this.bound = true;
        if (base instanceof RuleBiomeSource rule) {
            rule.bindWorldSeed(worldSeed);
        } else if (base instanceof OverrideBiomeSource nested) {
            nested.bindWorldSeed(worldSeed);
        }
    }

    /**
     * Whether this source and any Isekai source it wraps have seen {@link #bindWorldSeed}. Read by
     * the server-started sweep, which warns about sources that slipped past the level-load hook.
     */
    public boolean isBound() {
        if (!bound) return false;
        if (base instanceof RuleBiomeSource rule) return rule.isBound();
        if (base instanceof OverrideBiomeSource nested) return nested.isBound();
        return true;
    }

    public BiomeSource base() {
        return base;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        // Feature/structure eligibility and the locate command read this set, so it must include
        // everything the base can produce plus every replacement biome.
        return Stream.concat(base.possibleBiomes().stream(), overrides.stream().map(Entry::biome));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return IsekaiBiomeSources.OVERRIDE.get();
    }

    /**
     * Forward the F3 lines to the wrapped source, so a wrapped {@code minecraft:multi_noise} keeps
     * showing its climate values (and the override is transparent on the debug screen too).
     */
    @Override
    public void addDebugInfo(List<String> info, BlockPos pos, Climate.Sampler sampler) {
        base.addDebugInfo(info, pos, sampler);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        // One context per call: worldgen samples on several workers at once, so no field.
        ZoneContext ctx = new ZoneContext(quartX, quartY, quartZ,
                (qx, qy, qz) -> base.getNoiseBiome(qx, qy, qz, sampler), sampler);
        for (Entry entry : activeOverrides) {
            if (entry.zone().test(quartX, quartY, quartZ, ctx)) {
                return entry.biome();
            }
        }
        // A zone such as base_biome may already have asked the base at this exact position;
        // reuse that answer instead of sampling it a second time.
        return ctx.cachedBaseBiome()
                .orElseGet(() -> base.getNoiseBiome(quartX, quartY, quartZ, sampler));
    }
}
