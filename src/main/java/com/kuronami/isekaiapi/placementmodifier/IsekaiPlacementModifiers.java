package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.IsekaiApi;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registry of Isekai-provided placement modifier types. Datapack consumers invoke these
 * via {@code "type": "isekai:<name>"} in their PlacedFeature JSON.
 *
 * <p>Three modifiers in v0.1:
 * <ul>
 *   <li>{@code isekai:surface_relative} — anchor to WORLD_SURFACE_WG + offset</li>
 *   <li>{@code isekai:fluid_relative} — anchor to water top/bottom + offset</li>
 *   <li>{@code isekai:in_block_context} — filter by surrounding block context</li>
 * </ul>
 *
 * <p>v0.2 will broaden: arbitrary SurfaceAnchor variants (now hardcoded), arbitrary
 * fluid (now water-only), additional context predicates.
 */
public final class IsekaiPlacementModifiers {

    public static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, IsekaiApi.MODID);

    public static final Supplier<PlacementModifierType<SurfaceRelativeModifier>> SURFACE_RELATIVE =
            TYPES.register("surface_relative", () -> () -> SurfaceRelativeModifier.CODEC);

    public static final Supplier<PlacementModifierType<FluidRelativeModifier>> FLUID_RELATIVE =
            TYPES.register("fluid_relative", () -> () -> FluidRelativeModifier.CODEC);

    public static final Supplier<PlacementModifierType<InBlockContextModifier>> IN_BLOCK_CONTEXT =
            TYPES.register("in_block_context", () -> () -> InBlockContextModifier.CODEC);

    private IsekaiPlacementModifiers() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        IsekaiApi.LOGGER.info("Isekai placement modifiers registered: surface_relative, fluid_relative, in_block_context");
    }
}
