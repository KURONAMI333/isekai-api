/**
 * Sixteen neutral density-function primitives that consumers can compose in
 * {@code data/<ns>/worldgen/density_function/} JSON. None of the primitives know
 * anything Isekai-specific; they are general-purpose worldgen building blocks.
 *
 * <p>Composite primitives (those holding inner {@link net.minecraft.world.level.levelgen.DensityFunction}
 * children) implement the package-internal
 * {@link com.kuronami.isekaiapi.densityfunction.CompositeDensityFunction} interface
 * to share the {@code fillArray} boilerplate; leaf primitives ({@code ConstantDF},
 * {@code CoordinateDF}, {@code DistanceDF}) implement
 * {@link net.minecraft.world.level.levelgen.DensityFunction.SimpleFunction} directly.
 */
package com.kuronami.isekaiapi.densityfunction;
