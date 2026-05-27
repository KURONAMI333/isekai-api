package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One layer in a multi-layer worldshape. Layers stack along Y; the consumer-supplied
 * {@link WorldshapeDescriptor} controls remap behavior within {@code yRange}.
 */
public record LayeredDescriptor(
        VerticalRange yRange,
        WorldshapeDescriptor descriptor,
        TransitionRule transition
) {
    public static final Codec<LayeredDescriptor> CODEC = RecordCodecBuilder.create(i -> i.group(
            VerticalRange.CODEC.fieldOf("y_range").forGetter(LayeredDescriptor::yRange),
            WorldshapeDescriptor.CODEC.fieldOf("descriptor").forGetter(LayeredDescriptor::descriptor),
            TransitionRule.CODEC.fieldOf("transition").forGetter(LayeredDescriptor::transition)
    ).apply(i, LayeredDescriptor::new));
}
