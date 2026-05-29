package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.AddPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.BiomeMatcher;
import com.kuronami.isekaiapi.biomemodifier.phase.ModifyPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.RemovePhase;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import org.jetbrains.annotations.ApiStatus;

/**
 * Applies an entire Isekai {@link WorldshapeDescriptor} to matching biomes via a single
 * {@link BiomeModifier} entry. Datapack consumers reference this through
 * {@code data/<ns>/neoforge/biome_modifier/foo.json} with:
 *
 * <pre>{@code
 * {
 *   "type": "isekai_api:apply_worldshape",
 *   "worldshape": { ... full WorldshapeDescriptor JSON ... }
 * }
 * }</pre>
 *
 * <p>The orchestration in {@link #modify} stays small: filter the biome via
 * {@link BiomeMatcher}, then delegate to the phase classes
 * ({@link RemovePhase} / {@link AddPhase} / {@link ModifyPhase}) which own the actual
 * work. Each phase class is independently testable and groups its concerns by lifecycle
 * stage rather than by content kind.
 *
 * <p>Biome scoping (the descriptor's {@code applies_to}) is biome-keyed only. Dimension
 * scoping isn't representable through {@link BiomeModifier} alone since biomes are
 * reusable across dimensions — consumers wanting "only in the overworld" should either
 * list every overworld biome explicitly, or pair this modifier with a biome tag.
 */
@ApiStatus.Internal
public record ApplyWorldshapeBiomeModifier(WorldshapeDescriptor worldshape) implements BiomeModifier {

    public static final MapCodec<ApplyWorldshapeBiomeModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WorldshapeDescriptor.CODEC.fieldOf("worldshape").forGetter(ApplyWorldshapeBiomeModifier::worldshape)
    ).apply(i, ApplyWorldshapeBiomeModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (!BiomeMatcher.matches(worldshape, biome)) {
            return;
        }
        // Biome key drives the per-biome scoping of ore re-injection in ADD/REMOVE — without
        // it, every matched biome would receive every snapshot ore (incl. modded ores).
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
        return IsekaiBiomeModifiers.APPLY_WORLDSHAPE.get();
    }
}
