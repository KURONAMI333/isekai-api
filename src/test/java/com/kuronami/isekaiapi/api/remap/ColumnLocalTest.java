package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RemapStrategy.ColumnLocal}'s projection, including the exact geometry the cosmos
 * acceptance datapack ships. The table below is the non-regression contract: each row is one
 * of cosmos's ores, its declared vanilla Y band, and the block offsets its hand-written
 * placement produced before the datapack moved onto {@code ore_strategy}. If the arithmetic or
 * the rounding direction ever shifts, this fails in milliseconds instead of costing a fresh
 * world and an RCON probe run.
 */
class ColumnLocalTest {

    private static final RemapContext CTX =
            new RemapContext(new VerticalRange(-64, 320, HeightDistribution.UNIFORM), -64, 319);

    /** One cosmos ore: declared band, whether it hangs off the top, and its shallow/deep offsets. */
    private record OreRow(String name, int minY, int maxY, boolean fromTop, int shallow, int deep) {}

    private static final List<OreRow> COSMOS_ORES = List.of(
            // Surface-anchored: offsets are blocks below the surface (negative).
            new OreRow("coal",          51,  62, true,   -2, -13),
            new OreRow("coal_rich",     47,  62, true,   -2, -17),
            new OreRow("copper",        52,  62, true,   -2, -12),
            new OreRow("copper_rich",   48,  62, true,   -2, -16),
            new OreRow("iron",          42,  61, true,   -3, -22),
            new OreRow("iron_rich",     38,  61, true,   -3, -26),
            new OreRow("iron_deep",      4,  40, true,  -24, -60),
            // Core-anchored: offsets are blocks above the underside (positive).
            new OreRow("diamond",      -63, -48, false,  16,   1),
            new OreRow("diamond_rich", -63, -44, false,  20,   1),
            new OreRow("emerald",      -56, -34, false,  30,   8),
            new OreRow("gold",         -57, -40, false,  24,   7),
            new OreRow("gold_rich",    -58, -36, false,  28,   6),
            new OreRow("lapis",        -50, -33, false,  31,  14),
            new OreRow("lapis_rich",   -52, -32, false,  32,  12),
            new OreRow("redstone",     -62, -42, false,  22,   2),
            new OreRow("redstone_rich", -62, -38, false, 26,   2));

    @Test void cosmosOreGeometryIsUnchanged() {
        RemapStrategy.ColumnLocal strategy = RemapStrategy.ColumnLocal.DEFAULT;
        for (OreRow ore : COSMOS_ORES) {
            ColumnBand band = strategy
                    .remapToColumn(new VerticalRange(ore.minY(), ore.maxY(), HeightDistribution.UNIFORM), CTX)
                    .orElseThrow(() -> new AssertionError(ore.name() + ": no column band"));
            assertEquals(ore.fromTop(), band.anchoredToTop(), ore.name() + ": anchor end");

            int topY = 240;      // arbitrary body; the offsets must not depend on it
            int bottomY = 100;
            int shallowY = band.resolveY(topY, bottomY, band.fromDepth());
            int deepY = band.resolveY(topY, bottomY, band.toDepth());
            int anchor = ore.fromTop() ? topY : bottomY;
            assertEquals(anchor + ore.shallow(), shallowY, ore.name() + ": shallow offset");
            assertEquals(anchor + ore.deep(), deepY, ore.name() + ": deep offset");
        }
    }

    @Test void sameOffsetsAtEveryAltitude() {
        RemapStrategy.ColumnLocal strategy = RemapStrategy.ColumnLocal.DEFAULT;
        // Two bodies 300 blocks apart, both 120 thick — the internal layout must be identical.
        int[][] bodies = {{300, 180}, {0, -120}};
        for (OreRow ore : COSMOS_ORES) {
            ColumnBand band = strategy
                    .remapToColumn(new VerticalRange(ore.minY(), ore.maxY(), HeightDistribution.UNIFORM), CTX)
                    .orElseThrow();
            int highOffset = band.resolveY(bodies[0][0], bodies[0][1], band.toDepth())
                    - (ore.fromTop() ? bodies[0][0] : bodies[0][1]);
            int lowOffset = band.resolveY(bodies[1][0], bodies[1][1], band.toDepth())
                    - (ore.fromTop() ? bodies[1][0] : bodies[1][1]);
            assertEquals(highOffset, lowOffset, ore.name() + ": offset drifted with altitude");
        }
    }

    @Test void depthClampsToTheDeclaredColumn() {
        RemapStrategy.ColumnLocal strategy = RemapStrategy.ColumnLocal.DEFAULT;
        assertEquals(0.0, strategy.depthOf(320));   // above the surface reference
        assertEquals(1.0, strategy.depthOf(-200));  // below the floor reference
        assertEquals(0.5, strategy.depthOf(0));
    }

    @Test void distributionCarriesThrough() {
        ColumnBand band = RemapStrategy.ColumnLocal.DEFAULT
                .remapToColumn(new VerticalRange(-60, -40, HeightDistribution.TRAPEZOID), CTX)
                .orElseThrow();
        assertEquals(HeightDistribution.TRAPEZOID, band.distribution());
    }

    @Test void compressedThicknessShrinksDepths() {
        // The GAP_LOG's 48-block reference: vanilla's 128-block column squeezed into 48.
        RemapStrategy.ColumnLocal compressed = new RemapStrategy.ColumnLocal(
                SurfaceAnchor.WorldSurface.INSTANCE, SurfaceAnchor.WorldFloor.DEFAULT,
                ColumnBand.DepthScale.BLOCKS, 48, 64, -64);
        ColumnBand band = compressed
                .remapToColumn(new VerticalRange(29, 59, HeightDistribution.UNIFORM), CTX)
                .orElseThrow();
        assertEquals(200 - 2, band.resolveY(200, 120, band.fromDepth()));
        assertEquals(200 - 13, band.resolveY(200, 120, band.toDepth()));
    }

    @Test void otherStrategiesStayOnTheAbsolutePath() {
        VerticalRange range = new VerticalRange(-64, 16, HeightDistribution.UNIFORM);
        assertEquals(Optional.empty(), RemapStrategy.Linear.INSTANCE.remapToColumn(range, CTX));
        assertEquals(Optional.empty(), RemapStrategy.Identity.INSTANCE.remapToColumn(range, CTX));
        assertEquals(Optional.empty(), new RemapStrategy.CountScale(0.5).remapToColumn(range, CTX));
    }

    @Test void pipePropagatesTheColumnBand() {
        VerticalRange range = new VerticalRange(51, 62, HeightDistribution.UNIFORM);
        RemapStrategy pipe = new RemapStrategy.Pipe(
                List.of(new RemapStrategy.CountScale(0.5), RemapStrategy.ColumnLocal.DEFAULT));
        ColumnBand band = pipe.remapToColumn(range, CTX).orElseThrow();
        assertEquals(200 - 13, band.resolveY(200, 120, band.toDepth()));
        assertEquals(0.5, pipe.countFactor());
    }

    @Test void rejectsInvertedReferenceColumn() {
        assertThrows(IllegalArgumentException.class, () -> new RemapStrategy.ColumnLocal(
                SurfaceAnchor.WorldSurface.INSTANCE, SurfaceAnchor.WorldFloor.DEFAULT,
                ColumnBand.DepthScale.BLOCKS, 128, -64, 64));
    }
}
