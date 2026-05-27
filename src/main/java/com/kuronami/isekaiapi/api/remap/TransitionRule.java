package com.kuronami.isekaiapi.api.remap;

/**
 * How adjacent layers in a {@link LayeredDescriptor} relate at their boundary.
 * v1.0 default: {@link Hard}. {@link Blend} smooths the seam. {@link Gap} inserts
 * empty space between layers.
 */
public sealed interface TransitionRule {
    /** Adjacent layers butt-join at the boundary Y. */
    record Hard() implements TransitionRule {}

    /** Smoothly blend layers over {@code blendHeight} blocks. */
    record Blend(int blendHeight) implements TransitionRule {
        public Blend {
            if (blendHeight < 0) throw new IllegalArgumentException("blendHeight < 0");
        }
    }

    /** Insert {@code gapHeight} blocks of empty space between layers. */
    record Gap(int gapHeight) implements TransitionRule {
        public Gap {
            if (gapHeight < 0) throw new IllegalArgumentException("gapHeight < 0");
        }
    }
}
