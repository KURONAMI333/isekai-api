package com.kuronami.isekaiapi.structuremodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.kuronami.isekaiapi.registry.DeprecatedInlineCodec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link StructureModifier} that applies an Isekai {@link WorldshapeDescriptor}'s
 * {@code structureSpawnOverrides} at {@link Phase#MODIFY}: it clears the existing
 * (structure, category) override when {@code replace=true}, then injects the consumer's
 * spawn entries with the configured bounding-box scope.
 *
 * <p>{@code exclusions.structures} is <strong>not</strong> applied here. A structure
 * modifier has no dimension context, so emptying the biome filter removed the structure
 * from every dimension that shares those biomes. Exclusions are vetoed per dimension in
 * {@code StructureFindValidGenerationPointMixin} instead.
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
 */
@ApiStatus.Internal
public record ApplyWorldshapeStructureModifier(WorldshapeDescriptor worldshape) implements StructureModifier {

    public static final MapCodec<ApplyWorldshapeStructureModifier> MAP_CODEC = DeprecatedInlineCodec.warnOnDecode(
            RecordCodecBuilder.mapCodec(i -> i.group(
                    WorldshapeDescriptor.CODEC.fieldOf("worldshape").forGetter(ApplyWorldshapeStructureModifier::worldshape)
            ).apply(i, ApplyWorldshapeStructureModifier::new)),
            "isekai_api:apply_worldshape_structures (inline) is deprecated — declare the worldshape once "
            + "under data/<ns>/isekai/worldshape/<name>.json and use isekai_api:apply_worldshape_structures_ref "
            + "instead. The inline form keeps working for now.");

    @Override
    public void modify(Holder<Structure> structure, Phase phase, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (phase == Phase.MODIFY) {
            applySpawnOverrides(structure, builder);
        }
    }

    private void applySpawnOverrides(Holder<Structure> structure, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (worldshape.structureSpawnOverrides().isEmpty()) return;
        ResourceKey<Structure> key = structure.unwrapKey().orElse(null);
        if (key == null) return;
        var settings = builder.getStructureSettings();
        int applied = 0;
        for (var config : worldshape.structureSpawnOverrides()) {
            if (!config.structure().equals(key)) continue;
            if (config.replace()) {
                settings.removeSpawnOverrides(config.category());
            }
            var spawnBuilder = settings.getOrAddSpawnOverrides(config.category());
            spawnBuilder.setBoundingBox(config.boundingBox());
            for (var spawn : config.spawns()) {
                spawnBuilder.addSpawn(new MobSpawnSettings.SpawnerData(
                        spawn.type(), spawn.weight(), spawn.minCount(), spawn.maxCount()));
            }
            applied++;
        }
        if (applied > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] applied {} spawn override(s) to {} (descriptor dim={})",
                    applied, key.location(), worldshape.dimension().location());
        }
    }

    @Override
    public MapCodec<? extends StructureModifier> codec() {
        return IsekaiStructureModifiers.APPLY_WORLDSHAPE_STRUCTURES.get();
    }
}
