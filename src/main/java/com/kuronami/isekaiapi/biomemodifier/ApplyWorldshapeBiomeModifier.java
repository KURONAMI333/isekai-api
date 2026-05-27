package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * A {@link BiomeModifier} that applies an entire Isekai {@link WorldshapeDescriptor}
 * to matching biomes. Datapack consumers reference this via
 * {@code data/<ns>/neoforge/biome_modifier/foo.json} like:
 *
 * <pre>{@code
 * {
 *   "type": "isekai_api:apply_worldshape",
 *   "worldshape": {
 *     // ... full WorldshapeDescriptor JSON (same shape as data/<ns>/isekai/worldshape/*.json)
 *   }
 * }
 * }</pre>
 *
 * <p>v0.6 behavior — minimum viable: the REMOVE phase drops every
 * {@link WorldshapeDescriptor#excludedFeatures()} from the biome's generation settings
 * across all decoration steps. v0.7 will add the ADD phase (remap-derived replacement
 * features) and mob spawn scaling per {@code mobSpawnStrategy}.
 *
 * <p>The {@code appliesTo} filter is biome-keyed only. Dimension scoping is not
 * representable through {@link BiomeModifier} alone — biomes are reusable across
 * dimensions. Consumers wanting "only in the overworld" should either list every
 * overworld biome explicitly, or pair this modifier with a biome tag.
 */
public record ApplyWorldshapeBiomeModifier(WorldshapeDescriptor worldshape) implements BiomeModifier {

    public static final MapCodec<ApplyWorldshapeBiomeModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WorldshapeDescriptor.CODEC.fieldOf("worldshape").forGetter(ApplyWorldshapeBiomeModifier::worldshape)
    ).apply(i, ApplyWorldshapeBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (!matches(biome)) {
            return;
        }
        if (phase == Phase.REMOVE) {
            removeExcludedFeatures(builder);
        }
        // v0.7: Phase.ADD for ore/structure/mob_spawn remap output;
        // Phase.MODIFY for climate/visual adjustments (not in scope here yet).
    }

    /**
     * @return {@code true} if this biome matches the descriptor's filter.
     *         Empty {@code appliesTo} = match every biome (caller controls scope via
     *         biome tags or by listing explicit biomes).
     */
    private boolean matches(Holder<Biome> biome) {
        var filter = worldshape.appliesTo();
        if (filter.isEmpty()) {
            return true;
        }
        for (var key : filter) {
            if (biome.is(key)) {
                return true;
            }
        }
        return false;
    }

    private void removeExcludedFeatures(ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        var excluded = worldshape.excludedFeatures();
        if (excluded.isEmpty()) {
            return;
        }
        var generation = builder.getGenerationSettings();
        int removed = 0;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            // Each step holds a HolderSet<PlacedFeature>; getFeatures returns the mutable
            // backing list of holders.
            var stepFeatures = generation.getFeatures(step);
            int sizeBefore = stepFeatures.size();
            stepFeatures.removeIf(holder -> {
                var optKey = holder.unwrapKey();
                return optKey.isPresent() && excluded.contains(optKey.get());
            });
            removed += sizeBefore - stepFeatures.size();
        }
        if (removed > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] removed {} excluded placed features (descriptor dim={})",
                    removed, worldshape.dimension().location());
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return IsekaiBiomeModifiers.APPLY_WORLDSHAPE.get();
    }
}
