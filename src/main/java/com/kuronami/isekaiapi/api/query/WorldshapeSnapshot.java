package com.kuronami.isekaiapi.api.query;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

public record WorldshapeSnapshot(
        ResourceKey<Level> dimension,
        List<PlacedFeatureInfo> ores,
        List<StructurePlacementInfo> structures,
        List<MobSpawnInfo> mobs
) {
    public static WorldshapeSnapshot of(ResourceKey<Level> dimension,
                                         List<PlacedFeatureInfo> ores,
                                         List<StructurePlacementInfo> structures,
                                         List<MobSpawnInfo> mobs) {
        return new WorldshapeSnapshot(
                dimension,
                List.copyOf(ores),
                List.copyOf(structures),
                List.copyOf(mobs)
        );
    }

    public boolean isEmpty() {
        return ores.isEmpty() && structures.isEmpty() && mobs.isEmpty();
    }
}
