package com.kuronami.isekaiapi.api.predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The world-access seam a {@link SpatialPredicate} tests against. Isekai evaluates predicates at
 * two very different stages, each with its own view of the world:
 * <ul>
 *   <li><b>structure placement</b> — during {@code Structure.findValidGenerationPoint}, before
 *       real blocks exist. Terrain queries are answered from the {@code ChunkGenerator}'s noise
 *       columns and biome source.</li>
 *   <li><b>feature decoration</b> — during placement, when base terrain blocks are already
 *       present, so queries read the {@code WorldGenLevel} directly.</li>
 * </ul>
 * A predicate is written once against this interface; Isekai supplies the stage-appropriate
 * implementation. Queries that a given stage cannot answer (for example the multi-column scans
 * at feature-decoration time) return {@code true} (permissive) so a predicate never blocks
 * placement on a query the stage can't resolve — matching the historical per-stage behavior.
 *
 * <p>Third-party predicates should build their decision from {@link #pos()} plus the terrain
 * queries below; do not assume both stages answer every query the same way.
 *
 * @since 2.0.0
 */
public interface EvaluationContext {

    /** The position being tested. */
    BlockPos pos();

    /** True when there is solid ground directly below {@link #pos()} and at least {@code minClearance} air above it. */
    boolean solidFloor(int minClearance);

    /** True when there is a solid ceiling directly above {@link #pos()} and at least {@code minClearance} air below it. */
    boolean solidCeiling(int minClearance);

    /** True when {@link #pos()} is inside the given fluid. */
    boolean inFluid(Fluid fluid);

    /** True when any block matching {@code targets} occurs within {@code maxDistance} of {@link #pos()}. */
    boolean nearBlock(HolderSet<Block> targets, int maxDistance);

    /** True when a chunk whose biome is {@code biome} occurs within {@code maxDistance} of {@link #pos()}. */
    boolean nearBiome(ResourceKey<Biome> biome, int maxDistance);

    /** True when the local terrain slope at {@link #pos()} falls within {@code [minSlope, maxSlope]}. */
    boolean terrainSlope(double minSlope, double maxSlope);
}
