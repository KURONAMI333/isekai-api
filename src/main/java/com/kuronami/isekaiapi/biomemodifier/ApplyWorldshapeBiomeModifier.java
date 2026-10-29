package com.kuronami.isekaiapi.biomemodifier;

import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.biomemodifier.phase.AddPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.BiomeMatcher;
import com.kuronami.isekaiapi.biomemodifier.phase.ModifyPhase;
import com.kuronami.isekaiapi.biomemodifier.phase.RemovePhase;
import com.kuronami.isekaiapi.registry.DeprecatedInlineCodec;
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
 * <p>Biome scoping (the descriptor's {@code applies_to}) is biome-keyed only, because a
 * biome is reusable across dimensions. Dimension scoping is applied by the phases instead:
 * feature additions carry {@code isekai_api:dimension_guard}, feature removals are recorded
 * here and dropped per dimension at decoration time
 * ({@code com.kuronami.isekaiapi.impl.DimensionFeatureScope}), and structure exclusions veto
 * at generation-point search. Mob spawns are recorded as a per-(dimension, biome) rule
 * ({@code com.kuronami.isekaiapi.impl.MobSpawnScope}) and enforced at spawn time; carvers are
 * recorded the same way ({@code com.kuronami.isekaiapi.impl.CarverScope}) and applied at
 * carve time.
 */
@ApiStatus.Internal
public record ApplyWorldshapeBiomeModifier(WorldshapeDescriptor worldshape) implements BiomeModifier {

    public static final MapCodec<ApplyWorldshapeBiomeModifier> MAP_CODEC = DeprecatedInlineCodec.warnOnDecode(
            RecordCodecBuilder.mapCodec(i -> i.group(
                    WorldshapeDescriptor.CODEC.fieldOf("worldshape").forGetter(ApplyWorldshapeBiomeModifier::worldshape)
            ).apply(i, ApplyWorldshapeBiomeModifier::new)),
            "isekai_api:apply_worldshape (inline) is deprecated — declare the worldshape once under "
            + "data/<ns>/isekai/worldshape/<name>.json and use isekai_api:apply_worldshape_ref instead. "
            + "The inline form keeps working for now.");

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (!BiomeMatcher.matches(worldshape, biome)) {
            return;
        }
        // Biome key drives the per-biome scoping of ore re-injection in ADD — without it,
        // every matched biome would receive every snapshot ore (incl. modded ores).
        ResourceKey<Biome> biomeKey = biome.unwrapKey().orElse(null);
        switch (phase) {
            case REMOVE -> {
                // Feature removals are dimension-scoped at decoration time
                // (DimensionFeatureScope); carvers are recorded per (dimension, biome) and
                // applied at carve time (CarverScope), so neither edits the shared biome.
                RemovePhase.excludedCarvers(worldshape, biomeKey, builder);
            }
            case ADD -> {
                AddPhase.registerFeatureScope(worldshape, biomeKey);
                AddPhase.additionalFeatures(worldshape, builder);
                AddPhase.additionalCarvers(worldshape, biomeKey, builder);
                AddPhase.remappedOreFeatures(worldshape, biomeKey, builder);
                AddPhase.predicatedFeatures(worldshape, biomeKey, builder);
            }
            case MODIFY -> {
                ModifyPhase.mobSpawnStrategy(worldshape, biome);
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
