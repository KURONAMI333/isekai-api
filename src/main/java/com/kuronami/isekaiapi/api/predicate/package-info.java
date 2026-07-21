/**
 * Spatial predicate language for filtering structure placement.
 *
 * <p>{@link com.kuronami.isekaiapi.api.predicate.SpatialPredicate} is a
 * registry-backed extensible interface; its twelve built-in variants are trivial
 * (Always, Never, YInRange), boolean combinators (And, Or, Not), terrain probes
 * (SolidFloor, SolidCeiling, InFluid), and proximity probes (NearBlock, NearBiome,
 * TerrainSlope). Each variant evaluates itself via
 * {@link com.kuronami.isekaiapi.api.predicate.SpatialPredicate#test} against an
 * {@link com.kuronami.isekaiapi.api.predicate.EvaluationContext} — supplied by the
 * structure-placement Mixin at chunk-gen time and by the feature-placement modifier
 * at decoration time. Third parties register their own variants in the
 * {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries#SPATIAL_PREDICATE_TYPE}
 * registry.
 */
package com.kuronami.isekaiapi.api.predicate;
