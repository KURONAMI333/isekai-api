package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.api.remap.LayeredDescriptor;
import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import com.kuronami.isekaiapi.api.remap.SurfaceAnchor;
import com.kuronami.isekaiapi.api.remap.TransitionRule;
import com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural lock for {@link LayerResolver} — the evaluation point that makes
 * {@link TransitionRule.Blend} and {@link TransitionRule.Gap} do something. Before this
 * existed both variants decoded from JSON and were then dropped on the floor.
 *
 * <p>Uses {@code Level.OVERWORLD} and pure record fields, so no game bootstrap is needed.
 */
class LayerResolverTest {

    private static final WorldshapeDescriptor LOWER = descriptor(0, 64);
    private static final WorldshapeDescriptor UPPER = descriptor(64, 128);

    private static WorldshapeDescriptor descriptor(int minY, int maxY) {
        return WorldshapeDescriptor.builder()
                .dimension(Level.OVERWORLD)
                .playableRange(new VerticalRange(minY, maxY, HeightDistribution.UNIFORM))
                .surfaceAnchor(new SurfaceAnchor.WorldSurface())
                .oreStrategy(new RemapStrategy.Identity())
                .structureStrategy(new RemapStrategy.Identity())
                .mobSpawnStrategy(new RemapStrategy.Identity())
                .defaultStructurePredicate(new SpatialPredicate.Always())
                .build();
    }

    private static List<LayeredDescriptor> stack(TransitionRule lowerTransition) {
        return List.of(
                new LayeredDescriptor(new VerticalRange(0, 64, HeightDistribution.UNIFORM),
                        LOWER, lowerTransition),
                new LayeredDescriptor(new VerticalRange(64, 128, HeightDistribution.UNIFORM),
                        UPPER, new TransitionRule.Hard()));
    }

    /** Share of positions on the y plane that resolve to the upper layer. */
    private static double upperShare(List<LayeredDescriptor> layers, int y) {
        int upper = 0;
        int total = 0;
        for (int x = -40; x < 40; x++) {
            for (int z = -40; z < 40; z++) {
                if (LayerResolver.resolve(layers, x, y, z).orElse(null) == UPPER) upper++;
                total++;
            }
        }
        return (double) upper / total;
    }

    // ---- Hard ------------------------------------------------------------

    @Test
    void hardSeamIsExact() {
        var layers = stack(new TransitionRule.Hard());
        assertSame(LOWER, LayerResolver.resolve(layers, 7, 63, -3).orElseThrow());
        assertSame(UPPER, LayerResolver.resolve(layers, 7, 64, -3).orElseThrow());
    }

    @Test
    void positionOutsideEveryLayerResolvesToNothing() {
        var layers = stack(new TransitionRule.Hard());
        assertTrue(LayerResolver.resolve(layers, 0, -1, 0).isEmpty());
        assertTrue(LayerResolver.resolve(layers, 0, 128, 0).isEmpty());
        assertTrue(LayerResolver.resolve(List.of(), 0, 10, 0).isEmpty());
    }

    // ---- Blend -----------------------------------------------------------

    @Test
    void blendBandIsCentredOnTheSeamAndBoundedByBlendHeight() {
        var layers = stack(new TransitionRule.Blend(4));
        // Band is [62, 66); everything outside it stays pure.
        assertEquals(0.0, upperShare(layers, 61));
        assertEquals(1.0, upperShare(layers, 66));
        for (int y = 62; y < 66; y++) {
            double share = upperShare(layers, y);
            assertTrue(share > 0.0 && share < 1.0,
                    "y=" + y + " must mix both layers, got upper share " + share);
        }
    }

    @Test
    void blendOddsRiseMonotonicallyThroughTheBand() {
        var layers = stack(new TransitionRule.Blend(4));
        double previous = -1.0;
        for (int y = 62; y < 66; y++) {
            double share = upperShare(layers, y);
            assertTrue(share > previous, "upper share must increase with Y, y=" + y);
            previous = share;
        }
        // Nominal odds are (y - 62 + 0.5)/4 = .125 .375 .625 .875; allow sampling slack.
        assertEquals(0.125, upperShare(layers, 62), 0.05);
        assertEquals(0.875, upperShare(layers, 65), 0.05);
    }

    @Test
    void blendIsDeterministicPerPosition() {
        var layers = stack(new TransitionRule.Blend(4));
        for (int y = 62; y < 66; y++) {
            Optional<WorldshapeDescriptor> first = LayerResolver.resolve(layers, 12, y, -9);
            for (int repeat = 0; repeat < 5; repeat++) {
                assertEquals(first, LayerResolver.resolve(layers, 12, y, -9),
                        "same position must always resolve to the same layer");
            }
        }
    }

    @Test
    void blendHeightZeroBehavesAsHard() {
        var layers = stack(new TransitionRule.Blend(0));
        assertEquals(0.0, upperShare(layers, 63));
        assertEquals(1.0, upperShare(layers, 64));
    }

    @Test
    void blendWithoutAnAdjacentLayerAboveBehavesAsHard() {
        // Layers not touching: seam at 64, upper starts at 80 — nothing to blend into.
        var layers = List.of(
                new LayeredDescriptor(new VerticalRange(0, 64, HeightDistribution.UNIFORM),
                        LOWER, new TransitionRule.Blend(8)),
                new LayeredDescriptor(new VerticalRange(80, 128, HeightDistribution.UNIFORM),
                        UPPER, new TransitionRule.Hard()));
        assertSame(LOWER, LayerResolver.resolve(layers, 3, 63, 3).orElseThrow());
        assertTrue(LayerResolver.resolve(layers, 3, 64, 3).isEmpty());
        assertSame(UPPER, LayerResolver.resolve(layers, 3, 80, 3).orElseThrow());
    }

    @Test
    void yOnlyResolutionIgnoresBlend() {
        // A blend cannot be expressed without X/Z, so the Y-only path sees a hard seam —
        // this is what keeps the client fog hook from flickering inside the band.
        var layers = stack(new TransitionRule.Blend(4));
        for (int y = 62; y < 64; y++) {
            assertSame(LOWER, LayerResolver.resolveByY(layers, y).orElseThrow());
        }
        for (int y = 64; y < 66; y++) {
            assertSame(UPPER, LayerResolver.resolveByY(layers, y).orElseThrow());
        }
    }

    // ---- Gap -------------------------------------------------------------

    @Test
    void gapEmptiesTheTopOfTheOwningLayer() {
        var layers = stack(new TransitionRule.Gap(3));
        assertSame(LOWER, LayerResolver.resolve(layers, 1, 60, 1).orElseThrow());
        for (int y = 61; y < 64; y++) {
            assertTrue(LayerResolver.resolve(layers, 1, y, 1).isEmpty(),
                    "y=" + y + " is inside the gap");
        }
        assertSame(UPPER, LayerResolver.resolve(layers, 1, 64, 1).orElseThrow());
    }

    @Test
    void gapAppliesToYOnlyResolutionToo() {
        var layers = stack(new TransitionRule.Gap(3));
        assertFalse(LayerResolver.resolveByY(layers, 62).isPresent());
        assertTrue(LayerResolver.resolveByY(layers, 60).isPresent());
    }

    @Test
    void gapHeightZeroBehavesAsHard() {
        var layers = stack(new TransitionRule.Gap(0));
        assertSame(LOWER, LayerResolver.resolve(layers, 1, 63, 1).orElseThrow());
        assertSame(UPPER, LayerResolver.resolve(layers, 1, 64, 1).orElseThrow());
    }

    // ---- hash ------------------------------------------------------------

    @Test
    void sampleStaysInRangeAndVariesWithEachInput() {
        for (int i = 0; i < 200; i++) {
            double v = LayerResolver.sample(i, i * 3, -i, 64);
            assertTrue(v >= 0.0 && v < 1.0, "sample out of range: " + v);
        }
        assertTrue(LayerResolver.sample(0, 0, 0, 64) != LayerResolver.sample(1, 0, 0, 64));
        assertTrue(LayerResolver.sample(0, 0, 0, 64) != LayerResolver.sample(0, 1, 0, 64));
        assertTrue(LayerResolver.sample(0, 0, 0, 64) != LayerResolver.sample(0, 0, 1, 64));
        assertTrue(LayerResolver.sample(0, 0, 0, 64) != LayerResolver.sample(0, 0, 0, 65));
    }
}
