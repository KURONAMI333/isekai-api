/**
 * Sponge-Mixin classes targeting Mojang internals that have no NeoForge event:
 *
 * <ul>
 *   <li>{@link com.kuronami.isekaiapi.mixin.StructureFindValidGenerationPointMixin} —
 *       hooks {@code Structure.findValidGenerationPoint} to enforce the descriptor's
 *       per-structure {@code SpatialPredicate} and its {@code structureStrategy}
 *       {@code CountScale} thinning at placement time.</li>
 * </ul>
 *
 * <p>Structure <em>spacing</em> is deliberately not mixed into.
 * {@code RandomSpreadStructurePlacement} is shared by every dimension that references the
 * same {@code StructureSet} and its {@code spacing()} takes no dimension argument, so a
 * per-worldshape scale applied there would leak across dimensions; the candidate-chunk grid
 * derived from it is also cached per world load, so changing it afterwards would break seed
 * reproducibility. Frequency is instead reduced by vetoing candidates in the mixin above —
 * see {@link com.kuronami.isekaiapi.impl.StructureThinning}.
 *
 * <p>All classes are listed in {@code resources/isekai.mixins.json}. Field exposure
 * for the target class lives in {@code resources/META-INF/accesstransformer.cfg}.
 */
package com.kuronami.isekaiapi.mixin;
