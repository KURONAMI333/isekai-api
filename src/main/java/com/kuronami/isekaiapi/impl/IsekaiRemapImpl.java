package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.query.DimensionProfile;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus;

/**
 * Concurrent in-memory registry of consumer-declared worldshape descriptors. A dimension can
 * hold more than one single-layer descriptor: selectable world types share a dimension key
 * (e.g. {@code minecraft:overworld}) while shipping a different {@code dimension_type}, so the
 * descriptors are keyed by {@link com.kuronami.isekaiapi.api.remap.Scope} within a dimension
 * and the active one is chosen from the level's actual dimension type at read time.
 *
 * <p>Declarations with the same scope keep the pre-3.0.0 tie rule (higher {@link
 * WorldshapeDescriptor#priority()} wins, equal or higher replaces). Declarations with different
 * scopes coexist; {@link #getActiveDescriptor(ResourceKey)} returns the one whose scope accepts
 * the level's dimension type, preferring a restricted descriptor over an unrestricted one and,
 * among equals, the higher priority.
 *
 * <p>Reads are O(1) via the two ConcurrentHashMaps (selection is O(declarations per dimension));
 * writes copy the per-dimension list, so readers always see an immutable snapshot. The maps
 * are the canonical store backing both the Java {@link IsekaiRemap} API and the JSON
 * reload pipeline ({@link com.kuronami.isekaiapi.lifecycle.IsekaiReloadListener}).
 *
 * <p>Actual chunk-gen effect of declared descriptors flows through NeoForge biome /
 * structure modifiers ({@link com.kuronami.isekaiapi.biomemodifier.ApplyWorldshapeBiomeModifier}
 * and {@link com.kuronami.isekaiapi.structuremodifier.ApplyWorldshapeStructureModifier}) — this
 * class only owns the registry, not the application.
 */
@ApiStatus.Internal
public final class IsekaiRemapImpl implements IsekaiRemap {

    private final Map<ResourceKey<Level>, List<WorldshapeDescriptor>> singleLayer = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, List<LayeredDescriptor>> multiLayer = new ConcurrentHashMap<>();

    @Override
    public void declareWorldshape(WorldshapeDescriptor descriptor) {
        singleLayer.compute(descriptor.dimension(), (k, existing) -> {
            List<WorldshapeDescriptor> current = existing == null ? List.of() : existing;
            int index = indexOfScope(current, descriptor);
            if (index < 0) {
                List<WorldshapeDescriptor> next = new ArrayList<>(current.size() + 1);
                next.addAll(current);
                next.add(descriptor);
                IsekaiApi.LOGGER.info("[Isekai] declareWorldshape: dim={}, range={}, priority={}",
                        descriptor.dimension(), descriptor.playableRange(), descriptor.priority());
                return List.copyOf(next);
            }

            WorldshapeDescriptor sameScope = current.get(index);
            if (sameScope.priority() > descriptor.priority()) {
                IsekaiApi.LOGGER.warn(
                        "[Isekai] Skipping declareWorldshape for {}: existing priority {} > new priority {}",
                        descriptor.dimension(), sameScope.priority(), descriptor.priority());
                return current;
            }
            IsekaiApi.LOGGER.warn(
                    "[Isekai] Replacing single-layer descriptor for {} (priority {} -> {})",
                    descriptor.dimension(), sameScope.priority(), descriptor.priority());
            List<WorldshapeDescriptor> next = new ArrayList<>(current);
            next.set(index, descriptor);
            return List.copyOf(next);
        });
    }

    @Override
    public void declareLayeredWorldshape(ResourceKey<Level> dimension,
                                          List<LayeredDescriptor> layers) {
        multiLayer.put(dimension, List.copyOf(layers));
        IsekaiApi.LOGGER.info("[Isekai] declareLayeredWorldshape: dim={}, layers={}",
                dimension, layers.size());
    }

    @Override
    public void updateWorldshape(ResourceKey<Level> dimension, WorldshapeDescriptor newDescriptor) {
        singleLayer.compute(dimension, (k, existing) -> {
            List<WorldshapeDescriptor> current = existing == null ? List.of() : existing;
            int index = indexOfScope(current, newDescriptor);
            if (index < 0) {
                List<WorldshapeDescriptor> next = new ArrayList<>(current.size() + 1);
                next.addAll(current);
                next.add(newDescriptor);
                return List.copyOf(next);
            }
            List<WorldshapeDescriptor> next = new ArrayList<>(current);
            next.set(index, newDescriptor);
            return List.copyOf(next);
        });
        IsekaiApi.LOGGER.info("[Isekai] updateWorldshape: dim={}", dimension);
    }

    @Override
    public void removeWorldshape(ResourceKey<Level> dimension) {
        singleLayer.remove(dimension);
        multiLayer.remove(dimension);
        IsekaiApi.LOGGER.info("[Isekai] removeWorldshape: dim={}", dimension);
    }

    @Override
    public Optional<WorldshapeDescriptor> getActiveDescriptor(ResourceKey<Level> dimension) {
        return getActiveDescriptor(dimension, IsekaiRemapImpl::lookupDimensionType);
    }

    /**
     * Package-private seam for tests: {@code typeLookup} returns the level's dimension type, or
     * {@code null} when it cannot be determined (no snapshot / no {@code LevelStem} entry).
     */
    Optional<WorldshapeDescriptor> getActiveDescriptor(
            ResourceKey<Level> dimension,
            Function<ResourceKey<Level>, ResourceKey<DimensionType>> typeLookup) {
        List<WorldshapeDescriptor> candidates = singleLayer.get(dimension);
        if (candidates == null || candidates.isEmpty()) return Optional.empty();

        boolean anyRestricted = candidates.stream()
                .anyMatch(candidate -> !candidate.scope().isUnrestricted());
        if (!anyRestricted) return Optional.of(best(candidates));
        return selectForType(candidates, typeLookup.apply(dimension));
    }

    /**
     * Pick the declaration that governs a level of {@code levelType}.
     *
     * <p>Rules, in order:
     * <ol>
     *   <li>A restricted descriptor whose scope accepts {@code levelType} beats an
     *       unrestricted one; among equals the higher priority wins.</li>
     *   <li>When the type is unknown, an unrestricted descriptor still applies. A lone
     *       restricted descriptor also applies (fail-open, the pre-3.0.0 single-declaration
     *       behaviour).</li>
     *   <li>When the type is unknown and two or more restricted descriptors are declared, the
     *       choice is ambiguous — return empty rather than applying the wrong world's shape.</li>
     * </ol>
     */
    static Optional<WorldshapeDescriptor> selectForType(List<WorldshapeDescriptor> candidates,
                                                        ResourceKey<DimensionType> levelType) {
        List<WorldshapeDescriptor> matched = new ArrayList<>();
        List<WorldshapeDescriptor> unknown = new ArrayList<>();
        for (WorldshapeDescriptor candidate : candidates) {
            if (candidate.scope().isUnrestricted()) {
                matched.add(candidate);
            } else if (levelType == null) {
                unknown.add(candidate);
            } else if (candidate.scope().accepts(levelType)) {
                matched.add(candidate);
            }
        }
        if (!matched.isEmpty()) return Optional.of(best(matched));
        if (unknown.size() == 1) return Optional.of(unknown.get(0));
        return Optional.empty();
    }

    /** Restricted beats unrestricted; otherwise the higher priority wins. Ties keep declaration order. */
    private static WorldshapeDescriptor best(List<WorldshapeDescriptor> candidates) {
        WorldshapeDescriptor best = null;
        for (WorldshapeDescriptor candidate : candidates) {
            if (best == null || isBetter(candidate, best)) best = candidate;
        }
        return best;
    }

    private static boolean isBetter(WorldshapeDescriptor candidate, WorldshapeDescriptor current) {
        boolean candidateUnrestricted = candidate.scope().isUnrestricted();
        boolean currentUnrestricted = current.scope().isUnrestricted();
        if (candidateUnrestricted != currentUnrestricted) return !candidateUnrestricted;
        return candidate.priority() > current.priority();
    }

    private static int indexOfScope(List<WorldshapeDescriptor> descriptors,
                                    WorldshapeDescriptor descriptor) {
        for (int i = 0; i < descriptors.size(); i++) {
            if (descriptors.get(i).scope().equals(descriptor.scope())) return i;
        }
        return -1;
    }

    /** The level's dimension type, or {@code null} when the snapshot has no profile for it. */
    private static ResourceKey<DimensionType> lookupDimensionType(ResourceKey<Level> dimension) {
        var snapshot = IsekaiInternal.currentSnapshot();
        if (snapshot == null) return null;
        return snapshot.dimensionProfile(dimension)
                .map(DimensionProfile::dimensionType)
                .orElse(null);
    }

    @Override
    public List<LayeredDescriptor> getActiveLayers(ResourceKey<Level> dimension) {
        List<LayeredDescriptor> layers = multiLayer.get(dimension);
        if (layers == null || layers.isEmpty()) return List.of();
        return layers.stream()
                .filter(layer -> appliesToLevel(layer.descriptor()))
                .toList();
    }

    /**
     * {@code true} when {@code descriptor} was written for the dimension type this level uses.
     *
     * <p>A selectable world type ships its own {@code LevelStem}, so {@code minecraft:overworld}
     * can be a different world than vanilla's overworld. Descriptors that declare no
     * {@code required_dimension_types} keep applying everywhere (pre-3.0.0 behaviour), and a
     * dimension missing from the snapshot fails open rather than silently dropping the shape.
     *
     * <p>This per-descriptor predicate is used for layered declarations only. Single-layer
     * selection uses {@link #selectForType(List, ResourceKey)} because it must also resolve
     * ambiguity between two restricted declarations.
     */
    private static boolean appliesToLevel(WorldshapeDescriptor descriptor) {
        if (descriptor == null || descriptor.scope().isUnrestricted()) return true;
        ResourceKey<DimensionType> levelType = lookupDimensionType(descriptor.dimension());
        return levelType == null || descriptor.scope().accepts(levelType);
    }

    @Override
    public Optional<WorldshapeDescriptor> getDescriptorAt(ResourceKey<Level> dimension, int y) {
        List<LayeredDescriptor> layers = getActiveLayers(dimension);
        if (!layers.isEmpty()) {
            return LayerResolver.resolveByY(layers, y);
        }
        return getActiveDescriptor(dimension);
    }

    @Override
    public Optional<WorldshapeDescriptor> getDescriptorAt(ResourceKey<Level> dimension,
                                                           int x, int y, int z) {
        List<LayeredDescriptor> layers = getActiveLayers(dimension);
        if (!layers.isEmpty()) {
            // Half-open [minY, maxY) bands, with each layer's TransitionRule applied at the
            // seam it owns. A position in a gap — authored or TransitionRule.Gap-derived —
            // resolves to no descriptor.
            return LayerResolver.resolve(layers, x, y, z);
        }
        return getActiveDescriptor(dimension);
    }

    @Override
    public Set<ResourceKey<Level>> getDeclaredDimensions() {
        Set<ResourceKey<Level>> all = new HashSet<>(singleLayer.keySet());
        all.addAll(multiLayer.keySet());
        return Set.copyOf(all);
    }

    /**
     * Drop every declaration (single-layer and layered). Called at server stop via
     * {@link IsekaiInternal#clearDeclarations()} so Java-side declarations don't bleed into
     * the next world. JSON-sourced declarations are re-applied on the next reload, so this
     * is a full reset of the in-memory registry rather than a selective purge.
     */
    public void clearAll() {
        int cleared = singleLayer.values().stream().mapToInt(List::size).sum()
                + multiLayer.size();
        singleLayer.clear();
        multiLayer.clear();
        if (cleared > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] cleared {} worldshape declaration(s) on shutdown", cleared);
        }
    }
}
