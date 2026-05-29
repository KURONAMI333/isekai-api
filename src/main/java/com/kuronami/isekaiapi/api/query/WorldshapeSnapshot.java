package com.kuronami.isekaiapi.api.query;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Per-dimension worldgen snapshot returned by
 * {@link IsekaiQuery#getSnapshot(ResourceKey)}. Contains the dimension's resolved
 * PlacedFeatures, structure placements, and mob spawn entries — all immutable copies.
 *
 * <p>Diagnostic-only: this view describes the input rules a remap would see, not the
 * post-modifier result that actually generates.
 */
public record WorldshapeSnapshot(
        ResourceKey<Level> dimension,
        List<PlacedFeatureInfo> placedFeatures,
        List<StructurePlacementInfo> structures,
        List<MobSpawnInfo> mobs
) {
    public WorldshapeSnapshot {
        placedFeatures = List.copyOf(placedFeatures);
        structures = List.copyOf(structures);
        mobs = List.copyOf(mobs);
    }

    public boolean isEmpty() {
        return placedFeatures.isEmpty() && structures.isEmpty() && mobs.isEmpty();
    }
}
