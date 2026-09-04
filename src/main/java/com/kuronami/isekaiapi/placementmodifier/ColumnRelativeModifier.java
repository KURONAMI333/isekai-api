package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.api.remap.ColumnBand;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * Places features at a depth into the column's own terrain rather than at an absolute Y.
 * Resolves the {@link ColumnBand}'s two anchors for the column, draws a normalized depth from
 * the band, and converts it to a Y. Drop-in replacement for {@code minecraft:height_range} when
 * the terrain's altitude varies per column.
 *
 * <p>A column of floating terrain can hold several separate bodies stacked above one another,
 * and each of them is terrain the band describes. One position is emitted per body, at the same
 * normalized depth: {@code height_range} yields one Y per column because a vanilla column is one
 * body, and the invariant that carries over is one placement per <i>body</i>, not per column.
 * Splitting a single placement among the bodies instead would make an island's ore thin out as
 * soon as another island happened to float above it.
 *
 * <p>The depth is drawn exactly once per call, however many bodies the column turns out to hold,
 * so the random source this modifier consumes never depends on the shape of the column. A
 * chunk's later placements therefore land exactly where they did before this walked past the
 * topmost body.
 *
 * <p>Yields nothing when either anchor fails to resolve (no terrain in this column, or a body
 * that never ends) — so void columns cost one heightmap read and are skipped. A body with no
 * solid space between its anchors is skipped on its own, without ending the walk.
 *
 * <p>JSON form: the {@link ColumnBand} fields inline under
 * {@code "type": "isekai_api:column_relative"}.
 */
@ApiStatus.Internal
public class ColumnRelativeModifier extends PlacementModifier {

    /**
     * Hard stop on how many bodies one column may contribute. The walk already terminates on
     * its own for the built-in anchors — each body's floor is strictly below the previous one's
     * — so this only bounds a third-party {@link com.kuronami.isekaiapi.api.remap.SurfaceAnchor}
     * that reports a non-descending column.
     */
    private static final int MAX_BODIES = 32;

    public static final MapCodec<ColumnRelativeModifier> CODEC =
            ColumnBand.MAP_CODEC.xmap(ColumnRelativeModifier::new, m -> m.band);

    private final ColumnBand band;

    public ColumnRelativeModifier(ColumnBand band) {
        this.band = band;
    }

    /** The band this modifier resolves per column. */
    public ColumnBand band() {
        return band;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext ctx, RandomSource rand, BlockPos pos) {
        Integer topY = band.top().resolveY(ctx, pos);
        if (topY == null) return Stream.empty();
        Integer bottomY = band.bottom().resolveY(ctx, pos);
        if (bottomY == null) return Stream.empty();

        WorldGenLevel level = ctx.getLevel();
        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight();
        // Drawn on the first body that can actually take a placement, so a column that yields
        // nothing draws nothing — the same random source this modifier consumed before.
        double depth = 0.0;
        boolean drawn = false;
        List<BlockPos> out = null;

        for (int body = 0; body < MAX_BODIES; body++) {
            // Both anchors name free space; a body needs at least one block between them.
            if (topY - bottomY >= 2) {
                if (!drawn) {
                    depth = band.sampleDepth(rand);
                    drawn = true;
                }
                int y = band.resolveY(topY, bottomY, depth);
                if (y >= minBuild && y < maxBuild) {
                    if (out == null) out = new ArrayList<>(2);
                    out.add(new BlockPos(pos.getX(), y, pos.getZ()));
                }
            }

            Integer nextTop = band.top().resolveYBelow(ctx, pos, bottomY);
            if (nextTop == null || nextTop > bottomY) break;
            Integer nextBottom = band.bottom().resolveYBelow(ctx, pos, nextTop);
            // The walk only ever descends; anything else would revisit a body already handled.
            if (nextBottom == null || nextBottom >= bottomY) break;
            topY = nextTop;
            bottomY = nextBottom;
        }
        return out == null ? Stream.empty() : out.stream();
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.COLUMN_RELATIVE.get();
    }
}
