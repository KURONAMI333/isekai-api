package com.kuronami.isekaiapi.api.registry;

import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.TransitionRule;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry keys for Isekai's five open extension points. Each key names an Isekai-managed
 * custom {@link Registry} whose entries are {@code MapCodec}s — exactly the shape vanilla uses
 * for {@code minecraft:worldgen/density_function_type}. Third-party mods extend any of the five
 * vocabularies by registering their own {@code MapCodec} under one of these keys via
 * {@link net.neoforged.neoforge.registries.DeferredRegister#create(ResourceKey, String)}; the
 * new variant is then usable from datapack JSON with no change to Isekai.
 *
 * <p>Example — a third party adds a spatial predicate:
 * <pre>{@code
 * DeferredRegister<MapCodec<? extends SpatialPredicate>> TYPES =
 *     DeferredRegister.create(IsekaiRegistries.SPATIAL_PREDICATE_TYPE, "yourmod");
 * TYPES.register("checkerboard", () -> Checkerboard.MAP_CODEC);
 * // -> {"type": "yourmod:checkerboard"} now decodes in any Isekai predicate slot.
 * }</pre>
 *
 * @since 2.0.0
 */
public final class IsekaiRegistries {

    private IsekaiRegistries() {}

    private static <T> ResourceKey<Registry<MapCodec<? extends T>>> key(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("isekai_api", path));
    }

    /** Registry of {@link SpatialPredicate} variant codecs. @since 2.0.0 */
    public static final ResourceKey<Registry<MapCodec<? extends SpatialPredicate>>> SPATIAL_PREDICATE_TYPE =
            key("spatial_predicate_type");

    /** Registry of {@link RemapStrategy} variant codecs. @since 2.0.0 */
    public static final ResourceKey<Registry<MapCodec<? extends RemapStrategy>>> REMAP_STRATEGY_TYPE =
            key("remap_strategy_type");

    /** Registry of {@link BiomeZone} variant codecs. @since 2.0.0 */
    public static final ResourceKey<Registry<MapCodec<? extends BiomeZone>>> BIOME_ZONE_TYPE =
            key("biome_zone_type");

    /** Registry of {@link SurfaceAnchor} variant codecs. @since 2.0.0 */
    public static final ResourceKey<Registry<MapCodec<? extends SurfaceAnchor>>> SURFACE_ANCHOR_TYPE =
            key("surface_anchor_type");

    /** Registry of {@link TransitionRule} variant codecs. @since 2.0.0 */
    public static final ResourceKey<Registry<MapCodec<? extends TransitionRule>>> TRANSITION_RULE_TYPE =
            key("transition_rule_type");
}
