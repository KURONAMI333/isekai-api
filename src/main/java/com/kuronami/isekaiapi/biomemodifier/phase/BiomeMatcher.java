package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.ApiStatus;

/**
 * Static helper for testing whether a biome falls inside a {@link WorldshapeDescriptor}'s
 * {@code appliesTo} filter.
 *
 * <p><b>Empty filter = match no biome.</b> NeoForge {@code BiomeModifier} has no dimension
 * scope, so an "empty means all" interpretation would silently apply a descriptor declared
 * for one dimension to biomes in every other dimension that happens to reuse those biomes.
 * Consumers must opt into specific biomes explicitly — list them, or use a biome tag.
 */
@ApiStatus.Internal
public final class BiomeMatcher {

    private BiomeMatcher() {}

    public static boolean matches(WorldshapeDescriptor descriptor, Holder<Biome> biome) {
        var filter = descriptor.appliesTo();
        if (filter.isEmpty()) {
            return false;
        }
        return filter.matches(biome);
    }
}
