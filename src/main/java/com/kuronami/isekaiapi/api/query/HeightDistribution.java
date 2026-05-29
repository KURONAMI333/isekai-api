package com.kuronami.isekaiapi.api.query;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * How samples are distributed within a {@link VerticalRange}. Mirrors the vanilla
 * {@code HeightProvider} families: {@code uniform} (flat), {@code trapezoid} / {@code
 * triangle} (center-weighted), {@code biased_low} / {@code biased_high} (skewed toward one
 * end). Serialized via its lowercase name in JSON (e.g. {@code "distribution": "uniform"}).
 */
public enum HeightDistribution implements StringRepresentable {
    UNIFORM("uniform"),
    TRAPEZOID("trapezoid"),
    TRIANGLE("triangle"),
    BIASED_LOW("biased_low"),
    BIASED_HIGH("biased_high");

    public static final Codec<HeightDistribution> CODEC = StringRepresentable.fromEnum(HeightDistribution::values);

    private final String serializedName;

    HeightDistribution(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
