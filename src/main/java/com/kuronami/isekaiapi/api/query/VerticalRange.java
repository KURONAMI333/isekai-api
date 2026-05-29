package com.kuronami.isekaiapi.api.query;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * An inclusive Y band with a vertical {@link HeightDistribution}. The core value type of
 * the remap layer: {@code IsekaiQuery} reports a vanilla feature's range as a
 * {@code VerticalRange}, and {@code RemapStrategy} maps it into the worldshape's playable
 * range. JSON shape: {@code {"min_y": <int>, "max_y": <int>, "distribution":
 * "<HeightDistribution>"}}. The canonical constructor rejects {@code minY > maxY}.
 */
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
