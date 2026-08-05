package com.kuronami.isekaiapi.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural lock for {@link StructureThinning} — the evaluation point that makes a
 * worldshape's {@code structure_strategy} {@code count_scale} factor do something. Before
 * this existed the factor decoded from JSON and was then dropped on the floor.
 *
 * <p>The properties that matter to a save are the ones asserted here: the surviving share
 * tracks the factor, the decision never changes for a given chunk, and two structure types
 * are thinned independently.
 */
class StructureThinningTest {

    private static final long SEED = 0x5EED_1234_5678L;
    private static final String VILLAGE = "minecraft:village_plains";

    private static double keptShare(double factor, String structureId) {
        int kept = 0;
        int total = 0;
        for (int cx = -60; cx < 60; cx++) {
            for (int cz = -60; cz < 60; cz++) {
                if (StructureThinning.keep(factor, SEED, cx, cz, structureId)) kept++;
                total++;
            }
        }
        return (double) kept / total;
    }

    @Test
    void factorOfOneOrMoreKeepsEverything() {
        assertEquals(1.0, keptShare(1.0, VILLAGE));
        assertTrue(StructureThinning.keep(2.5, SEED, 3, 4, VILLAGE));
    }

    @Test
    void factorOfZeroKeepsNothing() {
        assertEquals(0.0, keptShare(0.0, VILLAGE));
        assertFalse(StructureThinning.keep(-1.0, SEED, 3, 4, VILLAGE));
    }

    @Test
    void survivingShareTracksTheFactor() {
        for (double factor : new double[] {0.1, 0.25, 0.4, 0.5, 0.75, 0.9}) {
            assertEquals(factor, keptShare(factor, VILLAGE), 0.02,
                    "kept share must approximate count_scale factor " + factor);
        }
    }

    @Test
    void decisionIsStableForAGivenChunk() {
        for (int cx = -5; cx < 5; cx++) {
            for (int cz = -5; cz < 5; cz++) {
                boolean first = StructureThinning.keep(0.4, SEED, cx, cz, VILLAGE);
                for (int repeat = 0; repeat < 5; repeat++) {
                    assertEquals(first, StructureThinning.keep(0.4, SEED, cx, cz, VILLAGE),
                            "a chunk must always answer the same way — regenerating it "
                                    + "has to reproduce the original world");
                }
            }
        }
    }

    @Test
    void differentSeedsThinDifferentChunks() {
        int differences = 0;
        for (int cx = 0; cx < 40; cx++) {
            for (int cz = 0; cz < 40; cz++) {
                if (StructureThinning.keep(0.5, SEED, cx, cz, VILLAGE)
                        != StructureThinning.keep(0.5, SEED + 1, cx, cz, VILLAGE)) {
                    differences++;
                }
            }
        }
        assertTrue(differences > 400, "seeds must not share a thinning pattern, got " + differences);
    }

    @Test
    void structureTypesAreThinnedIndependently() {
        int differences = 0;
        for (int cx = 0; cx < 40; cx++) {
            for (int cz = 0; cz < 40; cz++) {
                if (StructureThinning.keep(0.5, SEED, cx, cz, VILLAGE)
                        != StructureThinning.keep(0.5, SEED, cx, cz, "minecraft:desert_pyramid")) {
                    differences++;
                }
            }
        }
        assertTrue(differences > 400,
                "thinning one structure must not correlate with another, got " + differences);
    }

    @Test
    void saltIsAStableFunctionOfTheIdText() {
        // Pinned so a Minecraft or JDK change to String/ResourceLocation hashing can never
        // silently re-roll which structures an existing world generates.
        assertEquals(StructureThinning.salt(VILLAGE), StructureThinning.salt("minecraft:village_plains"));
        assertEquals(0x811C9DC5, StructureThinning.salt(""));
        assertTrue(StructureThinning.salt("a") != StructureThinning.salt("b"));
    }

    @Test
    void sampleStaysInRange() {
        for (int i = 0; i < 500; i++) {
            double v = StructureThinning.sample(SEED + i, i, -i, StructureThinning.salt(VILLAGE));
            assertTrue(v >= 0.0 && v < 1.0, "sample out of range: " + v);
        }
    }
}
