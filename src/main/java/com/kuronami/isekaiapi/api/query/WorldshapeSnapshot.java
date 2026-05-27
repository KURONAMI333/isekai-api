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
    public WorldshapeSnapshot {
        ores = List.copyOf(ores);
        structures = List.copyOf(structures);
        mobs = List.copyOf(mobs);
    }

    public boolean isEmpty() {
        return ores.isEmpty() && structures.isEmpty() && mobs.isEmpty();
    }
}
