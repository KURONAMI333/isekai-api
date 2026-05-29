package com.kuronami.isekaiapi.api.remap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Declare worldshape transformations. Consumer entry point for the remap pipeline.
 * Implementation reads vanilla rules via Query API, generates wrapped PlacedFeatures /
 * Structures / MobSpawns, and injects them via NeoForge BiomeModifier.
 *
 * <p>Original vanilla rules are <b>never mutated in place</b> — they are read once at
 * {@code ServerAboutToStartEvent}, then replaced by remap-derived equivalents at biome
 * modifier resolution time.
 */
public interface IsekaiRemap {

    /** Single-layer worldshape. */
    void declareWorldshape(WorldshapeDescriptor descriptor);

    /**
     * Multi-layer worldshape stacked along Y. Layers must not overlap in their
     * {@code yRange}; each {@link LayeredDescriptor} carries its own
     * {@link TransitionRule} (Hard / Blend / Gap) controlling the seam to the next layer
     * above it.
     */
    void declareLayeredWorldshape(ResourceKey<Level> dimension,
                                   List<LayeredDescriptor> layers);

    /** Debug helper. Triggers a worldgen reload to re-apply biome modifiers. */
    void updateWorldshape(ResourceKey<Level> dimension, WorldshapeDescriptor newDescriptor);

    /** Withdraw a consumer's declaration. Worldgen reload required. */
    void removeWorldshape(ResourceKey<Level> dimension);

    /** Currently-active single-layer descriptor for the given dimension, if any. */
    Optional<WorldshapeDescriptor> getActiveDescriptor(ResourceKey<Level> dimension);

    /** Currently-active layered descriptors for the given dimension, if any. */
    List<LayeredDescriptor> getActiveLayers(ResourceKey<Level> dimension);

    /** All dimensions that currently have any worldshape declaration (single or layered). */
    Set<ResourceKey<Level>> getDeclaredDimensions();
}
