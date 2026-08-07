package com.kuronami.isekaiapi.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry contract for {@code isekai_api:pool}'s outline.
 *
 * <p>Four properties carry the feature: the default is byte-for-byte the old circle (existing
 * datapacks keep their look), an irregular outline actually departs from that circle, the
 * outline is a pure function of its seed, and — the one that matters for whether the fluid
 * stays in — the footprint never leaves the nominal disc and never splits into islands.
 */
class PoolFootprintTest {

    private static final long[] SEEDS = {
            0L, 1L, -1L, 42L, 1234567L, -987654321L, Long.MIN_VALUE, Long.MAX_VALUE,
            0x5DEECE66DL, 8675309L, -42L, 999999937L
    };

    private static Set<Long> asSet(List<int[]> cells) {
        Set<Long> out = new HashSet<>();
        for (int[] c : cells) out.add(((long) c[0] << 32) ^ (c[1] & 0xFFFFFFFFL));
        return out;
    }

    /** The disc the pre-2.1.0 implementation carved: {@code dx² + dz² <= (r + 0.5)²}. */
    private static Set<Long> legacyCircle(int radius) {
        double edgeSq = (radius + 0.5) * (radius + 0.5);
        List<int[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= edgeSq) cells.add(new int[]{dx, dz});
            }
        }
        return asSet(cells);
    }

    @Test
    void defaultIrregularityReproducesTheLegacyCircle() {
        for (int radius = 1; radius <= 16; radius++) {
            for (long seed : SEEDS) {
                assertEquals(legacyCircle(radius), asSet(PoolFeature.footprint(radius, 0.0, seed)),
                        "irregularity 0 must stay an exact circle (r=" + radius + ")");
            }
        }
    }

    @Test
    void irregularOutlineDepartsFromTheCircle() {
        for (int radius = 4; radius <= 12; radius++) {
            for (long seed : SEEDS) {
                Set<Long> shaped = asSet(PoolFeature.footprint(radius, 0.35, seed));
                assertFalse(shaped.equals(legacyCircle(radius)),
                        "irregularity 0.35 must bite into the circle (r=" + radius
                                + ", seed=" + seed + ")");
            }
        }
    }

    /** Different seeds must give different outlines — otherwise every pool looks alike. */
    @Test
    void seedChangesTheOutline() {
        Set<Set<Long>> distinct = new HashSet<>();
        for (long seed : SEEDS) distinct.add(asSet(PoolFeature.footprint(9, 0.35, seed)));
        assertTrue(distinct.size() >= SEEDS.length - 1,
                "outlines should be seed-dependent, got " + distinct.size()
                        + " distinct shapes from " + SEEDS.length + " seeds");
    }

    @Test
    void sameInputsGiveTheSameOutline() {
        for (double irregularity : new double[]{0.0, 0.15, 0.35, 0.6, 1.0}) {
            for (long seed : SEEDS) {
                List<int[]> a = PoolFeature.footprint(11, irregularity, seed);
                List<int[]> b = PoolFeature.footprint(11, irregularity, seed);
                assertEquals(a.size(), b.size(), "footprint size must be reproducible");
                for (int i = 0; i < a.size(); i++) {
                    assertEquals(a.get(i)[0], b.get(i)[0], "dx at index " + i);
                    assertEquals(a.get(i)[1], b.get(i)[1], "dz at index " + i);
                }
            }
        }
    }

    /** Same world seed + same block position ⇒ same shape seed, and neighbours differ. */
    @Test
    void shapeSeedIsPositionalAndStable() {
        assertEquals(PoolFeature.shapeSeed(1234L, 100, -250),
                PoolFeature.shapeSeed(1234L, 100, -250));
        Set<Long> seeds = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) seeds.add(PoolFeature.shapeSeed(1234L, x, z));
        }
        assertEquals(256, seeds.size(), "nearby positions must not collide");
        assertFalse(PoolFeature.shapeSeed(1234L, 100, -250) == PoolFeature.shapeSeed(1235L, 100, -250),
                "world seed must change the shape");
    }

    /**
     * The wall guarantee. Every carved cell lies inside the nominal radius-r disc, so the
     * cells bounding the pool are either carved too or untouched terrain from within that
     * same disc — never a cell whose surroundings the placement filter never looked at.
     */
    @Test
    void footprintNeverLeavesTheNominalDisc() {
        for (int radius = 1; radius <= 16; radius++) {
            double edgeSq = (radius + 0.5) * (radius + 0.5);
            for (double irregularity : new double[]{0.0, 0.1, 0.35, 0.6, 0.85, 1.0}) {
                for (long seed : SEEDS) {
                    for (int[] c : PoolFeature.footprint(radius, irregularity, seed)) {
                        assertTrue(c[0] * c[0] + c[1] * c[1] <= edgeSq,
                                "cell " + c[0] + "," + c[1] + " escapes r=" + radius);
                    }
                }
            }
        }
    }

    /**
     * A split footprint would leave a disconnected puddle of fluid hanging in the terrain, and
     * a floor slab under nothing. Swept over the whole shipped {@code irregularity} range.
     */
    @Test
    void footprintIsOneConnectedRegionContainingTheCentre() {
        for (int radius = 1; radius <= 16; radius++) {
            for (double irregularity : new double[]{0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0}) {
                for (long seed : SEEDS) {
                    List<int[]> cells = PoolFeature.footprint(radius, irregularity, seed);
                    assertFalse(cells.isEmpty(), "footprint must not be empty");
                    Set<Long> present = asSet(cells);
                    assertTrue(present.contains(0L), "footprint must contain the origin cell");
                    assertEquals(present.size(), floodFill(present),
                            "footprint split into islands (r=" + radius
                                    + ", irregularity=" + irregularity + ", seed=" + seed + ")");
                }
            }
        }
    }

    /** Size of the 4-connected component containing {@code (0, 0)}. */
    private static int floodFill(Set<Long> present) {
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        seen.add(0L);
        queue.add(new int[]{0, 0});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] step : steps) {
                int nx = cur[0] + step[0];
                int nz = cur[1] + step[1];
                long k = ((long) nx << 32) ^ (nz & 0xFFFFFFFFL);
                if (present.contains(k) && seen.add(k)) queue.add(new int[]{nx, nz});
            }
        }
        return seen.size();
    }

    /** Bites only remove cells, so an irregular pool is never larger than the plain circle. */
    @Test
    void irregularityOnlyShrinks() {
        for (int radius = 3; radius <= 14; radius++) {
            int circle = legacyCircle(radius).size();
            for (long seed : SEEDS) {
                assertTrue(PoolFeature.footprint(radius, 0.35, seed).size() <= circle,
                        "irregular footprint grew past the circle at r=" + radius);
            }
        }
    }

    // ---- codec ------------------------------------------------------------------------

    @BeforeAll
    static void boot() {
        Bootstrap.bootStrap();
    }

    private static PoolFeature.Config decode(String json) {
        JsonElement el = JsonParser.parseString(json);
        return PoolFeature.Config.CODEC.parse(JsonOps.INSTANCE, el)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg + " for " + json));
    }

    /** A pre-2.1.0 datapack entry (no {@code irregularity}) still decodes, and stays circular. */
    @Test
    void legacyJsonDecodesToACircle() {
        PoolFeature.Config config = decode("""
                {"fluid":{"Name":"minecraft:water"},
                 "rim_block":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:sand"}},
                 "xz_radius":{"type":"minecraft:uniform","min_inclusive":3,"max_inclusive":5},
                 "depth":2}
                """);
        assertEquals(0.0, config.irregularity(), "absent irregularity must default to a circle");
        assertEquals(2, config.depth());
        assertEquals(legacyCircle(5), asSet(PoolFeature.footprint(5, config.irregularity(), 42L)));
    }

    @Test
    void irregularityDecodes() {
        PoolFeature.Config config = decode("""
                {"fluid":{"Name":"minecraft:water"},
                 "rim_block":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:sand"}},
                 "xz_radius":5,
                 "irregularity":0.35}
                """);
        assertEquals(0.35, config.irregularity());
        assertEquals(2, config.depth(), "depth still defaults to 2");
    }

    @Test
    void irregularityOutOfRangeIsRejected() {
        assertTrue(PoolFeature.Config.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"fluid":{"Name":"minecraft:water"},
                 "rim_block":{"type":"minecraft:simple_state_provider","state":{"Name":"minecraft:sand"}},
                 "xz_radius":5,
                 "irregularity":1.5}
                """)).isError(), "irregularity above 1 must not decode");
    }
}
