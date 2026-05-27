package com.kuronami.isekaiapi.biomemodifier.phase;

import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Static helper for testing whether a biome falls inside a {@link WorldshapeDescriptor}'s
 * {@code appliesTo} filter. Empty filter = match every biome (the descriptor applies
 * dimension-wide; consumers scope via biome tags or explicit lists).
 */
public final class BiomeMatcher {

    private BiomeMatcher() {}

    public static boolean matches(WorldshapeDescriptor descriptor, Holder<Biome> biome) {
        var filter = descriptor.appliesTo();
        if (filter.isEmpty()) {
            return true;
        }
        for (var key : filter) {
            if (biome.is(key)) {
                return true;
            }
        }
        return false;
    }
}
