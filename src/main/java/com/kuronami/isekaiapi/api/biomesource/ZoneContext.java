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
 * <p><b>Memoised.</b> The base answer and the climate sample at the position being decided are
 * computed at most once and then reused. This is what keeps a tree such as {@code and(base_biome, ...)} — or fifteen overrides
 * asking the same question — from sampling the wrapped source or the climate field once per
 * candidate. A query at another coordinate (edge_jitter moves it) is computed every time and never
 * stored, so it cannot displace the answer for the position being decided.
 *
 * <p>Zones that are not handed a wrapped source ({@code isekai_api:rule} owns every position, so
 * there is nothing to wrap) get empty answers: {@link #baseBiome} is empty and
 * {@link BiomeZone.BaseBiome} never matches there. That is documented behaviour, not a failure —
 * use {@code isekai_api:override} when the condition is "wherever the base says".
 *
 * @since 3.0.0
 */
public final class ZoneContext {

    /** Resolves the wrapped biome source's answer at a biome-grid (quart) position. */
    @FunctionalInterface
    public interface BaseBiomeLookup {
        Holder<Biome> at(int quartX, int quartY, int quartZ);
    }

    /** Neither a wrapped source nor a climate field: every query is empty. @since 3.0.0 */
    public static final ZoneContext NONE = new ZoneContext(0, 0, 0, null, null);

    private final int x;
    private final int y;
    private final int z;
    private final BaseBiomeLookup base;
    private final Climate.Sampler sampler;

    private boolean baseQueried;
    private Holder<Biome> baseResult;

    private boolean climateQueried;
    private Climate.TargetPoint climateResult;

    /**
     * A context for deciding the biome at one position.
     *
     * @param quartX the position the source is deciding; only answers at this exact position are
     *               remembered, so a zone that looks elsewhere first (edge_jitter) cannot displace them
     * @param base the wrapped source's answer, or {@code null} when nothing is wrapped
     * @param sampler the level's climate sampler, or {@code null}
     * @since 3.0.0
     */
    public ZoneContext(int quartX, int quartY, int quartZ, BaseBiomeLookup base, Climate.Sampler sampler) {
        this.x = quartX;
        this.y = quartY;
        this.z = quartZ;
        this.base = base;
        this.sampler = sampler;
    }

    private boolean isSamplePosition(int qx, int qy, int qz) {
        return qx == x && qy == y && qz == z;
    }

    /**
     * A context with the climate field only, for sources that own every position —
     * {@code isekai_api:rule}. {@link #baseBiome} is always empty here.
     * @since 3.0.0
     */
    public static ZoneContext forClimate(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        return sampler == null ? NONE : new ZoneContext(quartX, quartY, quartZ, null, sampler);
    }

    /**
     * The wrapped source's biome at this position, or empty when this context has no wrapped
     * source. Memoised: see the class comment.
     * @since 3.0.0
     */
    public Optional<Holder<Biome>> baseBiome(int quartX, int quartY, int quartZ) {
        if (base == null) return Optional.empty();
        if (!isSamplePosition(quartX, quartY, quartZ)) {
            return Optional.ofNullable(base.at(quartX, quartY, quartZ));
        }
        if (!baseQueried) {
            baseResult = base.at(quartX, quartY, quartZ);
            baseQueried = true;
        }
        return Optional.ofNullable(baseResult);
    }

    /**
     * The vanilla climate sample at this position, or empty when this context has no sampler.
     * Memoised: see the class comment.
     * @since 3.0.0
     */
    public Optional<Climate.TargetPoint> climate(int quartX, int quartY, int quartZ) {
        if (sampler == null) return Optional.empty();
        if (!isSamplePosition(quartX, quartY, quartZ)) {
            return Optional.of(sampler.sample(quartX, quartY, quartZ));
        }
        if (!climateQueried) {
            climateResult = sampler.sample(quartX, quartY, quartZ);
            climateQueried = true;
        }
        return Optional.of(climateResult);
    }

    /**
     * The base answer already memoised for the sample position, without querying the wrapped
     * source. A source that falls through to its base uses this, so a zone which asked
     * {@link #baseBiome} at the sample coordinate does not make the base sample twice.
     * @since 3.0.0
     */
    public Optional<Holder<Biome>> cachedBaseBiome() {
        return baseQueried ? Optional.ofNullable(baseResult) : Optional.empty();
    }
}
