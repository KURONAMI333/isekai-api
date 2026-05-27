package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.VerticalRange;

/**
 * One layer in a multi-layer worldshape. Layers stack along Y; the consumer-supplied
 * {@link WorldshapeDescriptor} controls remap behavior within {@code yRange}.
 */
public record LayeredDescriptor(
        VerticalRange yRange,
        WorldshapeDescriptor descriptor,
        TransitionRule transition
) {}
