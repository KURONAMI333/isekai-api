package com.kuronami.isekaiapi.densityfunction;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Package-internal base interface for Isekai composite density functions — every primitive
 * that holds one or more inner {@link DensityFunction} children. Provides the default
 * {@code fillArray} delegate that {@code DensityFunction.SimpleFunction} would otherwise
 * supply (we can't use {@code SimpleFunction} because its {@code mapAll} short-circuits
 * without recursing into children, which breaks vanilla's cache-wrapper / optimizer
 * passes — each composite implements its own {@code mapAll} to recurse properly).
 *
 * <p>Leaf primitives ({@code ConstantDF}, {@code CoordinateDF}, {@code DistanceDF}) hold
 * no child DF and continue to implement {@link DensityFunction.SimpleFunction} directly.
 */
interface CompositeDensityFunction extends DensityFunction {
    @Override
    default void fillArray(double[] doubles, ContextProvider provider) {
        provider.fillAllDirectly(doubles, this);
    }
}
