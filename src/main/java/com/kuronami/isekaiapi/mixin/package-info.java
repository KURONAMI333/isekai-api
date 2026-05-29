/**
 * Sponge-Mixin classes targeting Mojang internals that have no NeoForge event:
 *
 * <ul>
 *   <li>{@link com.kuronami.isekaiapi.mixin.StructureFindValidGenerationPointMixin} —
 *       hooks {@code Structure.findValidGenerationPoint} to enforce the descriptor's
 *       per-structure {@code SpatialPredicate} at placement time.</li>
 *   <li>{@link com.kuronami.isekaiapi.mixin.RandomSpreadSpacingMixin} — hooks
 *       {@code RandomSpreadStructurePlacement.spacing} / {@code separation} to scale
 *       structure spacing per-dimension by the descriptor's {@code structureStrategy}
 *       {@code CountScale} factor.</li>
 * </ul>
 *
 * <p>All classes are listed in {@code resources/isekai.mixins.json}. Field exposure
 * for both target classes lives in {@code resources/META-INF/accesstransformer.cfg}.
 */
package com.kuronami.isekaiapi.mixin;
