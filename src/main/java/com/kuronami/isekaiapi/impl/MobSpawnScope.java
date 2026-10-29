package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dimension scoping for the mob spawn list.
 *
 * <p>NeoForge's {@code BiomeModifier} has no dimension scope: rewriting the biome's
 * {@link MobSpawnSettings.Builder} there changes the spawn list of that biome in every
 * dimension that reuses it. A nether worldshape that thins monsters therefore also thinned
 * them in every modded dimension built on the same nether biome. So the MODIFY phase no
 * longer edits the builder at all: it records, per (dimension, biome), the rule it wants and
 * {@link #apply} enforces that rule at the runtime read points, where the level is known.
 *
 * <p>Read points (see {@code NaturalSpawnerMobsAtMixin},
 * {@code NaturalSpawnerChunkGenerationMixin} and {@code MobSpawnVetoHandler}):
 * <ul>
 *   <li>{@code NaturalSpawner#mobsAt} — every natural spawn attempt, on the list that already
 *       carries other mods' {@code PotentialSpawns} additions.</li>
 *   <li>{@code NaturalSpawner#spawnMobsForChunkGeneration} — the initial creature population
 *       of a newly generated chunk, which reads the biome settings directly.</li>
 *   <li>{@code FinalizeSpawnEvent} — the veto for spawns that never read a biome list
 *       (patrols, spawners, structures); only the rule's exclusions apply there.</li>
 * </ul>
 *
 * <p><b>Identity is part of the contract.</b> {@code NaturalSpawner#canSpawnMobAt} checks
 * {@code mobsAt(...).unwrap().contains(data)}, and {@code SpawnerData} has no {@code equals}
 * — it is identity-comparable. A list whose weight did not change keeps the original
 * {@code SpawnerData} instance, and a rebuilt entry is interned by value
 * ({@link SpawnerKey}) so every equivalent lookup returns the same instance. NeoForge's
 * {@code PotentialSpawns} event hands out a fresh {@code unmodifiableList} per call when it
 * has a subscriber, so {@link #apply} transforms again on every call; the intern map is what
 * carries the entry identity across equivalent lists. When no rule applies, {@link #apply}
 * returns the caller's own list, not a copy.
 *
 * <p><b>Fortress spawns are out of scope</b>, exactly as in 2.x: the nether fortress override
 * does not go through the biome list at the point the mixin hooks, and it is left alone.
 *
 * <p><b>Limits.</b> {@code atmosphere.mob_spawn_costs} still applies to every dimension
 * ({@code NaturalSpawner#createState} has no level). Climate and visual effects are shared
 * biome attributes, not spawn data, and stay in the biome modifier. A custom generator that
 * overrides {@code ChunkGenerator#getMobsAt} still routes through {@code mobsAt}, so the list
 * is scoped there; only a generator that bypasses {@code NaturalSpawner} entirely escapes.
 */
@ApiStatus.Internal
public final class MobSpawnScope {

    private MobSpawnScope() {}

    /** One (dimension, biome) pair. Both are registry keys, so both are always present. */
    private record ScopeKey(ResourceKey<Level> dimension, ResourceKey<Biome> biome) {}

    /**
     * What a worldshape wants for a (dimension, biome): drop these entity types, multiply the
     * remaining weights, and append these extra entries.
     *
     * <p>{@code weightScale} is the fallback for every category; a value in
     * {@code weightScaleByCategory} overrides it for that category. This mirrors the 2.x
     * resolution order of {@code mob_spawn_strategy} and
     * {@code mob_spawn_strategy_by_category}. Entity types are stored as registry keys rather
     * than {@link EntityType} values so the rule survives a registry change without pinning
     * instances.
     *
     * <p>{@code additions} are already-built {@link MobSpawnSettings.SpawnerData} instances,
     * created once when the rule is recorded, so the same instance is appended on every call.
     */
    public record MobSpawnRule(Set<ResourceKey<EntityType<?>>> removed,
                               Map<MobCategory, Double> weightScaleByCategory,
                               double weightScale,
                               Map<MobCategory, List<MobSpawnSettings.SpawnerData>> additions) {

        public MobSpawnRule {
            removed = Set.copyOf(removed);
            weightScaleByCategory = immutableEnumMap(weightScaleByCategory);
            additions = immutableEnumMap(additions);
        }

        /** Whether applying this rule would change anything. */
        public boolean isNoOp() {
            if (!removed.isEmpty()) return false;
            if (Math.abs(weightScale - 1.0) >= 1e-6) return false;
            for (double factor : weightScaleByCategory.values()) {
                if (Math.abs(factor - 1.0) >= 1e-6) return false;
            }
            return additions.values().stream().allMatch(List::isEmpty);
        }

        /** The weight multiplier for {@code category}: the per-category entry wins. */
        public double factorFor(MobCategory category) {
            Double factor = weightScaleByCategory.get(category);
            return factor != null ? factor : weightScale;
        }
    }

    /** Rules per (dimension, biome), written by the MODIFY phase, read by every spawn attempt. */
    private static final Map<ScopeKey, MobSpawnRule> RULES = new ConcurrentHashMap<>();

    /**
     * Rebuilt entries by value. {@code SpawnerData} has no {@code equals}, so the same weight
     * scaled twice has to resolve to one instance for {@code contains} to match. A rebuilt
     * entry is only ever created for a changed weight; untouched entries keep the original.
     */
    private static final Map<SpawnerKey, MobSpawnSettings.SpawnerData> INTERN = new ConcurrentHashMap<>();

    private record SpawnerKey(EntityType<?> type, int weight, int minCount, int maxCount) {}

    /**
     * Record (or merge) the rule for {@code dimension} x {@code biome}. Called once per
     * (biome, modifier) during the startup pass. Two modifier entries naming the same pair
     * compose the way 2.x applied them in sequence: exclusions union, weights multiply,
     * additions concatenate. A no-op rule records nothing.
     */
    public static void record(ResourceKey<Level> dimension,
                              ResourceKey<Biome> biome,
                              MobSpawnRule rule) {
        if (dimension == null || biome == null || rule == null || rule.isNoOp()) return;
        ScopeKey key = new ScopeKey(dimension, biome);
        RULES.merge(key, rule, MobSpawnScope::merge);
        // The rule changed: a changed scale yields new weights, so the interned instances are
        // stale. Startup-only path, so clearing the whole map is cheap.
        INTERN.clear();
        IsekaiApi.LOGGER.debug(
                "[Isekai] mob spawn scope recorded (dim={}, biome={}): removed={}, scaled={}, added={}",
                dimension.location(), biome.location(), rule.removed().size(),
                rule.weightScaleByCategory().size() + (Math.abs(rule.weightScale - 1.0) >= 1e-6 ? 1 : 0),
                rule.additions().values().stream().mapToInt(List::size).sum());
    }

    /**
     * Compose two rules for the same pair. The per-category effective factor of the result is
     * the product of the two effective factors, which is what applying the modifiers one after
     * the other did to the builder in 2.x. Exclusions union and additions concatenate.
     */
    private static MobSpawnRule merge(MobSpawnRule a, MobSpawnRule b) {
        Set<ResourceKey<EntityType<?>>> removed = new HashSet<>(a.removed());
        removed.addAll(b.removed());

        Map<MobCategory, Double> factors = new EnumMap<>(MobCategory.class);
        double global = a.weightScale() * b.weightScale();
        for (MobCategory category : MobCategory.values()) {
            double factor = a.factorFor(category) * b.factorFor(category);
            if (Math.abs(factor - global) >= 1e-9) factors.put(category, factor);
        }

        Map<MobCategory, List<MobSpawnSettings.SpawnerData>> additions =
                new EnumMap<>(MobCategory.class);
        for (Map<MobCategory, List<MobSpawnSettings.SpawnerData>> source
                : List.of(a.additions(), b.additions())) {
            source.forEach((category, list) -> {
                if (list.isEmpty()) return;
                List<MobSpawnSettings.SpawnerData> merged =
                        new ArrayList<>(additions.getOrDefault(category, List.of()));
                merged.addAll(list);
                additions.put(category, List.copyOf(merged));
            });
        }

        return new MobSpawnRule(removed, factors, global, additions);
    }

    /**
     * Drop everything recorded for the current world. Called on server stop and by tests.
     */
    public static void clear() {
        RULES.clear();
        INTERN.clear();
    }

    /** Drop one pair's rule. The server path never needs it; tests do. */
    public static void forget(ResourceKey<Level> dimension, ResourceKey<Biome> biome) {
        if (dimension == null || biome == null) return;
        RULES.remove(new ScopeKey(dimension, biome));
    }

    /** Whether any pair has a rule. Lets the hot path skip the biome lookup entirely. */
    public static boolean isEmpty() {
        return RULES.isEmpty();
    }

    /** Whether {@code dimension} x {@code biome} has a rule. */
    public static boolean hasRulesFor(ResourceKey<Level> dimension, Holder<Biome> biome) {
        ResourceKey<Biome> biomeKey = keyOf(biome);
        return dimension != null && biomeKey != null && RULES.containsKey(new ScopeKey(dimension, biomeKey));
    }

    /**
     * Whether {@code type} is excluded for this {@code dimension} x {@code biome}.
     *
     * <p>The non-list read point: spawn paths that never build a {@link MobSpawnSettings} list
     * (patrols, spawners, structures, ...) cannot be filtered by {@link #apply}, so they ask
     * this predicate instead. It answers only the exclusion half of a rule — weights and
     * additions have no meaning without a list — and returns {@code false} when no rule
     * applies, so the caller keeps vanilla behaviour. No logging, and the empty map is checked
     * before any key work: this runs on every finalized spawn.
     */
    public static boolean isRemoved(ResourceKey<Level> dimension, Holder<Biome> biome, EntityType<?> type) {
        if (dimension == null || type == null || RULES.isEmpty()) return false;
        ResourceKey<Biome> biomeKey = keyOf(biome);
        if (biomeKey == null) return false;
        MobSpawnRule rule = RULES.get(new ScopeKey(dimension, biomeKey));
        if (rule == null) return false;
        ResourceKey<EntityType<?>> key = entityKey(type);
        return key != null && rule.removed().contains(key);
    }

    /**
     * Return {@code original} filtered/scaled for this dimension, or {@code original} itself
     * when no rule applies. Never returns {@code null}.
     *
     * <p>The category is inferred from the first entry. That is exact for the lists vanilla
     * builds (every entry belongs to the category it is filed under), but a list that mixes
     * categories has no unambiguous answer; prefer the overload that takes the category.
     */
    public static List<MobSpawnSettings.SpawnerData> apply(ResourceKey<Level> dimension,
                                                            Holder<Biome> biome,
                                                            List<MobSpawnSettings.SpawnerData> original) {
        return apply(dimension, biome, null, original);
    }

    /**
     * Return {@code original} filtered/scaled for this dimension and category, or
     * {@code original} itself when no rule applies.
     *
     * <p>{@code category} is the category the list is filed under (the value vanilla reads it
     * by), not the category of an individual entry: {@code SpawnerData} rewrites a MISC type to
     * {@code EntityType.PIG} in its constructor, so entry-derived categories can disagree with
     * the list they live in.
     */
    public static List<MobSpawnSettings.SpawnerData> apply(ResourceKey<Level> dimension,
                                                            Holder<Biome> biome,
                                                            @Nullable MobCategory category,
                                                            List<MobSpawnSettings.SpawnerData> original) {
        if (original == null || dimension == null || RULES.isEmpty()) return original;
        ResourceKey<Biome> biomeKey = keyOf(biome);
        if (biomeKey == null) return original;
        ScopeKey scope = new ScopeKey(dimension, biomeKey);
        MobSpawnRule rule = RULES.get(scope);
        if (rule == null || rule.isNoOp()) return original;

        // No cache: the rule is per (dimension, biome), so a list's transform depends on more
        // than its content. The per-entry intern map keeps identity across calls instead.
        return transform(rule, category, original);
    }

    private static List<MobSpawnSettings.SpawnerData> transform(
            MobSpawnRule rule,
            @Nullable MobCategory category,
            List<MobSpawnSettings.SpawnerData> original) {
        MobCategory effectiveCategory = category;
        if (effectiveCategory == null && !original.isEmpty()) {
            effectiveCategory = original.get(0).type.getCategory();
        }
        List<MobSpawnSettings.SpawnerData> additions = effectiveCategory == null
                ? List.of()
                : rule.additions().getOrDefault(effectiveCategory, List.of());

        List<MobSpawnSettings.SpawnerData> out = new ArrayList<>(original.size() + additions.size());
        for (MobSpawnSettings.SpawnerData entry : original) {
            if (rule.removed().contains(entityKey(entry.type))) continue;
            MobCategory entryCategory = effectiveCategory != null
                    ? effectiveCategory
                    : entry.type.getCategory();
            double factor = rule.factorFor(entryCategory);
            int oldWeight = entry.getWeight().asInt();
            if (Math.abs(factor - 1.0) < 1e-6) {
                out.add(entry);
                continue;
            }
            // Keep 0 possible: 2.x's builder path used max(0, round(w * factor)), and
            // CountScale's codec admits factor 0.0, so max(1, ...) would re-enable mobs a
            // consumer disabled by weight.
            int newWeight = Math.max(0, (int) Math.round(oldWeight * factor));
            out.add(newWeight == oldWeight
                    ? entry
                    : intern(entry.type, newWeight, entry.minCount, entry.maxCount));
        }
        // Additions win over exclusions, as in 2.x: the excluded entry is gone, this one is
        // appended after it (record() warns when a type is in both).
        out.addAll(additions);
        return List.copyOf(out);
    }

    /**
     * One instance per (type, weight, min, max). {@code SpawnerData} is identity-comparable, so
     * this is what makes a rebuilt entry survive the second {@code mobsAt} lookup that
     * {@code NaturalSpawner#canSpawnMobAt} performs.
     */
    private static MobSpawnSettings.SpawnerData intern(EntityType<?> type,
                                                        int weight,
                                                        int minCount,
                                                        int maxCount) {
        SpawnerKey key = new SpawnerKey(type, weight, minCount, maxCount);
        return INTERN.computeIfAbsent(key,
                ignored -> new MobSpawnSettings.SpawnerData(type, weight, minCount, maxCount));
    }

    /**
     * Normalize a biome holder to a registry key. A {@code Holder.direct} biome has no key, so
     * no rule can be looked up for it and the list passes through untouched.
     */
    private static ResourceKey<Biome> keyOf(Holder<Biome> biome) {
        if (biome instanceof Holder.Reference<Biome> reference) return reference.key();
        return null;
    }

    /**
     * The registry key of a spawn entry's type, or {@code null} for an unregistered type (which
     * no exclusion can name). 1.21.1's {@code EntityType.getKey} returns a
     * {@link net.minecraft.resources.ResourceLocation}, so the key has to come from the
     * registry.
     */
    public static @Nullable ResourceKey<EntityType<?>> entityKey(EntityType<?> type) {
        if (type == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).orElse(null);
    }

    private static <V> Map<MobCategory, V> immutableEnumMap(Map<MobCategory, V> source) {
        if (source.isEmpty()) return Map.of();
        EnumMap<MobCategory, V> copy = new EnumMap<>(MobCategory.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }
}
