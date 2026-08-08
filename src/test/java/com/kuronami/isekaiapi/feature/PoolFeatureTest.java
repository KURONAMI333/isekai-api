package com.kuronami.isekaiapi.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PoolFeature} is the one feature here whose correctness is a statement about terrain it
 * refuses: a pool that places on ground it cannot be contained by is a pool with an open edge.
 * These tests drive the algorithm against synthetic terrain through the {@link PoolFeature.Cells}
 * seam, so the containment rule is checked directly rather than inferred from a screenshot.
 *
 * <p>The refusal cases use {@link NoWriteCells}, which throws on any write. "Returns false
 * without touching the world" is therefore enforced by the fake, not by an assertion someone
 * has to remember to make.
 */
class PoolFeatureTest {

    private static BlockState stone;
    private static BlockState air;
    private static BlockState water;

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
        stone = Blocks.STONE.defaultBlockState();
        air = Blocks.AIR.defaultBlockState();
        water = Blocks.WATER.defaultBlockState();
    }

    // ------------------------------------------------------------------
    // Synthetic terrain
    // ------------------------------------------------------------------

    /** Block at a position relative to the pool origin. */
    private interface Terrain {
        BlockState at(int dx, int dy, int dz);
    }

    /** Ground surface at dy = -1, solid for {@code thickness} blocks, void below that. */
    private static Terrain plate(int thickness) {
        return (dx, dy, dz) -> (dy < 0 && dy >= -thickness) ? stone : air;
    }

    /** Solid all the way down. The easy case. */
    private static Terrain bedrock() {
        return (dx, dy, dz) -> dy < 0 ? stone : air;
    }

    /** Nothing at all — the feature is hovering in mid-air. */
    private static Terrain openAir() {
        return (dx, dy, dz) -> air;
    }

    /**
     * A plateau of half-width {@code half} whose top is at dy = -1; outside it the ground drops
     * by {@code drop} blocks. This is the "端が囲われていない" case — the pool's own surface
     * would sit above the neighbouring ground.
     */
    private static Terrain plateau(int half, int drop) {
        return (dx, dy, dz) -> {
            int top = (Math.abs(dx) <= half && Math.abs(dz) <= half) ? -1 : -1 - drop;
            return dy <= top ? stone : air;
        };
    }

    /** Solid ground for dx <= edge, void beyond it — standing at the lip of a floating plate. */
    private static Terrain plateEdge(int edge) {
        return (dx, dy, dz) -> (dy < 0 && dx <= edge) ? stone : air;
    }

    // ------------------------------------------------------------------
    // Cells fakes
    // ------------------------------------------------------------------

    /** Records every write. Reads come from the terrain; writes never feed back into it. */
    private static final class RecordingCells implements PoolFeature.Cells {
        private final Terrain terrain;
        final List<int[]> carved = new ArrayList<>();
        final List<int[]> filled = new ArrayList<>();
        final List<int[]> rimmed = new ArrayList<>();

        RecordingCells(Terrain terrain) {
            this.terrain = terrain;
        }

        @Override
        public BlockState get(int dx, int dy, int dz) {
            return terrain.at(dx, dy, dz);
        }

        @Override
        public void carve(int dx, int dy, int dz) {
            carved.add(new int[]{dx, dy, dz});
        }

        @Override
        public void fill(int dx, int dy, int dz) {
            filled.add(new int[]{dx, dy, dz});
        }

        @Override
        public void rim(int dx, int dy, int dz) {
            rimmed.add(new int[]{dx, dy, dz});
        }

        List<int[]> allWrites() {
            List<int[]> all = new ArrayList<>(carved);
            all.addAll(filled);
            all.addAll(rimmed);
            return all;
        }
    }

    /** Any write is a test failure. Used for every case that must refuse to place. */
    private static final class NoWriteCells implements PoolFeature.Cells {
        private final Terrain terrain;

        NoWriteCells(Terrain terrain) {
            this.terrain = terrain;
        }

        @Override
        public BlockState get(int dx, int dy, int dz) {
            return terrain.at(dx, dy, dz);
        }

        @Override
        public void carve(int dx, int dy, int dz) {
            throw new AssertionError("carved " + dx + "," + dy + "," + dz + " on terrain that cannot hold a pool");
        }

        @Override
        public void fill(int dx, int dy, int dz) {
            throw new AssertionError("filled " + dx + "," + dy + "," + dz + " on terrain that cannot hold a pool");
        }

        @Override
        public void rim(int dx, int dy, int dz) {
            throw new AssertionError("rimmed " + dx + "," + dy + "," + dz + " on terrain that cannot hold a pool");
        }
    }

    /** Refusal cases: any write throws. */
    private static boolean place(Terrain terrain, int radius, int depth, long seed) {
        return PoolFeature.placeInto(new NoWriteCells(terrain), radius, depth, water,
                RandomSource.create(seed));
    }

    /** Threshold probes: the answer is the boolean, writes are allowed and ignored. */
    private static boolean holds(Terrain terrain, int radius, int depth, long seed) {
        return PoolFeature.placeInto(new RecordingCells(terrain), radius, depth, water,
                RandomSource.create(seed));
    }

    // ------------------------------------------------------------------
    // Refusal: nothing is written when the terrain cannot hold the fluid
    // ------------------------------------------------------------------

    @Test
    void midAirPlacesNothing() {
        for (long seed = 0; seed < 16; seed++) {
            assertFalse(place(openAir(), 4, 2, seed), "seed " + seed);
        }
    }

    @Test
    void plateThinnerThanTheBasinPlacesNothing() {
        for (long seed = 0; seed < 16; seed++) {
            assertFalse(place(plate(1), 4, 2, seed), "seed " + seed);
        }
    }

    @Test
    void oneBlockLedgeInsideTheFootprintPlacesNothing() {
        // Origin on top of a 3x3 plateau one block above everything around it: the pool's
        // surface would hang over the drop. This is the shape kura rejected six times.
        for (long seed = 0; seed < 16; seed++) {
            assertFalse(place(plateau(1, 1), 4, 2, seed), "seed " + seed);
        }
    }

    @Test
    void lipOfAFloatingPlatePlacesNothing() {
        for (long seed = 0; seed < 16; seed++) {
            assertFalse(place(plateEdge(0), 4, 2, seed), "seed " + seed);
        }
    }

    // ------------------------------------------------------------------
    // Placement: terrain that can hold it, does
    // ------------------------------------------------------------------

    @Test
    void solidGroundGetsAPool() {
        RecordingCells cells = new RecordingCells(bedrock());
        assertTrue(PoolFeature.placeInto(cells, 4, 2, water, RandomSource.create(7)));
        assertFalse(cells.filled.isEmpty(), "no fluid placed");
        assertFalse(cells.rimmed.isEmpty(), "no rim placed");
        for (int[] p : cells.filled) {
            assertTrue(p[1] < 0, "fluid above the waterline at y=" + p[1]);
        }
        for (int[] p : cells.carved) {
            assertTrue(p[1] >= 0, "carved below the waterline at y=" + p[1]);
        }
    }

    @Test
    void smallestPoolStillPlaces() {
        // radius 1 / depth 1 is the codec's floor; the scaled ellipsoids must not degenerate
        // to an empty blob (which would return false with nothing placed).
        for (long seed = 0; seed < 16; seed++) {
            RecordingCells cells = new RecordingCells(bedrock());
            assertTrue(PoolFeature.placeInto(cells, 1, 1, water, RandomSource.create(seed)),
                    "seed " + seed);
            assertFalse(cells.filled.isEmpty(), "seed " + seed + " placed no fluid");
        }
    }

    @Test
    void writesStayWithinRadiusPlusOne() {
        int radius = 6;
        int depth = 3;
        RecordingCells cells = new RecordingCells(bedrock());
        assertTrue(PoolFeature.placeInto(cells, radius, depth, water, RandomSource.create(99)));
        for (int[] p : cells.allWrites()) {
            assertTrue(Math.abs(p[0]) <= radius + 1 && Math.abs(p[2]) <= radius + 1,
                    "write outside the footprint at " + p[0] + "," + p[1] + "," + p[2]);
            assertTrue(p[1] >= -(depth + 1) && p[1] <= depth,
                    "write outside the vertical box at y=" + p[1]);
        }
    }

    @Test
    void codecBoundsDoNotOverflowTheGrid() {
        // The codec accepts depth up to 32 and xz_radius up to 64 (clamped to MAX_RADIUS).
        RecordingCells cells = new RecordingCells(bedrock());
        assertTrue(PoolFeature.placeInto(cells, PoolFeature.MAX_RADIUS, 32, water,
                RandomSource.create(3)));
        assertTrue(PoolFeature.MAX_RADIUS <= 14,
                "radius must keep reads inside the 3x3 chunk region available during worldgen");
    }

    // ------------------------------------------------------------------
    // Outline
    // ------------------------------------------------------------------

    private static Set<Long> footprint(PoolFeature.Blob blob, int radius, int depth) {
        Set<Long> cells = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -depth; dy <= depth - 1; dy++) {
                    if (blob.get(dx, dy, dz)) {
                        cells.add(key(dx, dz));
                        break;
                    }
                }
            }
        }
        return cells;
    }

    private static long key(int dx, int dz) {
        return ((long) (dx + 128) << 16) | (dz + 128);
    }

    private static Set<Long> disc(int radius, double discRadius) {
        Set<Long> cells = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= discRadius * discRadius) {
                    cells.add(key(dx, dz));
                }
            }
        }
        return cells;
    }

    @Test
    void outlineIsNeverACircle() {
        int radius = 4;
        int depth = 2;
        Set<Set<Long>> distinct = new HashSet<>();
        for (long seed = 0; seed < 32; seed++) {
            Set<Long> shape = footprint(PoolFeature.shape(radius, depth, RandomSource.create(seed)),
                    radius, depth);
            distinct.add(shape);
            for (int rr = 0; rr <= radius; rr++) {
                assertNotEquals(disc(radius, rr + 0.5), shape,
                        "seed " + seed + " produced a plain disc of radius " + rr);
            }
        }
        assertTrue(distinct.size() >= 20,
                "only " + distinct.size() + " distinct outlines in 32 seeds — the shape is not varying");
    }

    @Test
    void outlineIsReproducibleForTheSameSeed() {
        int radius = 5;
        int depth = 2;
        Set<Long> first = footprint(PoolFeature.shape(radius, depth, RandomSource.create(4242)),
                radius, depth);
        Set<Long> second = footprint(PoolFeature.shape(radius, depth, RandomSource.create(4242)),
                radius, depth);
        assertEquals(first, second);
    }

    // ------------------------------------------------------------------
    // How much ground the containment rule actually demands
    // ------------------------------------------------------------------

    @Test
    void requiredPlateThicknessIsDepthPlusOne() {
        for (int depth = 1; depth <= 3; depth++) {
            int minimum = -1;
            for (int thickness = 1; thickness <= depth + 4; thickness++) {
                if (holds(plate(thickness), 4, depth, 11L)) {
                    minimum = thickness;
                    break;
                }
            }
            assertTrue(minimum == depth || minimum == depth + 1,
                    "depth " + depth + " needed a plate " + minimum
                            + " thick (expected " + depth + " or " + (depth + 1) + ")");
        }
    }

    @Test
    void requiredFlatHalfWidthIsRadiusPlusOne() {
        int radius = 4;
        int minimum = -1;
        for (int half = 1; half <= radius + 3; half++) {
            if (holds(plateau(half, 1), radius, 2, 11L)) {
                minimum = half;
                break;
            }
        }
        assertTrue(minimum == radius || minimum == radius + 1,
                "radius " + radius + " needed flat ground of half-width " + minimum
                        + " (expected " + radius + " or " + (radius + 1) + ")");
    }

    // ------------------------------------------------------------------
    // Datapack compatibility
    // ------------------------------------------------------------------

    private static PoolFeature.Config decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        DataResult<PoolFeature.Config> result =
                PoolFeature.Config.CODEC.parse(JsonOps.INSTANCE, el);
        return result.getOrThrow(msg -> new AssertionError("pool config did not decode: " + msg));
    }

    @Test
    void v1DatapackConfigStillDecodes() {
        // Verbatim shape of the config shipped with isekai_api 1.1.0 — no new required fields
        // may appear, or every published datapack using isekai_api:pool stops loading.
        PoolFeature.Config config = decode("""
                {
                  "fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
                  "rim_block": {"type": "minecraft:simple_state_provider",
                                "state": {"Name": "minecraft:sand"}},
                  "xz_radius": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 5},
                  "depth": 2
                }""");
        assertEquals(Blocks.WATER.defaultBlockState(), config.fluid());
        assertEquals(2, config.depth());
    }

    @Test
    void depthStaysOptionalAndRadiusStillAcceptsABareInt() {
        PoolFeature.Config config = decode("""
                {
                  "fluid": {"Name": "minecraft:lava"},
                  "rim_block": {"type": "minecraft:simple_state_provider",
                                "state": {"Name": "minecraft:stone"}},
                  "xz_radius": 4
                }""");
        assertEquals(2, config.depth(), "depth must keep defaulting to 2");
        assertEquals(4, config.xzRadius().sample(RandomSource.create(1)));
    }
}
