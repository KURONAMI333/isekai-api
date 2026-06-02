package com.kuronami.isekaiapi.densityfunction;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic test for {@link QuarterNegativeDF}: {@code v > 0 ? v : v*0.25}. No game bootstrap
 * needed — a constant child DF + a trivial context exercise the math directly.
 */
class QuarterNegativeDFTest {

    private static final DensityFunction.FunctionContext ORIGIN = new DensityFunction.FunctionContext() {
        @Override public int blockX() { return 0; }
        @Override public int blockY() { return 0; }
        @Override public int blockZ() { return 0; }
    };

    private static double q(double v) {
        return new QuarterNegativeDF(DensityFunctions.constant(v)).compute(ORIGIN);
    }

    @Test
    void positiveValuesPassThrough() {
        assertEquals(2.0, q(2.0), 1e-9);
        assertEquals(0.001, q(0.001), 1e-12);
    }

    @Test
    void negativeValuesAreQuartered() {
        assertEquals(-0.5, q(-2.0), 1e-9);
        assertEquals(-1.0, q(-4.0), 1e-9);
    }

    @Test
    void zeroStaysZero() {
        assertEquals(0.0, q(0.0), 1e-12);
    }

    @Test
    void minAndMaxAreMonotoneImages() {
        QuarterNegativeDF df = new QuarterNegativeDF(DensityFunctions.constant(-4.0));
        assertEquals(-1.0, df.minValue(), 1e-9);
        assertEquals(-1.0, df.maxValue(), 1e-9);
    }
}
