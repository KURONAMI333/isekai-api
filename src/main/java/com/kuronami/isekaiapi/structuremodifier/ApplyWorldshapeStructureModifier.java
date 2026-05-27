package com.kuronami.isekaiapi.structuremodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;

/**
 * A {@link StructureModifier} that applies an Isekai {@link WorldshapeDescriptor}'s
 * structure-related fields. v0.9 scope is intentionally narrow — the only structure-level
 * field the descriptor expresses today is {@link WorldshapeDescriptor#excludedStructures()},
 * which the REMOVE phase enforces by replacing the structure's biomes HolderSet with an
 * empty one. An empty biomes set makes Minecraft never place that structure.
 *
 * <p>Datapack consumers reference this via
 * {@code data/<ns>/neoforge/structure_modifier/foo.json} like:
 *
 * <pre>{@code
 * {
 *   "type": "isekai_api:apply_worldshape_structures",
 *   "worldshape": { ... full WorldshapeDescriptor JSON ... }
 * }
 * }</pre>
 *
 * <p>v0.10+ candidates: forward {@code structurePredicates} into a per-structure spawn
 * gate (would need a custom predicate codec landing in vanilla's StructurePlacement
 * pipeline, since neither BiomeModifier nor StructureModifier reaches spacing/frequency).
 */
public record ApplyWorldshapeStructureModifier(WorldshapeDescriptor worldshape) implements StructureModifier {

    public static final MapCodec<ApplyWorldshapeStructureModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WorldshapeDescriptor.CODEC.fieldOf("worldshape").forGetter(ApplyWorldshapeStructureModifier::worldshape)
    ).apply(i, ApplyWorldshapeStructureModifier::new));

    @Override
    public void modify(Holder<Structure> structure, Phase phase, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (phase != Phase.REMOVE) {
            return;
        }
        if (!isExcluded(structure)) {
            return;
        }
        // Empty biomes HolderSet disables placement entirely — Minecraft tests biome
        // membership before generating, and an empty set matches no biome.
        builder.getStructureSettings().setBiomes(HolderSet.empty());
        IsekaiApi.LOGGER.debug("[Isekai] excluded structure {} via empty biomes (descriptor dim={})",
                structure.unwrapKey().map(k -> k.location().toString()).orElse("?"),
                worldshape.dimension().location());
    }

    private boolean isExcluded(Holder<Structure> structure) {
        return structure.unwrapKey().map(worldshape.exclusions().structures()::contains).orElse(false);
    }

    @Override
    public MapCodec<? extends StructureModifier> codec() {
        return IsekaiStructureModifiers.APPLY_WORLDSHAPE_STRUCTURES.get();
    }
}
