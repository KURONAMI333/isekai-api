package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Dimension scoping for the biome carver list.
 *
 * <p>NeoForge's {@code BiomeModifier} has no dimension scope: a biome carries one carver list
 * and editing it in the modifier rewrites that list for every dimension that reuses the biome.
 * {@code exclusions.carvers} and {@code additions.carvers} therefore no longer touch
 * {@code BiomeGenerationSettingsBuilder} at all — they are recorded here per
 * (dimension, biome), and {@code NoiseBasedChunkGeneratorCarverMixin} applies the record when
 * that biome's carvers are read during the carve pass, where the generating dimension is known.
 *
 * <p><b>Why the carve pass.</b> {@code NoiseBasedChunkGenerator#applyCarvers} is the only
 * place the carver list is consumed, and it is also the first point that holds the
 * {@code WorldGenRegion}: the list comes from {@code ChunkAccess#carverBiome(...)} per
 * neighbour chunk, and the chunk being carved carries the dimension through the region.
 * Features are scoped earlier (decoration) by {@link DimensionFeatureScope}; carvers need their
 * own hook because the carve pass reads {@code getCarvers(step)} directly and exposes no biome
 * key.
 *
 * <p><b>Where the biome comes from.</b> The hook resolves it from the generator's own
 * {@code BiomeSource} at the neighbour chunk's corner quart — the same call the vanilla supplier
 * makes. It must not read the neighbour chunk's biome storage: during the carve pass most
 * neighbours are still EMPTY, and {@code ChunkAccess#getNoiseBiome} throws
 * "Asking for biomes before we have biomes". Verified on a live carve pass (the nether), where
 * every neighbour chunk reported that exception while the biome source answered normally.
 *
 * <p><b>Order is the modifier order.</b> NeoForge runs {@code Phase.ADD} before
 * {@code Phase.REMOVE} ({@code BiomeModifier.Phase}), and both used to edit the same builder,
 * so the effective list was {@code (base + additions) - exclusions}. {@link #apply} keeps that
 * order: a carver named in both lists is still dropped.
 *
 * <p><b>Generators this cannot reach.</b> The hook is a mixin on
 * {@code NoiseBasedChunkGenerator#applyCarvers}. A dimension whose generator is not
 * noise-based (or a subclass that overrides {@code applyCarvers} without calling super) never
 * reaches it, so {@link #hookable} reports {@code false} for it and the phases fall back to the
 * old global builder edit. That keeps every dimension working exactly as before; the
 * cross-dimension leak simply remains where no hook exists. Nothing here may be assumed to
 * have taken effect for such a dimension.
 */
@ApiStatus.Internal
public final class CarverScope {

    private CarverScope() {}

    /** One (dimension, biome) pair. Both are registry keys, so both are always present. */
    private record ScopeKey(ResourceKey<Level> dimension, ResourceKey<Biome> biome) {}

    /** One {@code additions.carvers} entry, stored as a key so the registry stays the source. */
    private record Addition(GenerationStep.Carving step,
                            ResourceKey<ConfiguredWorldCarver<?>> carver) {}

    /**
     * What a worldshape wants for a (dimension, biome): drop these keys from every carving
     * step, then append these entries to their named step.
     */
    record Rule(Set<ResourceKey<ConfiguredWorldCarver<?>>> remove,
                List<Addition> add) {

        boolean isNoOp() {
            return remove.isEmpty() && add.isEmpty();
        }
    }

    /**
     * Rules each (dimension, biome) must apply at carve time. Written by the REMOVE/ADD phases
     * of the biome modifier, read by every chunk carved in that dimension; empty for biomes no
     * worldshape touched.
     */
    private static final Map<ScopeKey, Rule> RULES = new ConcurrentHashMap<>();

    /**
     * The resolved settings per (dimension, biome, base settings identity). The settings
     * instance is part of the key because the generator may pass a different one per call;
     * keying by the pair alone would return a copy of a stale base. {@link
     * BiomeGenerationSettings} has no {@code equals}, so the record compares it by identity.
     * The size is capped so a settings-per-call generator cannot grow it without bound.
     */
    private static final int CACHE_LIMIT = 256;

    private record CacheKey(ResourceKey<Level> dimension,
                            ResourceKey<Biome> biome,
                            BiomeGenerationSettings settings) {}

    private static final Map<CacheKey, BiomeGenerationSettings> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, BiomeGenerationSettings> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    /**
     * Whether the dimension's generator is reached by the carve-pass hook. Cached because the
     * answer is fixed once the level stems are decoded, and the phases ask per modifier entry.
     */
    private static final Map<ResourceKey<Level>, Boolean> HOOKED = new ConcurrentHashMap<>();

    /**
     * Record {@code exclusions.carvers} for {@code dimension} x {@code biome}. Merges with an
     * earlier call so two modifier entries naming the same pair both scope correctly.
     */
    public static void recordRemovals(ResourceKey<Level> dimension,
                                      ResourceKey<Biome> biome,
                                      Set<ResourceKey<ConfiguredWorldCarver<?>>> carvers) {
        if (dimension == null || biome == null || carvers.isEmpty()) return;
        record(dimension, biome, new Rule(Set.copyOf(carvers), List.of()));
    }

    /**
     * Record {@code additions.carvers} for {@code dimension} x {@code biome}. Entries are kept
     * in declaration order and are appended after the base list, exactly as the old
     * {@code generation.getCarvers(step).add(holder)} did.
     */
    public static void recordAdditions(ResourceKey<Level> dimension,
                                       ResourceKey<Biome> biome,
                                       List<WorldshapeDescriptor.AdditionalCarver> carvers) {
        if (dimension == null || biome == null || carvers.isEmpty()) return;
        List<Addition> additions = new ArrayList<>(carvers.size());
        for (WorldshapeDescriptor.AdditionalCarver carver : carvers) {
            additions.add(new Addition(carver.step(), carver.carver()));
        }
        record(dimension, biome, new Rule(Set.of(), List.copyOf(additions)));
    }

    private static void record(ResourceKey<Level> dimension, ResourceKey<Biome> biome, Rule rule) {
        if (rule.isNoOp()) return;
        ScopeKey key = new ScopeKey(dimension, biome);
        RULES.merge(key, rule, CarverScope::merge);
        // The record changed; every list cached for the pair is stale. Startup-only path.
        synchronized (CACHE) {
            CACHE.keySet().removeIf(cached -> cached.dimension().equals(dimension)
                    && cached.biome().equals(biome));
        }
        IsekaiApi.LOGGER.debug("[Isekai] carver scope recorded (dim={}, biome={}): removed={}, added={}",
                dimension.location(), biome.location(), rule.remove().size(), rule.add().size());
    }

    /** Union removals, concatenate additions — the same result as applying both modifiers. */
    private static Rule merge(Rule a, Rule b) {
        Set<ResourceKey<ConfiguredWorldCarver<?>>> removed = new HashSet<>(a.remove());
        removed.addAll(b.remove());
        List<Addition> added = new ArrayList<>(a.add());
        added.addAll(b.add());
        return new Rule(Set.copyOf(removed), List.copyOf(added));
    }

    /** Whether any (dimension, biome) pair has a rule. The mixin's fast path. */
    public static boolean isEmpty() {
        return RULES.isEmpty();
    }

    /** Drop every rule and cache for the current world. Called on server stop and by tests. */
    public static void clearAll() {
        RULES.clear();
        CACHE.clear();
        HOOKED.clear();
    }

    /**
     * Whether the carve-pass hook can reach {@code dimension}, i.e. whether its {@code LevelStem}
     * generator is a {@link NoiseBasedChunkGenerator}. A dimension that resolves to anything else
     * (or is not in the registry yet) must keep the old biome-wide edit: the phase records
     * nothing and writes the builder instead.
     *
     * <p>Read from the {@code LevelStem} registry rather than a live {@code ServerLevel} because
     * the phases run during the modifier pass, before any level exists.
     */
    public static boolean hookable(ResourceKey<Level> dimension) {
        if (dimension == null) return false;
        Boolean cached = HOOKED.get(dimension);
        if (cached != null) return cached;
        Boolean resolved = resolveHookable(dimension);
        if (resolved == null) return false;
        HOOKED.put(dimension, resolved);
        return resolved;
    }

    /** {@code null} when the answer is not knowable yet (no server context). */
    private static @Nullable Boolean resolveHookable(ResourceKey<Level> dimension) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM)
                .get(ResourceKey.create(Registries.LEVEL_STEM, dimension.location()))
                .map(stem -> stem.value().generator() instanceof NoiseBasedChunkGenerator)
                .orElse(false);
    }

    /**
     * Return {@code settings} with the recorded rule for this (dimension, biome) applied, or
     * {@code settings} itself when nothing is scoped. Never returns {@code null}.
     *
     * <p>{@code dimension} is the generating level's own key, so this is exact for the overworld,
     * the nether, the end and any modded dimension — including two dimensions that share one
     * {@code BiomeSource} instance.
     *
     * <p>{@code carvers} resolves the recorded addition keys. The biome is a registry reference
     * in the worldgen path; a key-less holder has no rule and passes through.
     */
    public static BiomeGenerationSettings filter(ResourceKey<Level> dimension,
                                                 Holder<Biome> biome,
                                                 BiomeGenerationSettings settings,
                                                 HolderGetter<ConfiguredWorldCarver<?>> carvers) {
        if (dimension == null || settings == null) return settings;
        ResourceKey<Biome> biomeKey = biome instanceof Holder.Reference<Biome> reference
                ? reference.key() : null;
        if (biomeKey == null) return settings;

        ScopeKey key = new ScopeKey(dimension, biomeKey);
        Rule rule = RULES.get(key);
        if (rule == null) return settings;

        CacheKey cacheKey = new CacheKey(dimension, biomeKey, settings);
        BiomeGenerationSettings cached = CACHE.get(cacheKey);
        if (cached != null) return cached;

        BiomeGenerationSettings filtered = apply(settings, rule, carvers::get);
        CACHE.put(cacheKey, filtered);
        return filtered;
    }

    /**
     * Apply {@code rule} to {@code settings}: every carving step keeps its base carvers minus
     * the exclusions, then the additions for that step are appended. Returns {@code settings}
     * itself when the rule changes nothing.
     */
    static BiomeGenerationSettings apply(BiomeGenerationSettings settings,
                                         Rule rule,
                                         Function<ResourceKey<ConfiguredWorldCarver<?>>,
                                                 Optional<Holder.Reference<ConfiguredWorldCarver<?>>>> resolver) {
        Map<GenerationStep.Carving, HolderSet<ConfiguredWorldCarver<?>>> out =
                new EnumMap<>(GenerationStep.Carving.class);
        boolean changed = false;
        for (GenerationStep.Carving step : GenerationStep.Carving.values()) {
            List<Holder<ConfiguredWorldCarver<?>>> base = new ArrayList<>();
            settings.getCarvers(step).forEach(base::add);
            List<Holder<ConfiguredWorldCarver<?>>> scoped = resolveStep(base, step, rule, resolver);
            if (!scoped.equals(base)) changed = true;
            if (!scoped.isEmpty()) out.put(step, HolderSet.direct(scoped));
        }
        if (!changed) return settings;
        return new BiomeGenerationSettings(out, settings.features());
    }

    /**
     * The carver list for one step after the rule: base entries whose key is not excluded,
     * followed by every addition naming this step whose key is still not excluded.
     *
     * <p>Pure — the only outside input is {@code resolver}, which is the carver registry in
     * production and a stub in tests.
     */
    static List<Holder<ConfiguredWorldCarver<?>>> resolveStep(
            List<Holder<ConfiguredWorldCarver<?>>> base,
            GenerationStep.Carving step,
            Rule rule,
            Function<ResourceKey<ConfiguredWorldCarver<?>>,
                    Optional<Holder.Reference<ConfiguredWorldCarver<?>>>> resolver) {
        List<Holder<ConfiguredWorldCarver<?>>> out = new ArrayList<>(base.size() + rule.add().size());
        for (Holder<ConfiguredWorldCarver<?>> holder : base) {
            if (!matches(holder, rule.remove())) out.add(holder);
        }
        for (Addition addition : rule.add()) {
            if (addition.step() != step) continue;
            if (rule.remove().contains(addition.carver())) continue;
            resolver.apply(addition.carver()).ifPresent(out::add);
        }
        return List.copyOf(out);
    }

    private static <T> boolean matches(Holder<T> holder, Set<ResourceKey<T>> targets) {
        return holder.unwrapKey().map(targets::contains).orElse(false);
    }
}
