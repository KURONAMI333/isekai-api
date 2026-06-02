package com.kuronami.isekaiapi.surfacerule;

import com.kuronami.isekaiapi.IsekaiApi;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers Isekai's {@link SurfaceRules.RuleSource} types into the vanilla
 * {@code BuiltInRegistries.MATERIAL_RULE} registry. Datapack consumers reference them via
 * {@code "type": "isekai_api:<name>"} inside their dimension's noise_settings
 * {@code surface_rule}.
 *
 * <p>Currently registered:
 * <ul>
 *   <li>{@code isekai_api:worldshape_surface_top} — biome-keyed top-block override read
 *       from the active worldshape's {@code block_overrides.surface_top}.</li>
 *   <li>{@code isekai_api:worldshape_default_block} — biome-keyed default-fill override.</li>
 *   <li>{@code isekai_api:strata} — ordered (block, thickness) bands measured downward from
 *       the floor surface. Collapses N-layer nested {@code stone_depth} sequences into one
 *       flat list.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiSurfaceRules {

    public static final DeferredRegister<MapCodec<? extends SurfaceRules.RuleSource>> CODECS =
            DeferredRegister.create(BuiltInRegistries.MATERIAL_RULE, IsekaiApi.MODID);

    public static final Supplier<MapCodec<? extends SurfaceRules.RuleSource>> WORLDSHAPE_SURFACE_TOP =
            CODECS.register("worldshape_surface_top", () -> WorldshapeSurfaceTopRule.CODEC);

    public static final Supplier<MapCodec<? extends SurfaceRules.RuleSource>> WORLDSHAPE_DEFAULT_BLOCK =
            CODECS.register("worldshape_default_block", () -> WorldshapeDefaultBlockRule.CODEC);

    public static final Supplier<MapCodec<? extends SurfaceRules.RuleSource>> STRATA =
            CODECS.register("strata", () -> StrataRule.CODEC);

    private IsekaiSurfaceRules() {}

    public static void register(IEventBus modBus) {
        CODECS.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] surface rule sources registered: worldshape_surface_top, worldshape_default_block, strata");
    }
}
