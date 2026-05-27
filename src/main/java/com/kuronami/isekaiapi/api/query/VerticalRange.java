package com.kuronami.isekaiapi.api.query;

public record VerticalRange(int minY, int maxY, HeightDistribution distribution) {
    public VerticalRange {
        if (minY > maxY) {
            throw new IllegalArgumentException("minY (" + minY + ") > maxY (" + maxY + ")");
        }
    }

    public int span() {
        return maxY - minY;
    }
}
