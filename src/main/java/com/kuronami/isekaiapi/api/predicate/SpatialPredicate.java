package com.kuronami.isekaiapi.api.predicate;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.List;

/**
 * Neutral spatial conditions for placement filtering. Combine via {@link And} / {@link Or} /
 * {@link Not} to express arbitrary placement constraints without committing to any specific
 * worldshape's vocabulary.
 *
 * <p>This replaces the older {@code StructureContext} enum, which encoded named contexts
 * (PLATEAU_TOP / CANYON_FLOOR / FLOATING / UNDERWATER) that leaked specific worldshape
 * assumptions into the API surface.
 */
public sealed interface SpatialPredicate {

    /** Y coordinate must fall within [min, max] (inclusive). */
    record YInRange(int min, int max) implements SpatialPredicate {}

    /** Block has solid ground beneath and at least {@code minClearance} blocks of empty space above. */
    record SolidFloor(int minClearance) implements SpatialPredicate {}

    /** Block has solid ceiling above and at least {@code minClearance} blocks of empty space below. */
    record SolidCeiling(int minClearance) implements SpatialPredicate {}

    /** Local terrain slope falls within [min, max]. 0 = flat, 1 = 45deg. */
    record TerrainSlope(double minSlope, double maxSlope) implements SpatialPredicate {}

    /** Block matches {@code target} (or any in {@code target}'s tag) within {@code maxDistance}. */
    record NearBlock(Block target, int maxDistance) implements SpatialPredicate {}

    /** Position is within {@code maxDistance} of a chunk whose biome key matches. */
    record NearBiome(ResourceKey<Biome> biome, int maxDistance) implements SpatialPredicate {}

    /** Position is inside the specified fluid. */
    record InFluid(Fluid fluid) implements SpatialPredicate {}

    /** Always true. */
    record Always() implements SpatialPredicate {}

    /** Always false. */
    record Never() implements SpatialPredicate {}

    /** All sub-predicates must hold. */
    record And(List<SpatialPredicate> all) implements SpatialPredicate {
        public And { all = List.copyOf(all); }
    }

    /** Any sub-predicate holds. */
    record Or(List<SpatialPredicate> any) implements SpatialPredicate {
        public Or { any = List.copyOf(any); }
    }

    /** Negation of inner predicate. */
    record Not(SpatialPredicate inner) implements SpatialPredicate {}
}
