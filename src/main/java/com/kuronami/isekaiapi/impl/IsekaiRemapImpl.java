package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;

/**
 * Concurrent in-memory registry of consumer-declared worldshape descriptors. Single-layer
 * declarations enforce {@link WorldshapeDescriptor#priority()} ties via
 * {@link Map#compute}; layered declarations replace on conflict (per spec — multi-layer
 * stacks are typically one-per-dimension by design).
 *
 * <p>Reads are O(1) via the two ConcurrentHashMaps; writes are O(1) amortised. The maps
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

    private final Map<ResourceKey<Level>, WorldshapeDescriptor> singleLayer = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, List<LayeredDescriptor>> multiLayer = new ConcurrentHashMap<>();

    @Override
    public void declareWorldshape(WorldshapeDescriptor descriptor) {
        singleLayer.compute(descriptor.dimension(), (k, existing) -> {
            if (existing != null && existing.priority() > descriptor.priority()) {
                IsekaiApi.LOGGER.warn(
                        "[Isekai] Skipping declareWorldshape for {}: existing priority {} > new priority {}",
                        descriptor.dimension(), existing.priority(), descriptor.priority());
                return existing;
            }
            if (existing != null) {
                IsekaiApi.LOGGER.warn(
                        "[Isekai] Replacing single-layer descriptor for {} (priority {} -> {})",
                        descriptor.dimension(), existing.priority(), descriptor.priority());
            }
            IsekaiApi.LOGGER.info("[Isekai] declareWorldshape: dim={}, range={}, priority={}",
                    descriptor.dimension(), descriptor.playableRange(), descriptor.priority());
            return descriptor;
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
        singleLayer.put(dimension, newDescriptor);
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
        return Optional.ofNullable(singleLayer.get(dimension));
    }

    @Override
    public List<LayeredDescriptor> getActiveLayers(ResourceKey<Level> dimension) {
        return multiLayer.getOrDefault(dimension, List.of());
    }

    @Override
    public Optional<WorldshapeDescriptor> getDescriptorAt(ResourceKey<Level> dimension, int y) {
        List<LayeredDescriptor> layers = multiLayer.get(dimension);
        if (layers != null && !layers.isEmpty()) {
            return LayerResolver.resolveByY(layers, y);
        }
        return getActiveDescriptor(dimension);
    }

    @Override
    public Optional<WorldshapeDescriptor> getDescriptorAt(ResourceKey<Level> dimension,
                                                           int x, int y, int z) {
        List<LayeredDescriptor> layers = multiLayer.get(dimension);
        if (layers != null && !layers.isEmpty()) {
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
        int cleared = singleLayer.size() + multiLayer.size();
        singleLayer.clear();
        multiLayer.clear();
        if (cleared > 0) {
            IsekaiApi.LOGGER.debug("[Isekai] cleared {} worldshape declaration(s) on shutdown", cleared);
        }
    }
}
