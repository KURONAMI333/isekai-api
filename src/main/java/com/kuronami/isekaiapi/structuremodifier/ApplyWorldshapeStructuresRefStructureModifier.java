package com.kuronami.isekaiapi.structuremodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.common.world.StructureModifier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Reference-based variant of {@link ApplyWorldshapeStructureModifier}. Looks up the active
 * worldshape for the given dimension at modify-time from {@link Isekai#remap()}; consumer
 * datapack reduces to:
 *
 * <pre>{@code
 * {
 *   "type": "isekai_api:apply_worldshape_structures_ref",
 *   "dimension": "minecraft:overworld"
 * }
 * }</pre>
 *
 * <p>Pair with {@code isekai_api:apply_worldshape_ref} on the biome-modifier side.
 */
@ApiStatus.Internal
public record ApplyWorldshapeStructuresRefStructureModifier(ResourceKey<Level> dimension) implements StructureModifier {

    public static final MapCodec<ApplyWorldshapeStructuresRefStructureModifier> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                    .forGetter(ApplyWorldshapeStructuresRefStructureModifier::dimension)
    ).apply(i, ApplyWorldshapeStructuresRefStructureModifier::new));

    @Override
    public void modify(Holder<Structure> structure, Phase phase, ModifiableStructureInfo.StructureInfo.Builder builder) {
        // Layered dims: structure modification has no Y context, so we apply every layer's
        // declared exclusion / spawn-override for this structure (union semantics). This is
        // permissive on purpose — if any layer wants to exclude or re-spawn-override a
        // structure, honour it. Different from biome modifier (which uses first-match per
        // biome) because structures don't have a single "claim" the way biomes do.
        var layers = Isekai.remap().getActiveLayers(dimension);
        if (!layers.isEmpty()) {
            for (var layer : layers) {
                applyOne(layer.descriptor(), structure, phase, builder);
            }
            return;
        }
        WorldshapeDescriptor worldshape = Isekai.remap().getActiveDescriptor(dimension).orElse(null);
        if (worldshape == null) {
            warnMissingOnce(dimension);
            return;
        }
        applyOne(worldshape, structure, phase, builder);
    }

    private void applyOne(WorldshapeDescriptor worldshape, Holder<Structure> structure,
                          Phase phase, ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (phase == Phase.REMOVE) {
            applyExclusion(worldshape, structure, builder);
        } else if (phase == Phase.MODIFY) {
            applySpawnOverrides(worldshape, structure, builder);
        }
    }

    private void applyExclusion(WorldshapeDescriptor worldshape, Holder<Structure> structure,
                                ModifiableStructureInfo.StructureInfo.Builder builder) {
        if (!structure.unwrapKey().map(worldshape.exclusions().structures()::contains).orElse(false)) {
            return;
        }
        builder.getStructureSettings().setBiomes(HolderSet.empty());
        IsekaiApi.LOGGER.debug("[Isekai] excluded structure {} via empty biomes (descriptor dim={}, ref)",
                structure.unwrapKey().map(k -> k.location().toString()).orElse("?"),
                worldshape.dimension().location());
    }

    private void applySpawnOverrides(WorldshapeDescriptor worldshape, Holder<Structure> structure,
                                     ModifiableStructureInfo.StructureInfo.Builder builder) {
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
            IsekaiApi.LOGGER.debug("[Isekai] applied {} spawn override(s) to {} (descriptor dim={}, ref)",
                    applied, key.location(), worldshape.dimension().location());
        }
    }

    @Override
    public MapCodec<? extends StructureModifier> codec() {
        return IsekaiStructureModifiers.APPLY_WORLDSHAPE_STRUCTURES_REF.get();
    }

    private static final java.util.Set<ResourceKey<Level>> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void warnMissingOnce(ResourceKey<Level> dim) {
        if (WARNED.add(dim)) {
            IsekaiApi.LOGGER.warn(
                    "[Isekai] apply_worldshape_structures_ref: no worldshape registered for {} at modify-time. " +
                    "Check data/<ns>/isekai/worldshape/*.json declares this dimension.", dim);
        }
    }
}
