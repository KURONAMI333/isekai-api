package com.kuronami.isekaiapi.registry;

import com.kuronami.isekaiapi.IsekaiApi;
import com.kuronami.isekaiapi.api.biomesource.BiomeZone;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.registry.IsekaiRegistries;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.TransitionRule;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

/**
 * Creates and populates Isekai's five custom {@code MapCodec} registries — the open extension
 * points behind {@link IsekaiRegistries}. Each registry is created via
 * {@link DeferredRegister#makeRegistry} and its built-in variants are registered here; third
 * parties add their own variants against the same {@link IsekaiRegistries} keys.
 *
 * <p>Built-in variants keep their historical ids ({@code y_in_range}, {@code linear}, …) under
 * the {@code isekai_api} namespace. The legacy {@code isekai:} prefix used in older datapacks is
 * accepted as a deprecated alias by {@link IsekaiDispatch}.
 */
@ApiStatus.Internal
public final class IsekaiSpiTypes {

    private IsekaiSpiTypes() {}

    // ---- SpatialPredicate ------------------------------------------------

    public static final DeferredRegister<MapCodec<? extends SpatialPredicate>> SPATIAL_PREDICATE_TYPES =
            DeferredRegister.create(IsekaiRegistries.SPATIAL_PREDICATE_TYPE, IsekaiApi.MODID);

    public static final Supplier<Registry<MapCodec<? extends SpatialPredicate>>> SPATIAL_PREDICATE_REGISTRY =
            SPATIAL_PREDICATE_TYPES.getRegistry();

    static {
        SPATIAL_PREDICATE_TYPES.register("y_in_range",    () -> SpatialPredicate.YInRange.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("solid_floor",   () -> SpatialPredicate.SolidFloor.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("solid_ceiling", () -> SpatialPredicate.SolidCeiling.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("terrain_slope", () -> SpatialPredicate.TerrainSlope.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("near_block",    () -> SpatialPredicate.NearBlock.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("near_biome",    () -> SpatialPredicate.NearBiome.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("in_fluid",      () -> SpatialPredicate.InFluid.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("always",        () -> SpatialPredicate.Always.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("never",         () -> SpatialPredicate.Never.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("and",           () -> SpatialPredicate.And.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("or",            () -> SpatialPredicate.Or.MAP_CODEC);
        SPATIAL_PREDICATE_TYPES.register("not",           () -> SpatialPredicate.Not.MAP_CODEC);
    }

    // ---- RemapStrategy ---------------------------------------------------

    public static final DeferredRegister<MapCodec<? extends RemapStrategy>> REMAP_STRATEGY_TYPES =
            DeferredRegister.create(IsekaiRegistries.REMAP_STRATEGY_TYPE, IsekaiApi.MODID);

    public static final Supplier<Registry<MapCodec<? extends RemapStrategy>>> REMAP_STRATEGY_REGISTRY =
            REMAP_STRATEGY_TYPES.getRegistry();

    static {
        REMAP_STRATEGY_TYPES.register("linear",      () -> RemapStrategy.Linear.MAP_CODEC);
        REMAP_STRATEGY_TYPES.register("band_split",  () -> RemapStrategy.BandSplit.MAP_CODEC);
        REMAP_STRATEGY_TYPES.register("fixed_range", () -> RemapStrategy.FixedRange.MAP_CODEC);
        REMAP_STRATEGY_TYPES.register("inverted",    () -> RemapStrategy.Inverted.MAP_CODEC);
        REMAP_STRATEGY_TYPES.register("count_scale", () -> RemapStrategy.CountScale.MAP_CODEC);
        REMAP_STRATEGY_TYPES.register("column_local",() -> RemapStrategy.ColumnLocal.MAP_CODEC);
        REMAP_STRATEGY_TYPES.register("identity",    () -> RemapStrategy.Identity.MAP_CODEC);
        REMAP_STRATEGY_TYPES.register("pipe",        () -> RemapStrategy.Pipe.MAP_CODEC);
    }

    // ---- BiomeZone -------------------------------------------------------

    public static final DeferredRegister<MapCodec<? extends BiomeZone>> BIOME_ZONE_TYPES =
            DeferredRegister.create(IsekaiRegistries.BIOME_ZONE_TYPE, IsekaiApi.MODID);

    public static final Supplier<Registry<MapCodec<? extends BiomeZone>>> BIOME_ZONE_REGISTRY =
            BIOME_ZONE_TYPES.getRegistry();

    static {
        BIOME_ZONE_TYPES.register("always",          () -> BiomeZone.Always.MAP_CODEC);
        BIOME_ZONE_TYPES.register("y_above",         () -> BiomeZone.YAbove.MAP_CODEC);
        BIOME_ZONE_TYPES.register("y_below",         () -> BiomeZone.YBelow.MAP_CODEC);
        BIOME_ZONE_TYPES.register("y_between",       () -> BiomeZone.YBetween.MAP_CODEC);
        BIOME_ZONE_TYPES.register("within_distance", () -> BiomeZone.WithinDistance.MAP_CODEC);
        BIOME_ZONE_TYPES.register("beyond_distance", () -> BiomeZone.BeyondDistance.MAP_CODEC);
        BIOME_ZONE_TYPES.register("and",             () -> BiomeZone.And.MAP_CODEC);
        BIOME_ZONE_TYPES.register("or",              () -> BiomeZone.Or.MAP_CODEC);
        BIOME_ZONE_TYPES.register("not",             () -> BiomeZone.Not.MAP_CODEC);
        BIOME_ZONE_TYPES.register("noise_threshold", () -> BiomeZone.NoiseThreshold.MAP_CODEC);
        BIOME_ZONE_TYPES.register("edge_jitter",     () -> BiomeZone.EdgeJitter.MAP_CODEC);
    }

    // ---- SurfaceAnchor ---------------------------------------------------

    public static final DeferredRegister<MapCodec<? extends SurfaceAnchor>> SURFACE_ANCHOR_TYPES =
            DeferredRegister.create(IsekaiRegistries.SURFACE_ANCHOR_TYPE, IsekaiApi.MODID);

    public static final Supplier<Registry<MapCodec<? extends SurfaceAnchor>>> SURFACE_ANCHOR_REGISTRY =
            SURFACE_ANCHOR_TYPES.getRegistry();

    static {
        SURFACE_ANCHOR_TYPES.register("world_surface", () -> SurfaceAnchor.WorldSurface.MAP_CODEC);
        SURFACE_ANCHOR_TYPES.register("below_fluid",   () -> SurfaceAnchor.BelowFluid.MAP_CODEC);
        SURFACE_ANCHOR_TYPES.register("fixed_y",       () -> SurfaceAnchor.FixedY.MAP_CODEC);
        SURFACE_ANCHOR_TYPES.register("world_floor",   () -> SurfaceAnchor.WorldFloor.MAP_CODEC);
    }

    // ---- TransitionRule --------------------------------------------------

    public static final DeferredRegister<MapCodec<? extends TransitionRule>> TRANSITION_RULE_TYPES =
            DeferredRegister.create(IsekaiRegistries.TRANSITION_RULE_TYPE, IsekaiApi.MODID);

    public static final Supplier<Registry<MapCodec<? extends TransitionRule>>> TRANSITION_RULE_REGISTRY =
            TRANSITION_RULE_TYPES.getRegistry();

    static {
        TRANSITION_RULE_TYPES.register("hard",  () -> TransitionRule.Hard.MAP_CODEC);
        TRANSITION_RULE_TYPES.register("blend", () -> TransitionRule.Blend.MAP_CODEC);
        TRANSITION_RULE_TYPES.register("gap",   () -> TransitionRule.Gap.MAP_CODEC);
    }

    /**
     * Create all five custom registries and wire their population onto the mod event bus.
     * {@code makeRegistry} must run before {@code NewRegistryEvent}; calling it here (from the
     * mod constructor) satisfies that ordering.
     */
    public static void register(IEventBus modBus) {
        SPATIAL_PREDICATE_TYPES.makeRegistry(b -> b.sync(false));
        SPATIAL_PREDICATE_TYPES.register(modBus);

        REMAP_STRATEGY_TYPES.makeRegistry(b -> b.sync(false));
        REMAP_STRATEGY_TYPES.register(modBus);

        BIOME_ZONE_TYPES.makeRegistry(b -> b.sync(false));
        BIOME_ZONE_TYPES.register(modBus);

        SURFACE_ANCHOR_TYPES.makeRegistry(b -> b.sync(false));
        SURFACE_ANCHOR_TYPES.register(modBus);

        TRANSITION_RULE_TYPES.makeRegistry(b -> b.sync(false));
        TRANSITION_RULE_TYPES.register(modBus);

        IsekaiApi.LOGGER.info("[Isekai] SPI type registries created (spatial_predicate, remap_strategy, "
                + "biome_zone, surface_anchor, transition_rule)");
    }
}
