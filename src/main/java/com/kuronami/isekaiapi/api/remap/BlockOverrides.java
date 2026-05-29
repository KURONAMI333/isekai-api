package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

/**
 * Per-biome block overrides applied during chunk generation. The two maps target the two
 * vanilla chunk-gen passes a worldshape might want to customise:
 *
 * <ul>
 *   <li>{@link #surfaceTop} — replaces the top BlockState in matched biomes. "In the
 *       desert, top with glass instead of sand" is one entry here. Applied via the
 *       {@code isekai_api:worldshape_surface_top} {@link net.minecraft.world.level.levelgen.SurfaceRules.RuleSource}
 *       (see {@link com.kuronami.isekaiapi.surfacerule.WorldshapeSurfaceTopRule}), which the
 *       consumer prepends to their dimension's {@code surface_rule} sequence.</li>
 *   <li>{@link #defaultBlock} — replaces the default (stone-equivalent) column fill in
 *       matched biomes, re-skinning the bulk of the chunk volume. Applied via the
 *       {@code isekai_api:worldshape_default_block} rule source (see
 *       {@link com.kuronami.isekaiapi.surfacerule.WorldshapeDefaultBlockRule}), which the
 *       consumer appends after the vanilla rules in their {@code surface_rule} sequence.</li>
 * </ul>
 *
 * <p>Both maps default to empty. The biome key is exact match — to apply to many biomes,
 * list each. Both rules read these maps from the active worldshape at apply-time, so the
 * overrides are hot-reloadable along with the rest of the worldshape JSON.
 */
public record BlockOverrides(
        Map<ResourceKey<Biome>, BlockState> surfaceTop,
        Map<ResourceKey<Biome>, BlockState> defaultBlock
) {
    public BlockOverrides {
        surfaceTop = Map.copyOf(surfaceTop);
        defaultBlock = Map.copyOf(defaultBlock);
    }

    public static final BlockOverrides EMPTY = new BlockOverrides(Map.of(), Map.of());

    public boolean isEmpty() {
        return surfaceTop.isEmpty() && defaultBlock.isEmpty();
    }

    public static final Codec<BlockOverrides> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceKey.codec(Registries.BIOME), BlockState.CODEC)
                    .optionalFieldOf("surface_top", Map.of())
                    .forGetter(BlockOverrides::surfaceTop),
            Codec.unboundedMap(ResourceKey.codec(Registries.BIOME), BlockState.CODEC)
                    .optionalFieldOf("default_block", Map.of())
                    .forGetter(BlockOverrides::defaultBlock)
    ).apply(i, BlockOverrides::new));
}
