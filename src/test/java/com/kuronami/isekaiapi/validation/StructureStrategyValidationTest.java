package com.kuronami.isekaiapi.validation;

import com.kuronami.isekaiapi.api.remap.RemapStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which {@code structure_strategy} variants a worldshape may declare. The rule is "accept
 * exactly what reaches chunk generation": {@code count_scale} thins structures, so it is
 * accepted up to a factor of 1.0; the Y-band remaps say nothing about spawn frequency, so
 * they are rejected rather than accepted as silent no-ops.
 */
class StructureStrategyValidationTest {

    @Test
    void identityIsAccepted() {
        assertDoesNotThrow(() -> IsekaiValidator.verifyStructureStrategyMeaningful(
                new RemapStrategy.Identity()));
    }

    @Test
    void countScaleThinningIsAccepted() {
        for (double factor : new double[] {0.0, 0.25, 0.5, 1.0}) {
            assertDoesNotThrow(() -> IsekaiValidator.verifyStructureStrategyMeaningful(
                    new RemapStrategy.CountScale(factor)), "factor " + factor);
        }
    }

    @Test
    void countScaleAboveOneIsRejectedRatherThanClamped() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> IsekaiValidator.verifyStructureStrategyMeaningful(
                        new RemapStrategy.CountScale(2.0)));
        assertTrue(error.getMessage().contains("thinned"), error.getMessage());
    }

    @Test
    void yRemapVariantsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> IsekaiValidator
                .verifyStructureStrategyMeaningful(new RemapStrategy.Linear()));
        assertThrows(IllegalArgumentException.class, () -> IsekaiValidator
                .verifyStructureStrategyMeaningful(new RemapStrategy.Inverted()));
    }

    @Test
    void pipeIsCheckedThroughToItsLeaves() {
        assertDoesNotThrow(() -> IsekaiValidator.verifyStructureStrategyMeaningful(
                new RemapStrategy.Pipe(List.of(
                        new RemapStrategy.Identity(), new RemapStrategy.CountScale(0.3)))));
        assertThrows(IllegalArgumentException.class, () -> IsekaiValidator
                .verifyStructureStrategyMeaningful(new RemapStrategy.Pipe(List.of(
                        new RemapStrategy.Identity(), new RemapStrategy.Linear()))));
    }
}
