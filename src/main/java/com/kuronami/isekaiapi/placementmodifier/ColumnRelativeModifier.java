package com.kuronami.isekaiapi.placementmodifier;

import com.kuronami.isekaiapi.api.remap.ColumnBand;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;
import org.jetbrains.annotations.ApiStatus;

/**
 * Places features at a depth into the column's own terrain rather than at an absolute Y.
 * Resolves the {@link ColumnBand}'s two anchors for the column, draws a normalized depth from
 * the band, and converts it to a Y. Drop-in replacement for {@code minecraft:height_range} when
 * the terrain's altitude varies per column.
 *
 * <p>Yields nothing when either anchor fails to resolve (no terrain in this column, or a body
 * that never ends) or when the two anchors leave no solid space between them — so void columns
 * cost one heightmap read and are skipped.
 *
 * <p>JSON form: the {@link ColumnBand} fields inline under
 * {@code "type": "isekai_api:column_relative"}.
 */
@ApiStatus.Internal
public class ColumnRelativeModifier extends PlacementModifier {

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
        // Both anchors name free space; a body needs at least one block between them.
        if (topY - bottomY < 2) return Stream.empty();

        int y = band.resolveY(topY, bottomY, band.sampleDepth(rand));
        WorldGenLevel level = ctx.getLevel();
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return Stream.empty();
        return Stream.of(new BlockPos(pos.getX(), y, pos.getZ()));
    }

    @Override
    public PlacementModifierType<?> type() {
        return IsekaiPlacementModifiers.COLUMN_RELATIVE.get();
    }
}
