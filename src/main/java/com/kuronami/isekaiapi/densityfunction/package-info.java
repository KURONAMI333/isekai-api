/**
 * Seventeen neutral density-function primitives plus five worldshape composers
 * ({@code squeeze}, {@code y_envelope}, {@code blended_noise}, {@code band_density},
 * {@code sloped_density}) that consumers compose in
 * {@code data/<ns>/worldgen/density_function/} or {@code noise_settings} JSON. None of the
 * primitives know anything Isekai-specific; they are general-purpose worldgen building
 * blocks (math, geometry, noise, neutral terrain structure — no themes).
 *
 * <p>Composite primitives (those holding inner {@link net.minecraft.world.level.levelgen.DensityFunction}
 * children) implement the package-internal
 * {@link com.kuronami.isekaiapi.densityfunction.CompositeDensityFunction} interface
 * to share the {@code fillArray} boilerplate; leaf primitives ({@code ConstantDF},
 * {@code CoordinateDF}, {@code DistanceDF}) implement
 * {@link net.minecraft.world.level.levelgen.DensityFunction.SimpleFunction} directly.
 */
package com.kuronami.isekaiapi.densityfunction;
