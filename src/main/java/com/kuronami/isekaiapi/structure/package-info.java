/**
 * Neutral {@link net.minecraft.world.level.levelgen.structure.Structure} primitives — give
 * datapack consumers a route to locatable composite landmarks without writing Java. The
 * single registered type {@code isekai_api:assembled}
 * ({@link com.kuronami.isekaiapi.structure.AssembledStructure}) delegates block placement
 * to a consumer-supplied list of {@code ConfiguredFeature}s at the structure origin, while
 * the Structure layer handles {@code /locate} support, biome filtering, generation-step
 * ordering, and {@code structure_set} spacing.
 *
 * <p>Pure plumbing — no theme content (oasis / well / shrine / ruin / etc.) is baked here;
 * those are consumer datapack arrangements of the underlying {@code ConfiguredFeature}s.
 */
package com.kuronami.isekaiapi.structure;
