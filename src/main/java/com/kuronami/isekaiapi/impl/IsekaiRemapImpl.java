package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.TransitionRule;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v0.1 stub. Records declarations in-memory; biome modifier generation lands in v0.2.
 */
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
            IsekaiApi.LOGGER.info("[Isekai v0.1 stub] declareWorldshape: dim={}, range={}, priority={}",
                    descriptor.dimension(), descriptor.playableRange(), descriptor.priority());
            return descriptor;
        });
    }

    @Override
    public void declareLayeredWorldshape(ResourceKey<Level> dimension,
                                          List<LayeredDescriptor> layers,
                                          TransitionRule transition) {
        multiLayer.put(dimension, List.copyOf(layers));
        IsekaiApi.LOGGER.info("[Isekai v0.1 stub] declareLayeredWorldshape: dim={}, layers={}, transition={}",
                dimension, layers.size(), transition.getClass().getSimpleName());
    }

    @Override
    public void updateWorldshape(ResourceKey<Level> dimension, WorldshapeDescriptor newDescriptor) {
        singleLayer.put(dimension, newDescriptor);
        IsekaiApi.LOGGER.info("[Isekai v0.1 stub] updateWorldshape: dim={}", dimension);
    }

    @Override
    public void removeWorldshape(ResourceKey<Level> dimension) {
        singleLayer.remove(dimension);
        multiLayer.remove(dimension);
        IsekaiApi.LOGGER.info("[Isekai v0.1 stub] removeWorldshape: dim={}", dimension);
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
    public Set<ResourceKey<Level>> getDeclaredDimensions() {
        Set<ResourceKey<Level>> all = new HashSet<>(singleLayer.keySet());
        all.addAll(multiLayer.keySet());
        return Set.copyOf(all);
    }
}
