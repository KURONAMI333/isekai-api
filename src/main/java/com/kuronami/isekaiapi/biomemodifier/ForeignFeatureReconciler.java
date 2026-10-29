package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.BiomeMatcher;
import com.kuronami.isekaiapi.impl.DimensionFeatureScope;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import com.kuronami.isekaiapi.mixin.ModifiableBiomeInfoAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Brings features that <em>other mods</em> add to a declared biome into this library's
 * dimension scope.
 *
 * <p>{@link VanillaRuleSnapshot} reads {@code Biome#getOriginalBiomeInfo()}, so it describes
 * the biome as the datapack defined it — the vanilla baseline. Features injected by another
 * mod's biome modifier are not in it. Our own modifiers run in
 * {@code ServerLifecycleHooks.runModifiers} alongside theirs, in registry order, so a modifier
 * that runs after ours adds features our ADD phase never saw: they stay unaudited in the
 * biome's shared settings and generate in every dimension that uses the biome.
 *
 * <p>The reconcile runs at the TAIL of {@code runModifiers}
 * ({@code ServerLifecycleHooksReconcileMixin}) — every modifier has run, no level exists yet,
 * and no chunk has been decorated. For each declared biome it takes the keyed holders in the
 * final generation settings that the vanilla snapshot does not know about, removes them from
 * the shared settings, injects a {@code DimensionFeatureScope.guarded} copy per declaring
 * dimension (mirroring {@code AddPhase}), and records the takeover so the decoration-time
 * filter agrees with the injection.
 *
 * <p><b>Keys the ADD phase already took over are left alone.</b> A key listed in
 * {@code exclusions.features} or {@code featurePredicates} is already in
 * {@link DimensionFeatureScope#skipKeys}: the ADD phase either injected its predicated copy at
 * startup or recorded the exclusion. Re-injecting it here would duplicate the copy (or undo the
 * exclusion), so only genuinely unknown keys are reconciled. The original stays in place for
 * those and the existing per-dimension filter handles it.
 *
 * <p><b>Idempotent.</b> The originals are removed from the final settings and the replacements
 * are key-less {@code Holder.direct} copies, so a second call finds no foreign key and does
 * nothing.
 *
 * <p><b>No-op cost.</b> With no declared worldshape the reconciler returns before touching the
 * registry. With declarations it makes one pass over the biome registry, matching
 * {@code appliesTo} per biome, and returns as soon as a declared biome has no foreign key.
 */
@ApiStatus.Internal
public final class ForeignFeatureReconciler {

    private ForeignFeatureReconciler() {}

    /** A descriptor together with the dimension it was declared for. */
    private record DeclaredScope(ResourceKey<Level> dimension, WorldshapeDescriptor descriptor) {}

    /** A rebuilt settings object plus how many guarded copies went into it. */
    private record Rebuilt(BiomeGenerationSettings settings, int copies) {}

    /**
     * Reconcile every declared biome. Called once per server start from the
     * {@code runModifiers} tail; exceptions propagate to the mixin, which degrades instead of
     * failing startup.
     */
    public static void reconcile(MinecraftServer server) {
        if (server == null) return;

        List<DeclaredScope> scopes = declaredScopes();
        if (scopes.isEmpty()) return;

        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            // With no baseline every keyed feature would look foreign, and removing them would
            // strip the biome for every dimension. A missing snapshot already degrades the
            // pipeline; leave the biomes untouched.
            IsekaiApi.LOGGER.debug("[Isekai] foreign-feature reconcile skipped: vanilla snapshot unavailable");
            return;
        }

        HolderLookup.RegistryLookup<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        int touchedBiomes = 0;
        int injectedCopies = 0;

        for (Holder.Reference<Biome> reference : biomes.listElements().toList()) {
            Map<ResourceKey<Level>, List<WorldshapeDescriptor>> byDimension = new LinkedHashMap<>();
            for (DeclaredScope scope : scopes) {
                if (!BiomeMatcher.matches(scope.descriptor(), reference)) continue;
                byDimension.computeIfAbsent(scope.dimension(), k -> new ArrayList<>()).add(scope.descriptor());
            }
            if (byDimension.isEmpty()) continue;

            Biome biome = reference.value();
            BiomeGenerationSettings finalSettings = biome.getGenerationSettings();
            Set<ResourceKey<PlacedFeature>> foreign = foreignFeatureKeys(
                    snapshot.featuresInBiome(reference.key()), flatten(finalSettings.features()));
            if (foreign.isEmpty()) continue;

            // Each dimension keeps its own view: the ADD phase's takeover set is per dimension,
            // so subtracting the union would let one dimension's exclusion erase a key another
            // dimension still has to reconcile.
            Map<ResourceKey<Level>, Set<ResourceKey<PlacedFeature>>> scopedForeign = new LinkedHashMap<>();
            for (ResourceKey<Level> dimension : byDimension.keySet()) {
                Set<ResourceKey<PlacedFeature>> perDimension = new HashSet<>(foreign);
                perDimension.removeAll(DimensionFeatureScope.skipKeys(dimension, reference));
                if (!perDimension.isEmpty()) scopedForeign.put(dimension, perDimension);
            }
            if (scopedForeign.isEmpty()) continue;

            // Rebuild removes exactly the keys at least one dimension reconciles; a key owned by
            // every declaring dimension stays where the ADD phase put it. Keys a single
            // dimension still owns are re-injected only for that dimension (see rebuild).
            Set<ResourceKey<PlacedFeature>> reconcilable = new HashSet<>();
            for (Set<ResourceKey<PlacedFeature>> keys : scopedForeign.values()) {
                reconcilable.addAll(keys);
            }

            Rebuilt rebuilt = rebuild(finalSettings, reconcilable, byDimension, scopedForeign);
            for (Map.Entry<ResourceKey<Level>, Set<ResourceKey<PlacedFeature>>> entry : scopedForeign.entrySet()) {
                DimensionFeatureScope.record(entry.getKey(), reference.key(), entry.getValue());
            }

            ModifiableBiomeInfo info = biome.modifiableBiomeInfo();
            ModifiableBiomeInfo.BiomeInfo current = info.get();
            ModifiableBiomeInfo.BiomeInfo replaced = new ModifiableBiomeInfo.BiomeInfo(
                    current.climateSettings(),
                    current.effects(),
                    rebuilt.settings(),
                    current.mobSpawnSettings());
            ((ModifiableBiomeInfoAccessor) (Object) info).isekai$setModifiedBiomeInfo(replaced);

            touchedBiomes++;
            injectedCopies += rebuilt.copies();
        }

        if (touchedBiomes > 0) {
            IsekaiApi.LOGGER.info("[Isekai] foreign feature reconcile: {} biome(s) touched, {} guarded copy/copies injected",
                    touchedBiomes, injectedCopies);
        }
    }

    /** Every active single-layer and layered descriptor, paired with its dimension. */
    private static List<DeclaredScope> declaredScopes() {
        IsekaiRemap remap = Isekai.remap();
        Set<ResourceKey<Level>> dimensions = remap.getDeclaredDimensions();
        if (dimensions.isEmpty()) return List.of();

        List<DeclaredScope> scopes = new ArrayList<>();
        for (ResourceKey<Level> dimension : dimensions) {
            remap.getActiveDescriptor(dimension)
                    .ifPresent(descriptor -> scopes.add(new DeclaredScope(descriptor.dimension(), descriptor)));
            for (LayeredDescriptor layer : remap.getActiveLayers(dimension)) {
                WorldshapeDescriptor descriptor = layer.descriptor();
                scopes.add(new DeclaredScope(descriptor.dimension(), descriptor));
            }
        }
        return scopes;
    }

    /**
     * The keys in {@code features} that are absent from the vanilla snapshot.
     *
     * <p>Key-less holders are ignored: that is exactly the shape of our own injected guarded
     * copies ({@code Holder.direct}), so they never count as foreign. The method is a pure
     * function of its arguments; {@link #reconcile} then subtracts the keys the ADD phase
     * already owns.
     */
    static Set<ResourceKey<PlacedFeature>> foreignFeatureKeys(Set<ResourceKey<PlacedFeature>> vanillaKeys,
                                                              Iterable<Holder<PlacedFeature>> features) {
        Set<ResourceKey<PlacedFeature>> foreign = new HashSet<>();
        if (vanillaKeys == null || features == null) return foreign;
        for (Holder<PlacedFeature> holder : features) {
            if (holder == null) continue;
            ResourceKey<PlacedFeature> key = holder.unwrapKey().orElse(null);
            if (key != null && !vanillaKeys.contains(key)) foreign.add(key);
        }
        return foreign;
    }

    private static List<Holder<PlacedFeature>> flatten(List<HolderSet<PlacedFeature>> steps) {
        List<Holder<PlacedFeature>> holders = new ArrayList<>();
        for (HolderSet<PlacedFeature> step : steps) step.forEach(holders::add);
        return holders;
    }

    /**
     * Remove every foreign holder and append one guarded copy per declaring dimension to the
     * same decoration step. The step list keeps its length and order — decoration indexes it by
     * {@link GenerationStep.Decoration#ordinal()} — and an emptied step becomes
     * {@link HolderSet#empty()}, matching
     * {@code DimensionFeatureScope#dropFeatures}. Carvers are copied so the new settings share
     * no mutable lists with the biome's.
     *
     * <p>A key the dimension's descriptor excludes gets no copy there, and a key it gates with
     * {@code feature_predicates} carries that predicate — the same rule
     * {@code AddPhase#predicatedFeatures} applies at startup. A key is only re-injected for a
     * dimension whose own {@code scopedForeign} set still holds it: a key the ADD phase already
     * took over in one dimension must not get a second guarded copy from this pass.
     */
    private static Rebuilt rebuild(BiomeGenerationSettings settings,
                                   Set<ResourceKey<PlacedFeature>> foreign,
                                   Map<ResourceKey<Level>, List<WorldshapeDescriptor>> byDimension,
                                   Map<ResourceKey<Level>, Set<ResourceKey<PlacedFeature>>> scopedForeign) {
        List<HolderSet<PlacedFeature>> steps = settings.features();
        List<HolderSet<PlacedFeature>> rebuilt = new ArrayList<>(steps.size());
        int copies = 0;

        for (HolderSet<PlacedFeature> step : steps) {
            List<Holder<PlacedFeature>> survivors = new ArrayList<>(step.size());
            List<Holder<PlacedFeature>> foreignHolders = new ArrayList<>();
            for (Holder<PlacedFeature> holder : step) {
                ResourceKey<PlacedFeature> key = holder.unwrapKey().orElse(null);
                if (key != null && foreign.contains(key)) {
                    foreignHolders.add(holder);
                } else {
                    survivors.add(holder);
                }
            }
            if (foreignHolders.isEmpty()) {
                rebuilt.add(step);
                continue;
            }
            for (Map.Entry<ResourceKey<Level>, List<WorldshapeDescriptor>> entry : byDimension.entrySet()) {
                Set<ResourceKey<PlacedFeature>> owned = scopedForeign.getOrDefault(entry.getKey(), Set.of());
                for (Holder<PlacedFeature> holder : foreignHolders) {
                    ResourceKey<PlacedFeature> key = holder.unwrapKey().orElse(null);
                    if (key == null || !owned.contains(key)) continue;
                    List<WorldshapeDescriptor> descriptors = entry.getValue();
                    if (excluded(descriptors, key)) continue;
                    survivors.add(Holder.direct(DimensionFeatureScope.guarded(
                            holder.value(), entry.getKey(), predicateFor(descriptors, key))));
                    copies++;
                }
            }
            rebuilt.add(survivors.isEmpty() ? HolderSet.empty() : HolderSet.direct(survivors));
        }

        return new Rebuilt(new BiomeGenerationSettings(copyCarvers(settings), List.copyOf(rebuilt)), copies);
    }

    private static boolean excluded(List<WorldshapeDescriptor> descriptors, ResourceKey<PlacedFeature> key) {
        if (key == null) return false;
        for (WorldshapeDescriptor descriptor : descriptors) {
            if (descriptor.exclusions().features().contains(key)) return true;
        }
        return false;
    }

    private static SpatialPredicate predicateFor(List<WorldshapeDescriptor> descriptors,
                                                 ResourceKey<PlacedFeature> key) {
        if (key == null) return null;
        for (WorldshapeDescriptor descriptor : descriptors) {
            SpatialPredicate predicate = descriptor.featurePredicates().get(key);
            if (predicate != null) return predicate;
        }
        return null;
    }

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
}
