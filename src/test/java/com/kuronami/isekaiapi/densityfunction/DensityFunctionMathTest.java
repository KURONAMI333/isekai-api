package com.kuronami.isekaiapi.densityfunction;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math tests for isekai density function primitives.
 * No Bootstrap needed — constant children + trivial contexts exercise the math directly.
 */
class DensityFunctionMathTest {

    // ----- FunctionContext helpers -----

    private static DensityFunction.FunctionContext ctx(int x, int y, int z) {
        return new DensityFunction.FunctionContext() {
            @Override public int blockX() { return x; }
            @Override public int blockY() { return y; }
            @Override public int blockZ() { return z; }
        };
    }

    private static final DensityFunction.FunctionContext ORIGIN = ctx(0, 0, 0);

    private static DensityFunction c(double v) {
        return DensityFunctions.constant(v);
    }

    // ===== SqueezeDF =====

    @Test void squeeze_zeroInput_isZero() {
        assertEquals(0.0, new SqueezeDF(c(0.0)).compute(ORIGIN), 1e-12);
    }

    @Test void squeeze_positiveOne_isEleven24ths() {
        // squeeze(1) = 1/2 - 1/24 = 11/24
        assertEquals(11.0 / 24.0, new SqueezeDF(c(1.0)).compute(ORIGIN), 1e-9);
    }

    @Test void squeeze_negativeOne_isMinusEleven24ths() {
        assertEquals(-11.0 / 24.0, new SqueezeDF(c(-1.0)).compute(ORIGIN), 1e-9);
    }

    @Test void squeeze_clampsAboveOne() {
        // input 5.0 is clamped to 1.0, same result as squeeze(1)
        assertEquals(11.0 / 24.0, new SqueezeDF(c(5.0)).compute(ORIGIN), 1e-9);
    }

    @Test void squeeze_clampsBelowNegativeOne() {
        assertEquals(-11.0 / 24.0, new SqueezeDF(c(-5.0)).compute(ORIGIN), 1e-9);
    }

    @Test void squeeze_midValue_formula() {
        double x = 0.5;
        double expected = x / 2.0 - x * x * x / 24.0;
        assertEquals(expected, new SqueezeDF(c(x)).compute(ORIGIN), 1e-9);
    }

    @Test void squeeze_minMaxValues() {
        SqueezeDF df = new SqueezeDF(c(0.0));
        assertEquals(-11.0 / 24.0, df.minValue(), 1e-9);
        assertEquals(11.0 / 24.0, df.maxValue(), 1e-9);
    }

    // ===== YEnvelopeDF =====

    @Test void yEnvelope_insideActiveBand_returnsOne() {
        // activeMinY=10, activeMaxY=20, gradientWidth=5
        YEnvelopeDF df = new YEnvelopeDF(10, 20, 5, false);
        assertEquals(1.0, df.compute(ctx(0, 15, 0)), 1e-9);
        assertEquals(1.0, df.compute(ctx(0, 10, 0)), 1e-9);
        assertEquals(1.0, df.compute(ctx(0, 20, 0)), 1e-9);
    }

    @Test void yEnvelope_outsideAllBands_returnsZero() {
        YEnvelopeDF df = new YEnvelopeDF(10, 20, 5, false);
        // below activeMinY - gradientWidth = 5
        assertEquals(0.0, df.compute(ctx(0, 4, 0)), 1e-9);
        // above activeMaxY + gradientWidth = 25
        assertEquals(0.0, df.compute(ctx(0, 26, 0)), 1e-9);
    }

    @Test void yEnvelope_rampUp_midGradient() {
        // gradientWidth=10, activeMinY=10 → ramp from y=0 to y=10
        // at y=5: (5 - (10 - 10)) / 10 = 0.5
        YEnvelopeDF df = new YEnvelopeDF(10, 20, 10, false);
        assertEquals(0.5, df.compute(ctx(0, 5, 0)), 1e-9);
    }

    @Test void yEnvelope_rampDown_midGradient() {
        // at y=25: (activeMaxY + gradientWidth - y) / gradientWidth = (30 - 25)/10 = 0.5
        YEnvelopeDF df = new YEnvelopeDF(10, 20, 10, false);
        assertEquals(0.5, df.compute(ctx(0, 25, 0)), 1e-9);
    }

    @Test void yEnvelope_inverted_flipsPolarity() {
        YEnvelopeDF df = new YEnvelopeDF(10, 20, 5, true);
        assertEquals(0.0, df.compute(ctx(0, 15, 0)), 1e-9); // inside = 0 when inverted
        assertEquals(1.0, df.compute(ctx(0, 0, 0)), 1e-9);  // outside = 1 when inverted
    }

    @Test void yEnvelope_zeroGradientWidth_sharpCutoff() {
        YEnvelopeDF df = new YEnvelopeDF(10, 20, 0, false);
        assertEquals(1.0, df.compute(ctx(0, 15, 0)), 1e-9);
        assertEquals(0.0, df.compute(ctx(0, 9, 0)), 1e-9);
        assertEquals(0.0, df.compute(ctx(0, 21, 0)), 1e-9);
    }

    @Test void yEnvelope_maxYMustBeGreaterThanMinY_throws() {
        assertThrows(IllegalArgumentException.class, () -> new YEnvelopeDF(20, 10, 5, false));
        assertThrows(IllegalArgumentException.class, () -> new YEnvelopeDF(10, 10, 5, false));
    }

    @Test void yEnvelope_negativeGradientWidth_throws() {
        assertThrows(IllegalArgumentException.class, () -> new YEnvelopeDF(10, 20, -1, false));
    }

    // ===== MaskYRangeDF =====

    @Test void maskYRange_insideRange_returnsInside() {
        MaskYRangeDF df = new MaskYRangeDF(0, 100, c(5.0), c(-3.0));
        assertEquals(5.0, df.compute(ctx(0, 50, 0)), 1e-9);
        assertEquals(5.0, df.compute(ctx(0, 0, 0)), 1e-9);
        assertEquals(5.0, df.compute(ctx(0, 100, 0)), 1e-9);
    }

    @Test void maskYRange_outsideRange_returnsOutside() {
        MaskYRangeDF df = new MaskYRangeDF(0, 100, c(5.0), c(-3.0));
        assertEquals(-3.0, df.compute(ctx(0, -1, 0)), 1e-9);
        assertEquals(-3.0, df.compute(ctx(0, 101, 0)), 1e-9);
    }

    @Test void maskYRange_minMax_propagate() {
        MaskYRangeDF df = new MaskYRangeDF(0, 100, c(5.0), c(-3.0));
        assertEquals(-3.0, df.minValue(), 1e-9);
        assertEquals(5.0, df.maxValue(), 1e-9);
    }

    // ===== StepDF =====

    @Test void step_valueBelowThreshold_returnsLow() {
        StepDF df = new StepDF(c(0.5), 1.0, c(-10.0), c(10.0));
        assertEquals(-10.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void step_valueAtThreshold_returnsHigh() {
        // value < threshold → low; at threshold → high (>= threshold)
        StepDF df = new StepDF(c(1.0), 1.0, c(-10.0), c(10.0));
        assertEquals(10.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void step_valueAboveThreshold_returnsHigh() {
        StepDF df = new StepDF(c(2.0), 1.0, c(-10.0), c(10.0));
        assertEquals(10.0, df.compute(ORIGIN), 1e-9);
    }

    // ===== LerpDF =====

    @Test void lerp_tEqualsZero_returnsA() {
        LerpDF df = new LerpDF(c(0.0), c(3.0), c(7.0));
        assertEquals(3.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void lerp_tEqualsOne_returnsB() {
        LerpDF df = new LerpDF(c(1.0), c(3.0), c(7.0));
        assertEquals(7.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void lerp_tEqualsHalf_returnsMidpoint() {
        LerpDF df = new LerpDF(c(0.5), c(0.0), c(10.0));
        assertEquals(5.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void lerp_tClampsBelow0() {
        // t=-1 clamped to 0 → returns a
        LerpDF df = new LerpDF(c(-1.0), c(3.0), c(7.0));
        assertEquals(3.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void lerp_tClampsAbove1() {
        // t=2 clamped to 1 → returns b
        LerpDF df = new LerpDF(c(2.0), c(3.0), c(7.0));
        assertEquals(7.0, df.compute(ORIGIN), 1e-9);
    }

    // ===== ClampDF =====

    @Test void clamp_insideRange_passthrough() {
        ClampDF df = new ClampDF(c(5.0), 0.0, 10.0);
        assertEquals(5.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void clamp_belowMin_returnsMin() {
        ClampDF df = new ClampDF(c(-5.0), 0.0, 10.0);
        assertEquals(0.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void clamp_aboveMax_returnsMax() {
        ClampDF df = new ClampDF(c(15.0), 0.0, 10.0);
        assertEquals(10.0, df.compute(ORIGIN), 1e-9);
    }

    // ===== DistanceDF =====

    @Test void distance_xzMode_computesXZDistance() {
        DistanceDF df = new DistanceDF(0.0, 0.0, 0.0, DistanceDF.Mode.XZ);
        // (3,0,4) → sqrt(9+16)=5
        assertEquals(5.0, df.compute(ctx(3, 999, 4)), 1e-9);
    }

    @Test void distance_xzMode_ignoresY() {
        DistanceDF df = new DistanceDF(0.0, 0.0, 0.0, DistanceDF.Mode.XZ);
        double d1 = df.compute(ctx(3, 0, 4));
        double d2 = df.compute(ctx(3, 1000, 4));
        assertEquals(d1, d2, 1e-9);
    }

    @Test void distance_xyzMode_computesFullDistance() {
        DistanceDF df = new DistanceDF(0.0, 0.0, 0.0, DistanceDF.Mode.XYZ);
        // (1,2,2) → sqrt(1+4+4)=3
        assertEquals(3.0, df.compute(ctx(1, 2, 2)), 1e-9);
    }

    @Test void distance_xyzMode_nonZeroRef() {
        DistanceDF df = new DistanceDF(1.0, 0.0, 0.0, DistanceDF.Mode.XYZ);
        // (1,0,0) relative ref (1,0,0) → distance=0
        assertEquals(0.0, df.compute(ctx(1, 0, 0)), 1e-9);
    }

    @Test void distance_minValueIsZero() {
        DistanceDF df = new DistanceDF(0.0, 0.0, 0.0, DistanceDF.Mode.XZ);
        assertEquals(0.0, df.minValue(), 1e-9);
    }

    // ===== ScaleCoordDF =====

    @Test void scaleCoord_sx2_halvesX() {
        // CoordinateDF(X) at x=10 with sx=2 → floor(10/2)=5
        ScaleCoordDF df = new ScaleCoordDF(new CoordinateDF(CoordinateDF.Axis.X), 2.0, 1.0, 1.0);
        assertEquals(5.0, df.compute(ctx(10, 0, 0)), 1e-9);
    }

    @Test void scaleCoord_sy2_halvesY() {
        ScaleCoordDF df = new ScaleCoordDF(new CoordinateDF(CoordinateDF.Axis.Y), 1.0, 2.0, 1.0);
        assertEquals(5.0, df.compute(ctx(0, 10, 0)), 1e-9);
    }

    @Test void scaleCoord_negativeSx_mirrorsX() {
        // CoordinateDF(X) at x=6 with sx=-2 → floor(6/-2)=floor(-3)=-3
        ScaleCoordDF df = new ScaleCoordDF(new CoordinateDF(CoordinateDF.Axis.X), -2.0, 1.0, 1.0);
        assertEquals(-3.0, df.compute(ctx(6, 0, 0)), 1e-9);
    }

    // ===== AbsDF =====

    @Test void abs_positiveValue_unchanged() {
        AbsDF df = new AbsDF(c(3.0));
        assertEquals(3.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void abs_negativeValue_becomesPositive() {
        AbsDF df = new AbsDF(c(-3.0));
        assertEquals(3.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void abs_zero_isZero() {
        AbsDF df = new AbsDF(c(0.0));
        assertEquals(0.0, df.compute(ORIGIN), 1e-12);
    }

    @Test void abs_minValue_whenRangeSpansZero() {
        // inner = add(c(-2), c(3)) → minValue=-2+3=1, maxValue=-2+3=1. Not crossing zero.
        // Better: use two constants whose sum is a ConstantDF with known range.
        // For abs to see [negative, positive], use two constants: min by inner.minValue,
        // max by inner.maxValue.
        // Easiest: AbsDF wrapping a ConstantDF(-2) has inner.min=-2, inner.max=-2.
        // To get a range spanning zero we need an inner with min<0, max>0.
        // AddDF(c(-2), c(3)) → minValue = c(-2).minValue + c(3).minValue = -2+3=1, not spanning.
        // To create inner with static range [-2, 3], construct AddDF where a=c(-2) and b=CoordDF(Y):
        //   but CoordDF has huge range. Use: AddDF(c(-2), NegateDF(c(-3))) → add(-2,-(-3))=-2+3 not right.
        // Real spanning range: use ClampDF where f itself has a wide range.
        // Actually the cleanest is: AbsDF(NegateDF(c(-3))) → inner: NegateDF(c(-3)).minValue=3, maxValue=3
        // Conclusion: to truly test the spanning-zero branch, need AddDF(c(-2), c(2)).
        // AddDF(c(-2), c(2)): minValue=-2+2=0, maxValue=0 → no.
        // The real way: MinDF can't give different min/max from constants either.
        // *** Use AddDF of two constants that are different: the range IS the value since constant. ***
        // The only DF with differing min/max from pure construction is CoordinateDF or YEnvelopeDF.
        // YEnvelopeDF: minValue=0, maxValue=1.
        // AbsDF(YEnvelopeDF(10,20,0,false)): inner spans [0,1] → min=0 (spans zero? No, min=0 >= 0)
        // Actually 0 is the boundary. (minValue <= 0 && maxValue >= 0) → true (0<=0 && 1>=0).
        // So abs.minValue = 0, abs.maxValue = max(|0|,|1|) = 1.
        AbsDF df = new AbsDF(new YEnvelopeDF(10, 20, 0, false));
        assertEquals(0.0, df.minValue(), 1e-9);
        assertEquals(1.0, df.maxValue(), 1e-9);
    }

    @Test void abs_minValue_whenAllNegative() {
        // inner = NegateDF(YEnvelopeDF(10,20,0,false)): inner.minValue=-1, inner.maxValue=0
        // (minValue<=0 && maxValue>=0) → true → abs.minValue=0
        // Use a different approach: inner = NegateDF(new ConstantDF(5)) → min=-5, max=-5
        // abs: (min=-5 <=0 && max=-5 >= 0) → false → abs.minValue = min(|-5|,|-5|) = 5
        AbsDF df = new AbsDF(new NegateDF(new ConstantDF(5.0)));
        assertEquals(5.0, df.minValue(), 1e-9);
        assertEquals(5.0, df.maxValue(), 1e-9);
    }

    // ===== NegateDF =====

    @Test void negate_positiveValue_becomesNegative() {
        NegateDF df = new NegateDF(c(5.0));
        assertEquals(-5.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void negate_negativeValue_becomesPositive() {
        NegateDF df = new NegateDF(c(-5.0));
        assertEquals(5.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void negate_minMaxSwap() {
        // YEnvelopeDF(10,20,0,false): min=0, max=1
        // NegateDF: min=-max=-1, max=-min=0
        NegateDF df = new NegateDF(new YEnvelopeDF(10, 20, 0, false));
        assertEquals(-1.0, df.minValue(), 1e-9);
        assertEquals(0.0, df.maxValue(), 1e-9);
    }

    // ===== MinDF =====

    @Test void min_returnsLowerValue() {
        MinDF df = new MinDF(c(3.0), c(7.0));
        assertEquals(3.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void min_equalValues_returnsThat() {
        MinDF df = new MinDF(c(5.0), c(5.0));
        assertEquals(5.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void min_maxValue_isMinOfBothMaxValues() {
        // a = YEnvelopeDF(10,20,0,false): [0,1]
        // b = YEnvelopeDF(10,20,0,true) (inverted): [0,1]
        // MinDF.maxValue = min(a.maxValue, b.maxValue) = min(1,1) = 1
        // Use two constants instead:
        // a = ConstantDF(3), b = ConstantDF(7): MinDF.maxValue = min(3,7) = 3
        MinDF df = new MinDF(new ConstantDF(3.0), new ConstantDF(7.0));
        assertEquals(3.0, df.maxValue(), 1e-9);
    }

    // ===== MaxDF =====

    @Test void max_returnsHigherValue() {
        MaxDF df = new MaxDF(c(3.0), c(7.0));
        assertEquals(7.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void max_equalValues_returnsThat() {
        MaxDF df = new MaxDF(c(5.0), c(5.0));
        assertEquals(5.0, df.compute(ORIGIN), 1e-9);
    }

    @Test void max_minValue_isMaxOfBothMinValues() {
        // a = ConstantDF(3): min=max=3
        // b = ConstantDF(7): min=max=7
        // MaxDF.minValue = max(3, 7) = 7
        MaxDF df = new MaxDF(new ConstantDF(3.0), new ConstantDF(7.0));
        assertEquals(7.0, df.minValue(), 1e-9);
    }

    // ===== ConstantDF =====

    @Test void constant_alwaysReturnsSameValue() {
        ConstantDF df = new ConstantDF(42.0);
        assertEquals(42.0, df.compute(ORIGIN), 1e-9);
        assertEquals(42.0, df.compute(ctx(100, 200, 300)), 1e-9);
    }

    @Test void constant_minMaxEqualValue() {
        ConstantDF df = new ConstantDF(-7.5);
        assertEquals(-7.5, df.minValue(), 1e-9);
        assertEquals(-7.5, df.maxValue(), 1e-9);
    }

    // ===== CoordinateDF =====

    @Test void coordinate_xAxis_returnsBlockX() {
        CoordinateDF df = new CoordinateDF(CoordinateDF.Axis.X);
        assertEquals(42.0, df.compute(ctx(42, 0, 0)), 1e-9);
        assertEquals(-10.0, df.compute(ctx(-10, 0, 0)), 1e-9);
    }

    @Test void coordinate_yAxis_returnsBlockY() {
        CoordinateDF df = new CoordinateDF(CoordinateDF.Axis.Y);
        assertEquals(64.0, df.compute(ctx(0, 64, 0)), 1e-9);
    }

    @Test void coordinate_zAxis_returnsBlockZ() {
        CoordinateDF df = new CoordinateDF(CoordinateDF.Axis.Z);
        assertEquals(-99.0, df.compute(ctx(0, 0, -99)), 1e-9);
    }

    // ===== TranslateDF =====

    @Test void translate_shiftsXByDx() {
        // inner = CoordinateDF(X), dx=10 → floor(x - 10)
        TranslateDF df = new TranslateDF(new CoordinateDF(CoordinateDF.Axis.X), 10.0, 0.0, 0.0);
        // at x=15: floor(15-10)=5
        assertEquals(5.0, df.compute(ctx(15, 0, 0)), 1e-9);
    }

    @Test void translate_shiftsYByDy() {
        TranslateDF df = new TranslateDF(new CoordinateDF(CoordinateDF.Axis.Y), 0.0, 5.0, 0.0);
        assertEquals(10.0, df.compute(ctx(0, 15, 0)), 1e-9);
    }

    @Test void translate_shiftsZByDz() {
        TranslateDF df = new TranslateDF(new CoordinateDF(CoordinateDF.Axis.Z), 0.0, 0.0, 3.0);
        assertEquals(7.0, df.compute(ctx(0, 0, 10)), 1e-9);
    }

    // ===== RepeatDF =====

    @Test void repeat_xTilesPeriodically() {
        // CoordinateDF(X), periodX=10 → x mod 10
        RepeatDF df = new RepeatDF(new CoordinateDF(CoordinateDF.Axis.X), 10.0, 100.0);
        // x=13 → 13 mod 10 = 3
        assertEquals(3.0, df.compute(ctx(13, 0, 0)), 1e-9);
        // x=0 → 0
        assertEquals(0.0, df.compute(ctx(0, 0, 0)), 1e-9);
    }

    @Test void repeat_zTilesPeriodically() {
        RepeatDF df = new RepeatDF(new CoordinateDF(CoordinateDF.Axis.Z), 100.0, 10.0);
        assertEquals(5.0, df.compute(ctx(0, 0, 15)), 1e-9);
    }

    @Test void repeat_negativeX_wrapsCorrectly() {
        // x=-1, periodX=10 → ((-1%10)+10)%10 = 9
        RepeatDF df = new RepeatDF(new CoordinateDF(CoordinateDF.Axis.X), 10.0, 100.0);
        assertEquals(9.0, df.compute(ctx(-1, 0, 0)), 1e-9);
    }

    @Test void repeat_yPassesThrough() {
        // Y is not tiled, should pass through
        RepeatDF df = new RepeatDF(new CoordinateDF(CoordinateDF.Axis.Y), 10.0, 10.0);
        assertEquals(64.0, df.compute(ctx(0, 64, 0)), 1e-9);
    }

    // ===== SlopedDensityDF =====

    @Test void slopedDensity_build_depthPositive_dominatesAtHighFactor() {
        // With depth=1.0, factor=4.0, baseNoise=0 → shaped=4*quarterNeg(1*4)=4*4=16
        DensityFunction result = SlopedDensityDF.build(c(1.0), 4.0, c(0.0));
        // quarterNeg(4) = 4 (positive), shaped = 4*4 = 16, add 0 = 16
        assertEquals(16.0, result.compute(ORIGIN), 1e-9);
    }

    @Test void slopedDensity_build_depthNegative_quartersAndAmplifies() {
        // depth=-1.0, factor=4.0: mul(-1,4)=-4, quarterNeg(-4)=-1, mul(4,-1)=-4, add(0)=-4
        DensityFunction result = SlopedDensityDF.build(c(-1.0), 4.0, c(0.0));
        assertEquals(-4.0, result.compute(ORIGIN), 1e-9);
    }

    @Test void slopedDensity_fromConfig_matchesBuildResult() {
        SlopedDensityDF df = SlopedDensityDF.fromConfig(c(0.5), 4.0, c(0.0));
        DensityFunction expected = SlopedDensityDF.build(c(0.5), 4.0, c(0.0));
        assertEquals(expected.compute(ORIGIN), df.compute(ORIGIN), 1e-9);
    }
}
