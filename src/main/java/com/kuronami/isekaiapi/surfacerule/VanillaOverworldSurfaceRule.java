package com.kuronami.isekaiapi.surfacerule;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;

import org.jetbrains.annotations.ApiStatus;

/**
 * A zero-field {@link SurfaceRules.RuleSource} — {@code isekai_api:vanilla_overworld_surface} —
 * that emits the full vanilla overworld surface (grass/dirt/sand/badlands-bands/snow-caps/…)
 * reconstructed by {@link VanillaOverworldSurface}.
 *
 * <p>Purpose: a consumer replacing or defining a noise_settings no longer copies the ~30&nbsp;KB
 * expanded overworld {@code surface_rule} JSON. They write one line:
 *
 * <pre>{@code "surface_rule": { "type": "isekai_api:vanilla_overworld_surface" } }</pre>
 *
 * <p>Or wrap it in a {@code minecraft:sequence} with {@code isekai_api:worldshape_surface_top}
 * (first) and {@code isekai_api:worldshape_default_block} (last) to layer per-biome overrides
 * from an active worldshape on top of the vanilla base:
 *
 * <pre>{@code
 * "surface_rule": { "type": "minecraft:sequence", "sequence": [
 *   { "type": "isekai_api:worldshape_surface_top",  "dimension": "<dim>" },
 *   { "type": "isekai_api:vanilla_overworld_surface" },
 *   { "type": "isekai_api:worldshape_default_block", "dimension": "<dim>" }
 * ]}
 * }</pre>
 *
 * <p>The rule is a singleton (the tree is immutable and shared) and reads nothing from the
 * registry, so it is safe even inside a replaced {@code minecraft:overworld} — it can never
 * recurse into the very rule it is standing in for.
 */
@ApiStatus.Internal
public enum VanillaOverworldSurfaceRule implements SurfaceRules.RuleSource {
    INSTANCE;

    public static final MapCodec<VanillaOverworldSurfaceRule> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return new KeyDispatchDataCodec<>(CODEC);
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        return VanillaOverworldSurface.tree().apply(context);
    }
}
