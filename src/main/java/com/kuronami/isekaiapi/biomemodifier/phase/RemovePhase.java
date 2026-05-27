package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.query.PlacedFeatureInfo;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.HashSet;
import java.util.Set;

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
public final class RemovePhase {

    private RemovePhase() {}

    public static void excludedFeatures(WorldshapeDescriptor descriptor,
                                         ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = descriptor.exclusions().features();
        if (excluded.isEmpty()) return;
        var generation = builder.getGenerationSettings();
        int removed = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            var stepFeatures = generation.getFeatures(step);
            int before = stepFeatures.size();
            stepFeatures.removeIf(holder ->
                    holder.unwrapKey().map(excluded::contains).orElse(false));
            removed += before - stepFeatures.size();
        }
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded placed features (dim={})",
                    removed, descriptor.dimension().location());
        }
    }

    public static void excludedCarvers(WorldshapeDescriptor descriptor,
                                        ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = descriptor.exclusions().carvers();
        if (excluded.isEmpty()) return;
        var generation = builder.getGenerationSettings();
        int removed = 0;
        for (var step : GenerationStep.Carving.values()) {
            var stepCarvers = generation.getCarvers(step);
            int before = stepCarvers.size();
            stepCarvers.removeIf(holder ->
                    holder.unwrapKey().map(excluded::contains).orElse(false));
            removed += before - stepCarvers.size();
        }
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded carvers (dim={})",
                    removed, descriptor.dimension().location());
        }
    }

    public static void originalsPendingRemap(WorldshapeDescriptor descriptor,
                                              ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (descriptor.oreStrategy() instanceof RemapStrategy.Identity) return;
        Set<ResourceKey<PlacedFeature>> remapTargets = collectRemapTargets();
        if (remapTargets.isEmpty()) return;
        var generation = builder.getGenerationSettings();
        int removed = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            var stepFeatures = generation.getFeatures(step);
            int before = stepFeatures.size();
            stepFeatures.removeIf(holder ->
                    holder.unwrapKey().map(remapTargets::contains).orElse(false));
            removed += before - stepFeatures.size();
        }
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} originals pending remap (dim={})",
                    removed, descriptor.dimension().location());
        }
    }

    private static Set<ResourceKey<PlacedFeature>> collectRemapTargets() {
        VanillaRuleSnapshot snapshot = Isekai.currentSnapshot();
        if (snapshot == null) return Set.of();
        Set<ResourceKey<PlacedFeature>> set = new HashSet<>();
        for (PlacedFeatureInfo info : snapshot.ores()) {
            if (snapshot.isFallback(info)) continue;
            set.add(info.key());
        }
        return set;
    }
}
