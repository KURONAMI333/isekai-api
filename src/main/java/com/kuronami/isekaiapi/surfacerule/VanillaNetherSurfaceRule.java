package com.kuronami.isekaiapi.surfacerule;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;

import org.jetbrains.annotations.ApiStatus;

/**
 * A zero-field {@link SurfaceRules.RuleSource} — {@code isekai_api:vanilla_nether_surface} —
 * that emits the full vanilla nether surface reconstructed by {@link VanillaNetherSurface}.
 *
 * <p>Use it from a nether {@code noise_settings}:
 *
 * <pre>{@code "surface_rule": { "type": "isekai_api:vanilla_nether_surface" } }</pre>
 *
 * <p>Or layer per-biome overrides from an active worldshape:
 *
 * <pre>{@code
 * "surface_rule": { "type": "minecraft:sequence", "sequence": [
 *   { "type": "isekai_api:worldshape_surface_top",  "dimension": "minecraft:the_nether" },
 *   { "type": "isekai_api:vanilla_nether_surface" },
 *   { "type": "isekai_api:worldshape_default_block", "dimension": "minecraft:the_nether" }
 * ]}
 * }</pre>
 *
 * <p>The rule is a singleton (the tree is immutable and shared) and reads nothing from the
 * registry, so it is safe even inside a replaced {@code minecraft:nether}.
 * @since 3.0.0
 */
@ApiStatus.Internal
public enum VanillaNetherSurfaceRule implements SurfaceRules.RuleSource {
    INSTANCE;

    public static final MapCodec<VanillaNetherSurfaceRule> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return new KeyDispatchDataCodec<>(CODEC);
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        return VanillaNetherSurface.tree().apply(context);
    }

    @Override
    public String toString() {
        return "isekai_api:vanilla_nether_surface";
    }
}
