package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.VerticalRange;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Maps a vanilla {@link VerticalRange} into the consumer's playable range.
 * Composable via {@link Pipe}. Sealed; new neutral variants land in v1.x.
 */
public sealed interface RemapStrategy {
    /** Proportional linear scale from vanilla [a,b] into target [a',b']. */
    record Linear() implements RemapStrategy {}

    /**
     * Partition the target range into bands with the given proportional weights.
     * The {@code ratios} sum should be 1.0 (validated). Order corresponds to vanilla
     * low -> high; each weight defines the fraction of the target range that band gets.
     * Band names are not part of the API — callers map ratios to vanilla bands externally.
     */
    record BandSplit(List<Float> ratios) implements RemapStrategy {
        public BandSplit { ratios = List.copyOf(ratios); }
    }

    /** Hard-coded target range, ignoring vanilla. */
    record FixedRange(int min, int max, com.kuronami.isekaiapi.api.query.HeightDistribution dist) implements RemapStrategy {
        public FixedRange {
            if (min > max) throw new IllegalArgumentException("min > max");
        }
    }

    /** Axis flip: vanilla low maps to target high and vice versa. */
    record Inverted() implements RemapStrategy {}

    /** Scale the count/density of generated features by {@code factor} (1.0 = unchanged). */
    record CountScale(double factor) implements RemapStrategy {
        public CountScale {
            if (factor < 0) throw new IllegalArgumentException("factor < 0");
        }
    }

    /** Identity mapping: pass through vanilla unchanged. */
    record Identity() implements RemapStrategy {}

    /**
     * Caller-supplied non-linear mapping: input {@code t in [0,1]} -> output {@code t' in [0,1]}.
     * Datapack consumers cannot use this variant; use {@link BandSplit} or {@link Pipe} instead.
     */
    record NonLinear(Function<Float, Float> mapping) implements RemapStrategy {}

    /** Fully custom: (vanillaRange, playableRange) -> remapped range. Java-only. */
    record Custom(BiFunction<VerticalRange, VerticalRange, VerticalRange> fn) implements RemapStrategy {}

    /** Apply chain in order, each operating on the previous result. Must be non-empty. */
    record Pipe(List<RemapStrategy> chain) implements RemapStrategy {
        public Pipe {
            chain = List.copyOf(chain);
            if (chain.isEmpty()) {
                throw new IllegalArgumentException("Pipe requires at least one strategy");
            }
        }
    }
}
