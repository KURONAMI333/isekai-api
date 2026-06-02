package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registry of Isekai-provided placement modifier types. Datapack consumers invoke these
 * via {@code "type": "isekai_api:<name>"} in their PlacedFeature JSON (the namespace is
 * the mod id, not the bare {@code "isekai"} prefix used by the in-house dispatch codecs
 * such as {@code SpatialPredicate}).
 *
 * <p>Registered types:
 * <ul>
 *   <li>{@code isekai_api:surface_relative} — anchor to WORLD_SURFACE_WG + offset</li>
 *   <li>{@code isekai_api:fluid_relative} — anchor to water top/bottom + offset</li>
 *   <li>{@code isekai_api:in_block_context} — filter by surrounding block context</li>
 *   <li>{@code isekai_api:spatial_predicate} — filter by any {@link
 *       com.kuronami.isekaiapi.api.predicate.SpatialPredicate}</li>
 *   <li>{@code isekai_api:scatter} — jitter into N samples within a radius with optional
 *       minimum spacing (vanilla {@code count + in_square} plus non-overlap)</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiPlacementModifiers {

    public static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, IsekaiApi.MODID);

    public static final Supplier<PlacementModifierType<SurfaceRelativeModifier>> SURFACE_RELATIVE =
            TYPES.register("surface_relative", () -> () -> SurfaceRelativeModifier.CODEC);

    public static final Supplier<PlacementModifierType<FluidRelativeModifier>> FLUID_RELATIVE =
            TYPES.register("fluid_relative", () -> () -> FluidRelativeModifier.CODEC);

    public static final Supplier<PlacementModifierType<InBlockContextModifier>> IN_BLOCK_CONTEXT =
            TYPES.register("in_block_context", () -> () -> InBlockContextModifier.CODEC);

    public static final Supplier<PlacementModifierType<SpatialPredicatePlacementModifier>> SPATIAL_PREDICATE =
            TYPES.register("spatial_predicate", () -> () -> SpatialPredicatePlacementModifier.CODEC);

    public static final Supplier<PlacementModifierType<ScatterPlacementModifier>> SCATTER =
            TYPES.register("scatter", () -> () -> ScatterPlacementModifier.CODEC);

    public static final Supplier<PlacementModifierType<FluidEdgeModifier>> FLUID_EDGE =
            TYPES.register("fluid_edge", () -> () -> FluidEdgeModifier.CODEC);

    public static final Supplier<PlacementModifierType<SlopeFilterModifier>> SLOPE_FILTER =
            TYPES.register("slope_filter", () -> () -> SlopeFilterModifier.CODEC);

    private IsekaiPlacementModifiers() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] placement modifiers registered: surface_relative, fluid_relative, in_block_context, spatial_predicate, scatter, fluid_edge, slope_filter");
    }
}
