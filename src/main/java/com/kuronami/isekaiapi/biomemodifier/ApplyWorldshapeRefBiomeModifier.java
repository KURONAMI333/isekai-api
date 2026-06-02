package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.AddPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.BiomeMatcher;
import com.kuronami.isekaiapi.biomemodifier.phase.ModifyPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.RemovePhase;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.jetbrains.annotations.ApiStatus;

/**
 * Reference-based variant of {@link ApplyWorldshapeBiomeModifier}. Looks up the active
 * worldshape for the given dimension at modify-time from {@link Isekai#remap()} — so the
 * descriptor itself lives only in {@code data/<ns>/isekai/worldshape/<name>.json}, and
 * the consumer's biome_modifier file shrinks to:
 *
 * <pre>{@code
 * {
 *   "type": "isekai_api:apply_worldshape_ref",
 *   "dimension": "minecraft:overworld"
 * }
 * }</pre>
 *
 * <p>If no worldshape is registered for the dimension at modify-time (e.g. the JSON
 * failed to load), this modifier is a no-op and logs a single warn per dimension.
 *
 * <p>Pair this with {@code isekai_api:apply_worldshape_structures_ref} on the
 * structure-modifier side for the same dimension.
 */
@ApiStatus.Internal
public record ApplyWorldshapeRefBiomeModifier(ResourceKey<Level> dimension) implements BiomeModifier {

    public static final MapCodec<ApplyWorldshapeRefBiomeModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .forGetter(ApplyWorldshapeRefBiomeModifier::dimension)
    ).apply(i, ApplyWorldshapeRefBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        // Layered dims: at biome-modifier time we have no Y context, so we can't pick by Y.
        // Instead we walk layers in declaration order and apply the FIRST layer whose
        // applies_to claims this biome. A biome belongs to at most one layer; non-claimed
        // biomes are untouched. This pairs with the Y-aware surface/structure paths so the
        // visual result of a layered world is consistent: layer 1's blocks at layer 1's Y,
        // layer 2's blocks at layer 2's Y, layer-specific biome modifications applied to
        // biomes that layer claims.
        WorldshapeDescriptor worldshape = pickDescriptorForBiome(biome);
        if (worldshape == null) {
            warnMissingOnce(dimension);
            return;
        }
        if (!BiomeMatcher.matches(worldshape, biome)) {
            return;
        }
        ResourceKey<Biome> biomeKey = biome.unwrapKey().orElse(null);
        switch (phase) {
            case REMOVE -> {
                RemovePhase.excludedFeatures(worldshape, builder);
                RemovePhase.excludedCarvers(worldshape, builder);
                RemovePhase.originalsPendingRemap(worldshape, biomeKey, builder);
            }
            case ADD -> {
                AddPhase.additionalFeatures(worldshape, builder);
                AddPhase.additionalCarvers(worldshape, builder);
                AddPhase.remappedOreFeatures(worldshape, biomeKey, builder);
            }
            case MODIFY -> {
                ModifyPhase.mobSpawnStrategy(worldshape, builder);
                ModifyPhase.featurePredicates(worldshape, builder);
                ModifyPhase.atmosphereOverride(worldshape, builder);
            }
            default -> { /* BEFORE_EVERYTHING / AFTER_EVERYTHING — no-op */ }
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return IsekaiBiomeModifiers.APPLY_WORLDSHAPE_REF.get();
    }

    /**
     * Resolve the descriptor that should govern this biome's modification: layered dims pick
     * the first layer whose {@code applies_to} claims the biome; single-descriptor dims fall
     * back to {@link Isekai#remap()}'s {@code getActiveDescriptor}.
     */
    private WorldshapeDescriptor pickDescriptorForBiome(Holder<Biome> biome) {
        var layers = Isekai.remap().getActiveLayers(dimension);
        if (!layers.isEmpty()) {
            for (var layer : layers) {
                if (BiomeMatcher.matches(layer.descriptor(), biome)) {
                    return layer.descriptor();
                }
            }
            return null;
        }
        return Isekai.remap().getActiveDescriptor(dimension).orElse(null);
    }

    private static final java.util.Set<ResourceKey<Level>> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnMissingOnce(ResourceKey<Level> dim) {
        if (WARNED.add(dim)) {
            IsekaiApi.LOGGER.warn(
                    "[Isekai] apply_worldshape_ref: no worldshape registered for {} at modify-time. " +
                    "Check data/<ns>/isekai/worldshape/*.json declares this dimension.", dim);
        }
    }
}
