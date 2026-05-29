package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.IsekaiInternal;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * REMOVE-phase logic for {@code isekai_api:apply_worldshape}. Three concerns, each a
 * separate static method:
 * <ul>
 *   <li>{@link #excludedFeatures} — drop every PlacedFeature in {@code exclusions.features}
 *       from every decoration step.</li>
 *   <li>{@link #excludedCarvers} — drop every ConfiguredWorldCarver in
 *       {@code exclusions.carvers} from every carving step.</li>
 *   <li>{@link #originalsPendingRemap} — drop every snapshot-known ore feature whose
 *       VerticalRange the ADD phase will re-inject under the active {@code oreStrategy}.
 *       Skipped when the strategy is {@link RemapStrategy.Identity}.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class RemovePhase {

    private RemovePhase() {}

    public static void excludedFeatures(WorldshapeDescriptor descriptor,
                                         ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = descriptor.exclusions().features();
        if (excluded.isEmpty()) return;
        int removed = removeFeaturesByKey(builder.getGenerationSettings(), excluded);
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded placed features (dim={})",
                    removed, descriptor.dimension().location());
        }
    }

    public static void excludedCarvers(WorldshapeDescriptor descriptor,
                                        ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = descriptor.exclusions().carvers();
        if (excluded.isEmpty()) return;
        int removed = removeCarversByKey(builder.getGenerationSettings(), excluded);
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded carvers (dim={})",
                    removed, descriptor.dimension().location());
        }
    }

    public static void originalsPendingRemap(WorldshapeDescriptor descriptor,
                                              ResourceKey<Biome> biomeKey,
                                              ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (descriptor.oreStrategy() instanceof RemapStrategy.Identity) return;
        if (biomeKey == null) return;
        Set<ResourceKey<PlacedFeature>> remapTargets = collectRemapTargets(biomeKey);
        if (remapTargets.isEmpty()) return;
        int removed = removeFeaturesByKey(builder.getGenerationSettings(), remapTargets);
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} originals pending remap in biome {} (dim={})",
                    removed, biomeKey.location(), descriptor.dimension().location());
        }
    }

    /**
     * Drop every placed feature whose key is in {@code targets} from every decoration step
     * of {@code gen}. Returns the total number of entries removed.
     */
    private static int removeFeaturesByKey(BiomeGenerationSettingsBuilder gen,
                                            Set<ResourceKey<PlacedFeature>> targets) {
        int removed = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            var stepFeatures = gen.getFeatures(step);
            int before = stepFeatures.size();
            stepFeatures.removeIf(holder -> matchesKey(holder, targets));
            removed += before - stepFeatures.size();
        }
        return removed;
    }

    /**
     * Drop every carver whose key is in {@code targets} from every carving step of
     * {@code gen}. Returns the total number of entries removed.
     */
    private static int removeCarversByKey(BiomeGenerationSettingsBuilder gen,
                                           Set<ResourceKey<ConfiguredWorldCarver<?>>> targets) {
        int removed = 0;
        for (var step : GenerationStep.Carving.values()) {
            var stepCarvers = gen.getCarvers(step);
            int before = stepCarvers.size();
            stepCarvers.removeIf(holder -> matchesKey(holder, targets));
            removed += before - stepCarvers.size();
        }
        return removed;
    }

    private static <T> boolean matchesKey(Holder<T> holder, Set<ResourceKey<T>> targets) {
        return holder.unwrapKey().map(targets::contains).orElse(false);
    }

    /**
     * Intersection of "ores tracked in the snapshot" ∩ "features originally in this biome".
     * Scoping by biome ensures the matching ADD phase only re-injects features the biome
     * actually had — pair invariant for the per-biome remap pipeline.
     */
    private static Set<ResourceKey<PlacedFeature>> collectRemapTargets(ResourceKey<Biome> biomeKey) {
        VanillaRuleSnapshot snapshot = IsekaiInternal.currentSnapshot();
        if (snapshot == null) return Set.of();
        Set<ResourceKey<PlacedFeature>> inBiome = snapshot.featuresInBiome(biomeKey);
        if (inBiome.isEmpty()) return Set.of();
        Set<ResourceKey<PlacedFeature>> set = new HashSet<>();
        for (PlacedFeatureInfo info : snapshot.placedFeatures()) {
            if (snapshot.isFallback(info)) continue;
            if (!inBiome.contains(info.key())) continue;
            set.add(info.key());
        }
        return set;
    }
}
