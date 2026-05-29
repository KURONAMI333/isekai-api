package com.kuronami.isekaiapi.structuremodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link StructureModifier} that applies an Isekai {@link WorldshapeDescriptor}'s
 * structure-related fields. Two phases:
 *
 * <ul>
 *   <li>{@link Phase#REMOVE}: drops every structure in {@code exclusions.structures} by
 *       replacing its biomes filter with {@link HolderSet#empty()} so Minecraft never
 *       finds a matching biome.</li>
 *   <li>{@link Phase#MODIFY}: applies every {@code structureSpawnOverrides} entry that
 *       targets this structure — clears the existing (structure, category) override when
 *       {@code replace=true}, then injects the consumer's spawn entries with the
 *       configured bounding-box scope.</li>
 * </ul>
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

    public static final MapCodec<ApplyWorldshapeStructureModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WorldshapeDescriptor.CODEC.fieldOf("worldshape").forGetter(ApplyWorldshapeStructureModifier::worldshape)
    ).apply(i, ApplyWorldshapeStructureModifier::new));

    @Override
    public void modify(Holder<Structure> structure, Phase phase, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (phase == Phase.REMOVE) {
            applyExclusion(structure, builder);
        } else if (phase == Phase.MODIFY) {
            applySpawnOverrides(structure, builder);
        }
    }

    private void applyExclusion(Holder<Structure> structure, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (!structure.unwrapKey().map(worldshape.exclusions().structures()::contains).orElse(false)) {
            return;
        }
        // Empty biomes HolderSet disables placement entirely — Minecraft tests biome
        // membership before generating, and an empty set matches no biome.
        builder.getStructureSettings().setBiomes(HolderSet.empty());
        IsekaiApi.LOGGER.debug("[Isekai] excluded structure {} via empty biomes (descriptor dim={})",
                structure.unwrapKey().map(k -> k.location().toString()).orElse("?"),
                worldshape.dimension().location());
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
