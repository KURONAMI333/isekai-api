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

    /**
     * One island band of Sky World, expressed the way the anchors report it: the body occupies
     * Y 116..176, so the free space above it is 177 and below it 115.
     */
    private static final int ISLAND_TOP_ANCHOR = 177;
    private static final int ISLAND_BOTTOM_ANCHOR = 115;

    private static ColumnBand proportionalBand(RemapStrategy.ColumnLocal strategy, int minY, int maxY) {
        return strategy
                .remapToColumn(new VerticalRange(minY, maxY, HeightDistribution.UNIFORM), CTX)
                .orElseThrow();
    }

    private static RemapStrategy.ColumnLocal proportionalStrategy(int surfaceY, int floorY) {
        return new RemapStrategy.ColumnLocal(
                SurfaceAnchor.WorldSurface.INSTANCE, SurfaceAnchor.WorldFloor.DEFAULT,
                ColumnBand.DepthScale.PROPORTIONAL, ColumnBand.VANILLA_THICKNESS, surfaceY, floorY);
    }

    /**
     * Vanilla's {@code _upper} stone and ore variants, whose Y ranges lie wholly above the
     * default reference surface of Y 64. Every one of them clamps to depth 0.0 at both ends.
     */
    private static final List<OreRow> UPPER_ORES = List.of(
            new OreRow("ore_andesite_upper", 64, 128, true, 0, 0),
            new OreRow("ore_granite_upper", 64, 128, true, 0, 0),
            new OreRow("ore_diorite_upper", 64, 128, true, 0, 0),
            new OreRow("ore_coal_upper", 136, 320, true, 0, 0),
            new OreRow("ore_iron_upper", 80, 384, true, 0, 0));

    @Test void upperOresCollapseOntoTheSurfaceAnchorUnderVanillaReferences() {
        RemapStrategy.ColumnLocal strategy = proportionalStrategy(64, -64);
        for (OreRow ore : UPPER_ORES) {
            ColumnBand band = proportionalBand(strategy, ore.minY(), ore.maxY());
            assertEquals(band.fromDepth(), band.toDepth(), ore.name() + ": expected zero width");
            assertTrue(band.collapsedAtAnchor(), ore.name() + ": should report the collapse");
        }
    }

    @Test void widerReferenceColumnGivesUpperOresRealWidth() {
        // surface_y raised past the highest of these ranges: nothing clamps, every band has width.
        RemapStrategy.ColumnLocal strategy = proportionalStrategy(384, -64);
        for (OreRow ore : UPPER_ORES) {
            ColumnBand band = proportionalBand(strategy, ore.minY(), ore.maxY());
            assertTrue(band.toDepth() - band.fromDepth() > 0.0, ore.name() + ": still zero width");
            assertFalse(band.collapsedAtAnchor(), ore.name() + ": still collapsed");

            int shallowY = band.resolveY(ISLAND_TOP_ANCHOR, ISLAND_BOTTOM_ANCHOR, band.fromDepth());
            int deepY = band.resolveY(ISLAND_TOP_ANCHOR, ISLAND_BOTTOM_ANCHOR, band.toDepth());
            assertTrue(shallowY > deepY, ore.name() + ": band did not span any block");
            assertInsideBody(ore.name(), shallowY);
            assertInsideBody(ore.name(), deepY);
        }
    }

    @Test void collapsedBandsStillResolveIntoTheBodyNotIntoAir() {
        // The clamp is the safety net, not the cure: a collapsed band still names one plane, but
        // that plane is the topmost solid block rather than the air above it.
        RemapStrategy.ColumnLocal strategy = proportionalStrategy(64, -64);
        for (OreRow ore : UPPER_ORES) {
            ColumnBand band = proportionalBand(strategy, ore.minY(), ore.maxY());
            int y = band.resolveY(ISLAND_TOP_ANCHOR, ISLAND_BOTTOM_ANCHOR, band.fromDepth());
            assertEquals(ISLAND_TOP_ANCHOR - 1, y, ore.name() + ": resolved outside the body");
        }
    }

    /**
     * Ores whose vanilla range already straddled the reference column. Their depths are the
     * non-regression contract: nothing about the projection may move for them.
     */
    @Test void straddlingOresKeepTheirDepths() {
        RemapStrategy.ColumnLocal strategy = proportionalStrategy(64, -64);
        record Row(String name, int minY, int maxY, double from, double to) {}
        for (Row row : List.of(
                new Row("ore_andesite_lower", 0, 60, 0.03125, 0.5),
                new Row("ore_copper", -16, 112, 0.0, 0.625),
                new Row("ore_gold", -64, 32, 0.25, 1.0),
                new Row("ore_diamond_medium", -64, -4, 0.53125, 1.0),
                new Row("ore_lapis", -32, 32, 0.25, 0.75),
                new Row("ore_coal_lower", 0, 192, 0.0, 0.5))) {
            ColumnBand band = proportionalBand(strategy, row.minY(), row.maxY());
            assertEquals(row.from(), band.fromDepth(), row.name() + ": from_depth");
            assertEquals(row.to(), band.toDepth(), row.name() + ": to_depth");
            assertFalse(band.collapsedAtAnchor(), row.name() + ": must not be reported as collapsed");
        }
    }

    /**
     * The resolved Y of a straddling ore, on the same island band. Only an endpoint that used
     * to sit in the air moves, and only by one block; everything between is untouched.
     */
    @Test void straddlingOresResolveToTheSameBlocksAsBefore() {
        RemapStrategy.ColumnLocal strategy = proportionalStrategy(64, -64);
        record Row(String name, int minY, int maxY, int shallowY, int deepY) {}
        for (Row row : List.of(
                // Interior on both ends: identical to the pre-clamp arithmetic.
                new Row("ore_andesite_lower", 0, 60, 175, 146),
                new Row("ore_lapis", -32, 32, 161, 130),
                // Shallow end was the air above the body (177); now the block below it.
                new Row("ore_copper", -16, 112, 176, 138),
                new Row("ore_coal_lower", 0, 192, 176, 146),
                // Deep end was the air under the body (115); now the block above it.
                new Row("ore_gold", -64, 32, 161, 116),
                new Row("ore_diamond_medium", -64, -4, 144, 116))) {
            ColumnBand band = proportionalBand(strategy, row.minY(), row.maxY());
            assertEquals(row.shallowY(),
                    band.resolveY(ISLAND_TOP_ANCHOR, ISLAND_BOTTOM_ANCHOR, band.fromDepth()),
                    row.name() + ": shallow Y");
            assertEquals(row.deepY(),
                    band.resolveY(ISLAND_TOP_ANCHOR, ISLAND_BOTTOM_ANCHOR, band.toDepth()),
                    row.name() + ": deep Y");
        }
    }

    private static void assertInsideBody(String name, int y) {
        assertTrue(y <= ISLAND_TOP_ANCHOR - 1 && y >= ISLAND_BOTTOM_ANCHOR + 1,
                name + ": resolved to Y " + y + ", outside the body "
                        + (ISLAND_BOTTOM_ANCHOR + 1) + ".." + (ISLAND_TOP_ANCHOR - 1));
    }

    @Test void rejectsInvertedReferenceColumn() {
        assertThrows(IllegalArgumentException.class, () -> new RemapStrategy.ColumnLocal(
                SurfaceAnchor.WorldSurface.INSTANCE, SurfaceAnchor.WorldFloor.DEFAULT,
                ColumnBand.DepthScale.BLOCKS, 128, -64, 64));
    }
}
