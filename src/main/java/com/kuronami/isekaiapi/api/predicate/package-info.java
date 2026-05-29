/**
 * Spatial predicate language for filtering structure placement.
 *
 * <p>{@link com.kuronami.isekaiapi.api.predicate.SpatialPredicate} is a sealed
 * interface with twelve variants — trivial (Always, Never, YInRange), boolean
 * combinators (And, Or, Not), terrain probes (SolidFloor, SolidCeiling, InFluid),
 * and proximity probes (NearBlock, NearBiome, TerrainSlope). Variants are evaluated
 * by the structure-placement Mixin at chunk-gen time against the candidate
 * {@code GenerationStub} position.
 */
package com.kuronami.isekaiapi.api.predicate;
