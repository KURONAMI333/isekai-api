package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * {@code exclusions.structures} resolution. Structure exclusions are enforced while the
 * structure searches for a generation point, not by emptying its biome filter, so the
 * exclusion stays inside the dimension that declared it even when other dimensions reuse
 * the same biomes.
 *
 * <p>Layered dimensions use union semantics: a structure excluded by any layer is excluded
 * from that dimension. The exclusion cannot be evaluated per Y because it must answer before
 * a position exists.
 */
@ApiStatus.Internal
public final class StructureExclusion {

    private StructureExclusion() {}

    public static boolean excludes(WorldshapeDescriptor descriptor, ResourceKey<Structure> structure) {
        return structure != null && descriptor.exclusions().structures().contains(structure);
    }

    public static boolean excludesAny(List<LayeredDescriptor> layers, ResourceKey<Structure> structure) {
        if (structure == null) return false;
        for (LayeredDescriptor layer : layers) {
            if (excludes(layer.descriptor(), structure)) return true;
        }
        return false;
    }

    /** Resolves the active declaration for {@code dimension} and tests the structure against it. */
    public static boolean isExcluded(ResourceKey<Level> dimension, ResourceKey<Structure> structure) {
        if (structure == null) return false;
        var remap = Isekai.remap();
        List<LayeredDescriptor> layers = remap.getActiveLayers(dimension);
        if (!layers.isEmpty()) return excludesAny(layers, structure);
        return remap.getActiveDescriptor(dimension)
                .map(descriptor -> excludes(descriptor, structure))
                .orElse(false);
    }

    /** Convenience for callers holding a structure holder but not its key. */
    public static ResourceKey<Structure> keyOf(net.minecraft.core.Holder<Structure> structure) {
        return structure.unwrapKey().orElse(null);
    }
}
