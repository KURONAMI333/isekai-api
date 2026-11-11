package com.kuronami.isekaiapi.api.biomesource;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import java.util.Optional;

/**
 * What a {@link BiomeZone} may read besides its own coordinate: the wrapped biome source's answer
 * and the vanilla climate sample at that position.
 *
 * <p>Most zones are pure geometry and never look here. The two that do —
 * {@link BiomeZone.BaseBiome} and {@link BiomeZone.ClimateMatch} — need state only a
 * {@code BiomeSource} can supply: the answer of the source being wrapped (a zone cannot ask it, or
 * "replace the biome inside this footprint" would be circular) and the climate field the level's
 * sampler produces. Rather than giving {@code BiomeZone} a reference to the source and threading
 * world state through the interface, the source builds one context per {@code getNoiseBiome} call
 * and hands it to {@link BiomeZone#test(int, int, int, ZoneContext)}.
 *
 * <p><b>No per-instance state.</b> Biome assignment runs on several worldgen workers at once, so a
 * source keeps no context field; every call gets its own. A context is therefore mutable and
 * single-threaded, and must not be kept past the call that made it.
 *
 * <p><b>Memoised.</b> The first base and the first climate query are remembered along with the
 * coordinate they were made at, and a repeated query at that same coordinate returns the stored
 * answer. This is what keeps a tree such as {@code and(base_biome, ...)} — or fifteen overrides
 * asking the same question — from sampling the wrapped source or the climate field once per
 * candidate. A query at another coordinate is computed but not stored, so the memo stays pinned to
 * the coordinate the source is deciding about.
 *
 * <p>Zones that are not handed a wrapped source ({@code isekai_api:rule} owns every position, so
 * there is nothing to wrap) get empty answers: {@link #baseBiome} is empty and
 * {@link BiomeZone.BaseBiome} never matches there. That is documented behaviour, not a failure —
 * use {@code isekai_api:override} when the condition is "wherever the base says".
 *
 * @since 3.1.0
 */
public final class ZoneContext {

    /** Resolves the wrapped biome source's answer at a biome-grid (quart) position. */
    @FunctionalInterface
    public interface BaseBiomeLookup {
        Holder<Biome> at(int quartX, int quartY, int quartZ);
    }

    /** Neither a wrapped source nor a climate field: every query is empty. @since 3.1.0 */
    public static final ZoneContext NONE = new ZoneContext(null, null);

    private final BaseBiomeLookup base;
    private final Climate.Sampler sampler;

    private boolean baseQueried;
    private int baseX;
    private int baseY;
    private int baseZ;
    private Holder<Biome> baseResult;

    private boolean climateQueried;
    private int climateX;
    private int climateY;
    private int climateZ;
    private Climate.TargetPoint climateResult;

    public ZoneContext(BaseBiomeLookup base, Climate.Sampler sampler) {
        this.base = base;
        this.sampler = sampler;
    }

    /**
     * A context with the climate field only, for sources that own every position —
     * {@code isekai_api:rule}. {@link #baseBiome} is always empty here.
     * @since 3.1.0
     */
    public static ZoneContext forClimate(Climate.Sampler sampler) {
        return sampler == null ? NONE : new ZoneContext(null, sampler);
    }

    /**
     * The wrapped source's biome at this position, or empty when this context has no wrapped
     * source. Memoised: see the class comment.
     * @since 3.1.0
     */
    public Optional<Holder<Biome>> baseBiome(int quartX, int quartY, int quartZ) {
        if (base == null) return Optional.empty();
        if (baseQueried && quartX == baseX && quartY == baseY && quartZ == baseZ) {
            return Optional.ofNullable(baseResult);
        }
        Holder<Biome> answer = base.at(quartX, quartY, quartZ);
        if (!baseQueried) {
            baseQueried = true;
            baseX = quartX;
            baseY = quartY;
            baseZ = quartZ;
            baseResult = answer;
        }
        return Optional.ofNullable(answer);
    }

    /**
     * The vanilla climate sample at this position, or empty when this context has no sampler.
     * Memoised: see the class comment.
     * @since 3.1.0
     */
    public Optional<Climate.TargetPoint> climate(int quartX, int quartY, int quartZ) {
        if (sampler == null) return Optional.empty();
        if (climateQueried && quartX == climateX && quartY == climateY && quartZ == climateZ) {
            return Optional.of(climateResult);
        }
        Climate.TargetPoint sample = sampler.sample(quartX, quartY, quartZ);
        if (!climateQueried) {
            climateQueried = true;
            climateX = quartX;
            climateY = quartY;
            climateZ = quartZ;
            climateResult = sample;
        }
        return Optional.of(sample);
    }

    /**
     * The base answer already memoised for this exact position, without querying the wrapped
     * source. A source that falls through to its base uses this, so a zone which asked
     * {@link #baseBiome} at the sample coordinate does not make the base sample twice.
     * @since 3.1.0
     */
    public Optional<Holder<Biome>> cachedBaseBiome(int quartX, int quartY, int quartZ) {
        if (!baseQueried || quartX != baseX || quartY != baseY || quartZ != baseZ) {
            return Optional.empty();
        }
        return Optional.ofNullable(baseResult);
    }
}
