package com.kuronami.isekaiapi.api.query;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record VerticalRange(int minY, int maxY, HeightDistribution distribution) {
    public VerticalRange {
        if (minY > maxY) {
            throw new IllegalArgumentException("minY (" + minY + ") > maxY (" + maxY + ")");
        }
    }

    public int span() {
        return maxY - minY;
    }

    public static final Codec<VerticalRange> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("min_y").forGetter(VerticalRange::minY),
            Codec.INT.fieldOf("max_y").forGetter(VerticalRange::maxY),
            HeightDistribution.CODEC.fieldOf("distribution").forGetter(VerticalRange::distribution)
    ).apply(i, VerticalRange::new));
}
