package com.kuronami.isekaiapi.api.remap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.Fluid;

import java.util.function.ToIntBiFunction;

/**
 * Defines "what Y level counts as the surface" for surface-relative placement modifiers
 * and predicate evaluation. Neutral primitives only.
 */
public sealed interface SurfaceAnchor {
    /** Topmost solid block per column (vanilla heightmap WORLD_SURFACE). */
    record WorldSurface() implements SurfaceAnchor {}

    /** Top of the highest contiguous body of the given fluid in each column. */
    record BelowFluid(Fluid fluid) implements SurfaceAnchor {}

    /** Fixed Y level regardless of terrain. */
    record FixedY(int y) implements SurfaceAnchor {}

    /** Caller-supplied function (level, pos) -> surface Y. Datapack consumers cannot use this. */
    record Custom(SurfaceLocator fn) implements SurfaceAnchor {}

    @FunctionalInterface
    interface SurfaceLocator extends ToIntBiFunction<LevelReader, BlockPos> {}
}
