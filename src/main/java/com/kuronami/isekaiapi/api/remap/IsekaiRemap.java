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
 * @since 1.0.0
 */
public interface IsekaiRemap {

    /** Single-layer worldshape. @since 1.0.0 */
    void declareWorldshape(WorldshapeDescriptor descriptor);

    /**
     * Multi-layer worldshape stacked along Y. Layers must not overlap in their
     * {@code yRange}; each {@link LayeredDescriptor} carries its own
     * {@link TransitionRule} (Hard / Blend / Gap) controlling the seam to the next layer
     * above it.
     */
    void declareLayeredWorldshape(ResourceKey<Level> dimension,
                                   List<LayeredDescriptor> layers);

    /** Debug helper. Triggers a worldgen reload to re-apply biome modifiers. @since 1.0.0 */
    void updateWorldshape(ResourceKey<Level> dimension, WorldshapeDescriptor newDescriptor);

    /** Withdraw a consumer's declaration. Worldgen reload required. @since 1.0.0 */
    void removeWorldshape(ResourceKey<Level> dimension);

    /** Currently-active single-layer descriptor for the given dimension, if any. @since 1.0.0 */
    Optional<WorldshapeDescriptor> getActiveDescriptor(ResourceKey<Level> dimension);

    /** Currently-active layered descriptors for the given dimension, if any. @since 1.0.0 */
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
     * <p>Position-independent, so {@link TransitionRule.Blend} seams resolve as if they were
     * {@link TransitionRule.Hard} — a blend is a spatial interleave and cannot be expressed
     * from Y alone. {@link TransitionRule.Gap} does apply, being a pure Y interval. Callers
     * that know X and Z should use {@link #getDescriptorAt(ResourceKey, int, int, int)}.
     *
     * @return descriptor active at this Y, or empty if neither layer nor single descriptor
     *         applies
     * @since 1.0.0
     */
    Optional<WorldshapeDescriptor> getDescriptorAt(ResourceKey<Level> dimension, int y);

    /**
     * The descriptor that applies at a specific block position. Same resolution as
     * {@link #getDescriptorAt(ResourceKey, int)}, with the layer seams evaluated per position
     * so that {@link TransitionRule.Blend} interleaves the two neighbouring descriptors across
     * its band and {@link TransitionRule.Gap} opens empty space below the seam.
     *
     * <p>The per-position choice is a pure hash of the coordinates and the seam Y, so it is
     * stable across runs, saves and chunk regeneration.
     *
     * @return descriptor active at this position, or empty if neither layer nor single
     *         descriptor applies
     * @since 2.1.0
     */
    default Optional<WorldshapeDescriptor> getDescriptorAt(ResourceKey<Level> dimension, int x, int y, int z) {
        return getDescriptorAt(dimension, y);
    }

    /** All dimensions that currently have any worldshape declaration (single or layered). @since 1.0.0 */
    Set<ResourceKey<Level>> getDeclaredDimensions();
}
