package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.placementmodifier.DimensionGuardPlacementModifier;
import com.kuronami.isekaiapi.placementmodifier.SpatialPredicatePlacementModifier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dimension scoping for the biome feature pipeline.
 *
 * <p>NeoForge's {@code BiomeModifier} has no dimension scope: it sees a {@link Biome} and
 * nothing else. Editing that biome's generation settings — deleting a remapped original,
 * deleting an excluded feature — therefore leaks into every dimension that reuses the biome,
 * which is exactly what a nether worldshape must not do (the nether biomes are reusable by
 * modded dimensions, and a custom nether dimension must not rewrite
 * {@code minecraft:the_nether}'s features).
 *
 * <p>So the removals move out of the modifier phase and into decoration time. The ADD phase
 * records, per (dimension, biome), the keys it took over — {@code exclusions.features},
 * {@code feature_predicates}, and the {@link RemapTargets} it re-injected — and
 * {@link #filter} drops exactly those keys when that biome is decorated in that dimension:
 * <ul>
 *   <li>{@link #record} / {@link #skipKeys} — the recorded takeover set.</li>
 *   <li>{@link #filter} — the biome's feature steps with that set removed. Carvers do not
 *       pass through here; {@code CarverScope} scopes them separately at carve time (see the
 *       limits below).</li>
 *   <li>{@link #guarded} — prepend a {@link DimensionGuardPlacementModifier} to a feature the
 *       ADD phase injected, so it can only place in the dimension its descriptor named.</li>
 * </ul>
 *
 * <p><b>Why the set is recorded rather than re-derived.</b> NeoForge runs biome modifiers once,
 * before the server starts ({@code ServerLifecycleHooks}), and never again — a {@code /reload}
 * does not re-run them. If decoration re-derived the set from the live declarations, a reload
 * that added a {@code feature_predicates} entry would start dropping the original while the
 * guarded copy from startup was never created: the feature would silently vanish. Removing the
 * entry would do the reverse and generate it twice. Recording at ADD time binds the drop and the
 * injection to the same pass, so a reload leaves features alone until the next start — the same
 * rule the injected copies already follow.
 *
 * <p><b>Final defense for custom generators.</b> {@link #filter} is applied by
 * {@code ChunkGeneratorDecorationMixin}, which redirects a call inside
 * {@code ChunkGenerator#applyBiomeDecoration}. A generator that overrides
 * {@code applyBiomeDecoration} wholesale never reaches that call, so the filter cannot see its
 * features. {@link #shouldDrop} therefore holds the same takeover set resolved to
 * {@link PlacedFeature} values, and {@code PlacedFeatureScopeMixin} vetoes those values at
 * {@code PlacedFeature#placeWithContext} — the last step every generator goes through. This
 * veto is <em>not</em> a replacement for the decoration filter: it cannot scope a feature the
 * generator never routes through {@code placeWithContext} manually, and it relies on value
 * equality, so it is a final defense line, not the primary mechanism.
 *
 * <p><b>Known limits.</b> Biomes carry one feature list and one carver list, so both are
 * shared across dimensions. Features are fixed per chunk at decoration time and are filtered
 * here. Carvers are read later through {@code applyCarvers}, which this class cannot see;
 * {@code CarverScope} scopes them from the carve-pass hook on
 * {@code NoiseBasedChunkGenerator}. A dimension whose generator is not noise-based, or a
 * subclass that overrides {@code applyCarvers} without calling super, never reaches that hook
 * and keeps the old biome-wide edit. Mob spawns are scoped by {@code MobSpawnScope} at the
 * runtime spawn read points. {@code atmosphere} — climate settings, biome special effects,
 * creature generation probability and spawn costs — is a biome attribute with no
 * per-dimension hook, so it stays biome-wide and must be narrowed with {@code applies_to};
 * {@code atmosphere.mob_spawn_costs} in particular stays global because the spawn state
 * carries no level. {@code isekai_api} scopes features, structures, carvers and mob spawns.
 */
@ApiStatus.Internal
public final class DimensionFeatureScope {

    private DimensionFeatureScope() {}

    /** One (dimension, biome) pair. The keys are registry keys, so both are always present. */
    private record ScopeKey(ResourceKey<Level> dimension, ResourceKey<Biome> biome) {}

    /**
     * Keys each (dimension, biome) must drop at decoration time. Written by the ADD phase,
     * read by every chunk in that dimension; empty for biomes no worldshape touched.
     */
    private static final Map<ScopeKey, Set<ResourceKey<PlacedFeature>>> TAKEOVER =
            new ConcurrentHashMap<>();

    /**
     * The same takeover set as {@link #TAKEOVER}, resolved to {@link PlacedFeature} values.
     * The decoration filter works on keys, but {@code PlacedFeature#placeWithContext} only has
     * the value in hand, and a generator that overrides {@code applyBiomeDecoration} never
     * reaches the filter at all. Resolved from the biome's generation settings when the set is
     * recorded; keys absent from that settings are skipped, and an empty result records nothing.
     * {@link PlacedFeature} is a record, so equality compares the configured feature and the
     * placement list — the guarded copies have a different list and therefore never match.
     */
    private static final Map<ScopeKey, Set<PlacedFeature>> DROP_VALUES = new ConcurrentHashMap<>();

    /**
     * Filtered settings per (dimension, biome, base settings identity). The settings instance is
     * part of the key because a generator may hand out a different one per call; keying by the
     * pair alone would then return a filtered copy of a stale settings object.
     *
     * <p>{@link BiomeGenerationSettings} does not override {@code equals}, so the record compares
     * it by identity. The size is capped: with a settings-per-call generator the entries would
     * otherwise grow without bound.
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
     * Record what the ADD phase took over in {@code biome} for {@code dimension}. Merges with an
     * earlier call so two modifier entries naming the same pair both scope correctly. Called once
     * per (biome, modifier) during the startup pass; an empty set records nothing.
     */
    public static void record(ResourceKey<Level> dimension,
                              ResourceKey<Biome> biome,
                              Set<ResourceKey<PlacedFeature>> keys) {
        if (dimension == null || biome == null || keys.isEmpty()) return;
        ScopeKey key = new ScopeKey(dimension, biome);
        TAKEOVER.merge(key, Set.copyOf(keys), DimensionFeatureScope::union);
        Set<PlacedFeature> values = resolveValues(biome, keys);
        if (!values.isEmpty()) {
            DROP_VALUES.merge(key, Set.copyOf(values), DimensionFeatureScope::unionValues);
        }
        // The settings shape did not change; only which keys survive. Drop any cached list for
        // the pair so a second recording is reflected (this is a startup-only path).
        synchronized (CACHE) {
            CACHE.keySet().removeIf(cached -> cached.dimension().equals(dimension)
                    && cached.biome().equals(biome));
        }
    }

    private static Set<ResourceKey<PlacedFeature>> union(Set<ResourceKey<PlacedFeature>> a,
                                                          Set<ResourceKey<PlacedFeature>> b) {
        Set<ResourceKey<PlacedFeature>> merged = new java.util.HashSet<>(a);
        merged.addAll(b);
        return Set.copyOf(merged);
    }

    private static Set<PlacedFeature> unionValues(Set<PlacedFeature> a, Set<PlacedFeature> b) {
        Set<PlacedFeature> merged = new java.util.HashSet<>(a);
        merged.addAll(b);
        return Set.copyOf(merged);
    }

    /**
     * Resolve takeover keys to the {@link PlacedFeature} values the biome's current generation
     * settings hold. A key that is not in those settings is skipped (it cannot be placed there
     * anyway), and a key-less holder is ignored because it has no registry key to match.
     *
     * <p>Resolution runs while the ADD phase records, i.e. against the biome as registered, not
     * against the builder being modified — the originals are exactly what the veto must stop.
     */
    private static Set<PlacedFeature> resolveValues(ResourceKey<Biome> biomeKey,
                                                     Set<ResourceKey<PlacedFeature>> keys) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Set.of();
        Holder.Reference<Biome> biome = server.registryAccess()
                .lookupOrThrow(Registries.BIOME).get(biomeKey).orElse(null);
        if (biome == null) return Set.of();
        Set<PlacedFeature> values = new java.util.HashSet<>();
        for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
            for (Holder<PlacedFeature> holder : step) {
                ResourceKey<PlacedFeature> key = holder.unwrapKey().orElse(null);
                if (key != null && keys.contains(key)) values.add(holder.value());
            }
        }
        return values;
    }

    /** Drop everything recorded for the current world. Called on server stop and by tests. */
    public static void clearAll() {
        TAKEOVER.clear();
        DROP_VALUES.clear();
        CACHE.clear();
    }

    /**
     * Drop one pair's record and cached list. The server path never needs this (a new world
     * starts from {@link #clearAll()}); tests use it so a fixture does not scope the chunks
     * every later test generates.
     */
    public static void forget(ResourceKey<Level> dimension, ResourceKey<Biome> biome) {
        if (dimension == null || biome == null) return;
        ScopeKey key = new ScopeKey(dimension, biome);
        TAKEOVER.remove(key);
        DROP_VALUES.remove(key);
        synchronized (CACHE) {
            CACHE.keySet().removeIf(cached -> cached.dimension().equals(dimension)
                    && cached.biome().equals(biome));
        }
    }

    /**
     * Return {@code settings} with the recorded takeover set removed, or {@code settings} itself
     * when nothing is scoped away. Never returns {@code null}.
     *
     * <p>{@code dimension} comes from the level that is generating the chunk, not from a lookup:
     * keys are the dimension registry id, so this works for the overworld, the nether, the end
     * and any modded dimension identically — including two dimensions that share one
     * {@code BiomeSource} instance, which no biome-source lookup can disambiguate.
     */
    public static BiomeGenerationSettings filter(ResourceKey<Level> dimension,
                                                  Holder<Biome> biome,
                                                  BiomeGenerationSettings settings) {
        if (dimension == null) return settings;
        Holder.Reference<Biome> reference = reference(biome);
        if (reference == null) return settings;

        ScopeKey key = new ScopeKey(dimension, reference.key());
        Set<ResourceKey<PlacedFeature>> skip = TAKEOVER.get(key);
        if (skip == null) return settings;

        CacheKey cacheKey = new CacheKey(dimension, reference.key(), settings);
        BiomeGenerationSettings cached = CACHE.get(cacheKey);
        if (cached != null) return cached;

        BiomeGenerationSettings filtered = dropFeatures(settings, skip);
        CACHE.put(cacheKey, filtered);
        return filtered;
    }

    /**
     * Keys the ADD phase took over in this biome and dimension. Empty when no modifier applied
     * a worldshape here — the scope is installed by the ADD phase, exactly like additions,
     * carvers and mob spawns, so a descriptor that no biome modifier references changes nothing.
     */
    public static Set<ResourceKey<PlacedFeature>> skipKeys(ResourceKey<Level> dimension,
                                                            Holder<Biome> biome) {
        if (dimension == null || biome == null) return Set.of();
        Holder.Reference<Biome> reference = reference(biome);
        if (reference == null) return Set.of();
        return TAKEOVER.getOrDefault(new ScopeKey(dimension, reference.key()), Set.of());
    }

    /**
     * Whether {@code feature} must not place in {@code dimension} for {@code biome} because the
     * ADD phase replaced it there.
     *
     * <p>This is the value-level twin of {@link #filter}, used by
     * {@code PlacedFeatureScopeMixin} at {@code PlacedFeature#placeWithContext}. It matters only
     * for generators that override {@code ChunkGenerator#applyBiomeDecoration} and therefore
     * bypass the decoration redirect; for a normal generator the filter has already removed the
     * feature, so this is a no-op there. It is a final defense line, not a substitute for the
     * filter: the comparison is value equality ({@link PlacedFeature} is a record), so a guarded
     * copy — whose placement list differs — does not match the original it replaced.
     *
     * <p>Returns {@code false} when nothing is scoped, when either argument is {@code null}, or
     * when the biome has no registry key to look the scope up by.
     */
    public static boolean shouldDrop(ResourceKey<Level> dimension,
                                     Holder<Biome> biome,
                                     PlacedFeature feature) {
        if (DROP_VALUES.isEmpty() || dimension == null || biome == null || feature == null) {
            return false;
        }
        Holder.Reference<Biome> reference = reference(biome);
        if (reference == null) return false;
        Set<PlacedFeature> values = DROP_VALUES.get(new ScopeKey(dimension, reference.key()));
        return values != null && values.contains(feature);
    }

    /**
     * Whether any (dimension, biome) pair has a value-level drop set. The mixin reads this as a
     * fast path so a server with no worldshape never pays for a biome lookup per placed feature.
     */
    public static boolean hasAnyDrop() {
        return !DROP_VALUES.isEmpty();
    }

    private static BiomeGenerationSettings dropFeatures(BiomeGenerationSettings settings,
                                                         Set<ResourceKey<PlacedFeature>> skip) {
        List<HolderSet<PlacedFeature>> features = settings.features();
        List<HolderSet<PlacedFeature>> kept = new ArrayList<>(features.size());
        boolean changed = false;
        for (HolderSet<PlacedFeature> step : features) {
            List<Holder<PlacedFeature>> survivors = new ArrayList<>(step.size());
            boolean dropped = false;
            for (Holder<PlacedFeature> holder : step) {
                if (holder.unwrapKey().map(skip::contains).orElse(false)) {
                    dropped = true;
                } else {
                    survivors.add(holder);
                }
            }
            if (dropped) {
                kept.add(survivors.isEmpty() ? HolderSet.empty() : HolderSet.direct(survivors));
                changed = true;
            } else {
                kept.add(step);
            }
        }
        if (!changed) return settings;
        return new BiomeGenerationSettings(copyCarvers(settings), List.copyOf(kept));
    }

    /**
     * Copy the carver lists so the filtered settings share nothing mutable with the biome's.
     * A tag-backed {@link HolderSet} becomes a direct list of the same holders, which carves
     * identically — the tag is only a lookup shorthand.
     */
    private static Map<GenerationStep.Carving, HolderSet<ConfiguredWorldCarver<?>>> copyCarvers(
            BiomeGenerationSettings settings) {
        Map<GenerationStep.Carving, HolderSet<ConfiguredWorldCarver<?>>> carvers =
                new EnumMap<>(GenerationStep.Carving.class);
        for (GenerationStep.Carving step : settings.getCarvingStages()) {
            List<Holder<ConfiguredWorldCarver<?>>> list = new ArrayList<>();
            settings.getCarvers(step).forEach(list::add);
            carvers.put(step, HolderSet.direct(list));
        }
        return carvers;
    }

    /**
     * Return {@code original} with a dimension guard — and optionally {@code predicate} —
     * prepended, so the copy places only in {@code dimension} and only where the predicate
     * holds. The copy is a distinct {@link PlacedFeature} instance; it must also be added to
     * the biome's generation settings, or {@code minecraft:biome} would reject it.
     *
     * <p><b>Do not give {@link DimensionGuardPlacementModifier} value equality.</b>
     * {@link PlacedFeature} is a record, so two copies are {@code equals} exactly when their
     * modifiers are; the guard instances are what keep them distinct. {@code FeatureSorter}
     * de-duplicates feature indices by {@code equals} but maps them back by reference
     * ({@code Util.createIndexIdentityLookup}, default {@code -1}), so two biomes' copies that
     * compared equal would collapse to one index and send the other through {@code get(-1)} at
     * decoration, crashing chunk generation.
     */
    public static PlacedFeature guarded(PlacedFeature original,
                                         ResourceKey<Level> dimension,
                                         SpatialPredicate predicate) {
        List<PlacementModifier> mods = new ArrayList<>(original.placement().size() + 2);
        mods.add(new DimensionGuardPlacementModifier(dimension));
        if (predicate != null) mods.add(new SpatialPredicatePlacementModifier(predicate));
        mods.addAll(original.placement());
        return new PlacedFeature(original.feature(), List.copyOf(mods));
    }

    /**
     * Normalize any biome holder to a keyed reference. The worldgen path always hands out
     * registry references, so the fallback is a safety net for programmatic callers only.
     */
    private static Holder.Reference<Biome> reference(Holder<Biome> biome) {
        if (biome instanceof Holder.Reference<Biome> reference) return reference;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return biome.unwrapKey()
                .flatMap(key -> server.registryAccess()
                        .lookupOrThrow(Registries.BIOME).get(key))
                .orElse(null);
    }
}
