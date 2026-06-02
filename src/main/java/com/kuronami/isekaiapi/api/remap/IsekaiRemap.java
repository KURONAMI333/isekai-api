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

    /**
     * The descriptor that applies at a specific Y in the dimension. Encapsulates the layered
     * vs single-layer decision so runtime consumers (surface rules, structure placement
     * mixins) can ask "which descriptor applies at this Y?" without knowing whether the dim
     * was declared as a single descriptor or as layers.
     *
     * <p>Resolution: if {@link #getActiveLayers(ResourceKey)} returns layers, pick the layer
     * whose {@link LayeredDescriptor#yRange()} contains {@code y} (half-open
     * {@code [minY, maxY)}) and return that layer's descriptor; if no layer covers {@code y}
     * (a gap), return empty. If no layered declaration exists for the dimension, fall back
     * to {@link #getActiveDescriptor(ResourceKey)}.
     *
     * @return descriptor active at this Y, or empty if neither layer nor single descriptor
     *         applies
     */
    Optional<WorldshapeDescriptor> getDescriptorAt(ResourceKey<Level> dimension, int y);

    /** All dimensions that currently have any worldshape declaration (single or layered). */
    Set<ResourceKey<Level>> getDeclaredDimensions();
}
