package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.impl.CarverScope;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.Set;
import org.jetbrains.annotations.ApiStatus;

/**
 * REMOVE-phase logic for {@code isekai_api:apply_worldshape}. One concern remains here:
 * <ul>
 *   <li>{@link #excludedCarvers} — drop every ConfiguredWorldCarver in
 *       {@code exclusions.carvers} from every carving step.</li>
 * </ul>
 *
 * <p>Features used to be removed here as well ({@code exclusions.features} and the originals
 * pending an {@code oreStrategy} remap). Both moved to
 * {@link com.kuronami.isekaiapi.impl.DimensionFeatureScope}, which drops them per dimension at
 * decoration time: this phase runs inside a NeoForge {@code BiomeModifier}, where no dimension
 * is in scope, so a nether descriptor's removals used to erase the same features from every
 * dimension sharing those biomes.
 *
 * <p>Carvers take the same route through {@link CarverScope}: the exclusion is recorded per
 * (dimension, biome) and applied at carve time by {@code NoiseBasedChunkGeneratorCarverMixin},
 * so it no longer erases the carver from every dimension reusing the biome. Only a dimension
 * whose generator the hook cannot reach keeps the old biome-wide edit, because there the
 * alternative is losing the exclusion entirely.
 */
@ApiStatus.Internal
public final class RemovePhase {

    private RemovePhase() {}

    public static void excludedCarvers(WorldshapeDescriptor descriptor,
                                        ResourceKey<Biome> biomeKey,
                                        ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = descriptor.exclusions().carvers();
        if (excluded.isEmpty() || biomeKey == null) return;
        if (CarverScope.hookable(descriptor.dimension())) {
            CarverScope.recordRemovals(descriptor.dimension(), biomeKey, excluded);
            return;
        }
        int removed = removeCarversByKey(builder.getGenerationSettings(), excluded);
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded carvers (dim={})",
                    removed, descriptor.dimension().location());
        }
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
}
