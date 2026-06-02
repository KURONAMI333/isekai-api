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
            // half-open interval [minY, maxY): standard for consecutive non-overlapping bands
            for (LayeredDescriptor l : layers) {
                if (y >= l.yRange().minY() && y < l.yRange().maxY()) {
                    return Optional.of(l.descriptor());
                }
            }
            // Y falls in a gap between layers — no descriptor applies. (TransitionRule.Gap
            // explicitly models this; for Hard / Blend the validator enforces no gaps.)
            return Optional.empty();
        }
        return getActiveDescriptor(dimension);
    }

    @Override
    public Set<ResourceKey<Level>> getDeclaredDimensions() {
        Set<ResourceKey<Level>> all = new HashSet<>(singleLayer.keySet());
        all.addAll(multiLayer.keySet());
        return Set.copyOf(all);
    }
}
