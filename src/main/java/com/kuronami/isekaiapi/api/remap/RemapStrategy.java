package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Maps a vanilla {@link VerticalRange} into the consumer's playable range.
 * Composable via {@link Pipe}. Sealed; new neutral variants land in v1.x.
 *
 * <p>JSON form: {@code {"type": "isekai:linear"}} / {@code {"type": "isekai:count_scale", "factor": 0.5}}.
 *
 * <p>{@link NonLinear} and {@link Custom} are Java-only — they carry function references
 * that cannot be encoded to JSON. Datapack consumers must use {@link BandSplit} or
 * {@link Pipe} for non-linear behavior.
 */
public sealed interface RemapStrategy {

    String typeId();
    MapCodec<? extends RemapStrategy> codec();

    Codec<RemapStrategy> CODEC = Codec.lazyInitialized(RemapStrategy::buildDispatchCodec);

    /** Proportional linear scale from vanilla [a,b] into target [a',b']. */
    record Linear() implements RemapStrategy {
        public static final Linear INSTANCE = new Linear();
        public static final MapCodec<Linear> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public String typeId() { return "isekai:linear"; }
        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
    }

    /**
     * Partition the target range into bands. Each {@link Band} declares the vanilla Y
     * subrange it covers plus the fraction of the playable range it should occupy. The
     * bands' {@code targetRatio}s should sum to 1.0 (validated by IsekaiValidator's
     * cross-field checks); their {@code vanillaSource} ranges should be ordered
     * low-to-high and non-overlapping (validator enforces).
     *
     * <p>RemapEngine dispatches: for a feature with original Y range, find the band whose
     * {@code vanillaSource} contains the feature's midpoint, then map proportionally into
     * the playable range slice corresponding to that band's cumulative ratio offset.
     *
     * <p>JSON:
     * <pre>{@code
     * {
     *   "type": "isekai:band_split",
     *   "bands": [
     *     { "vanilla_source": { "min_y": -64, "max_y": 0,   "distribution": "uniform" }, "target_ratio": 0.5 },
     *     { "vanilla_source": { "min_y": 0,   "max_y": 320, "distribution": "uniform" }, "target_ratio": 0.5 }
     *   ]
     * }
     * }</pre>
     */
    record BandSplit(List<Band> bands) implements RemapStrategy {
        public BandSplit {
            bands = List.copyOf(bands);
            if (bands.isEmpty()) {
                throw new IllegalArgumentException("BandSplit requires at least one band");
            }
        }
        public static final MapCodec<BandSplit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Band.CODEC.listOf().fieldOf("bands").forGetter(BandSplit::bands)
        ).apply(i, BandSplit::new));

        @Override public String typeId() { return "isekai:band_split"; }
        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }

        /** One band in a {@link BandSplit}: a vanilla source range + its target ratio. */
        public record Band(com.kuronami.isekaiapi.api.query.VerticalRange vanillaSource, float targetRatio) {
            public Band {
                if (targetRatio < 0) {
                    throw new IllegalArgumentException("Band.targetRatio < 0: " + targetRatio);
                }
            }
            public static final com.mojang.serialization.Codec<Band> CODEC = RecordCodecBuilder.create(i -> i.group(
                    com.kuronami.isekaiapi.api.query.VerticalRange.CODEC.fieldOf("vanilla_source").forGetter(Band::vanillaSource),
                    Codec.FLOAT.fieldOf("target_ratio").forGetter(Band::targetRatio)
            ).apply(i, Band::new));
        }
    }

    /** Hard-coded target range, ignoring vanilla. */
    record FixedRange(int min, int max, HeightDistribution dist) implements RemapStrategy {
        public FixedRange {
            if (min > max) throw new IllegalArgumentException("min > max");
        }
        public static final MapCodec<FixedRange> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(FixedRange::min),
                Codec.INT.fieldOf("max").forGetter(FixedRange::max),
                HeightDistribution.CODEC.fieldOf("dist").forGetter(FixedRange::dist)
        ).apply(i, FixedRange::new));

        @Override public String typeId() { return "isekai:fixed_range"; }
        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
    }

    /** Axis flip: vanilla low maps to target high and vice versa. */
    record Inverted() implements RemapStrategy {
        public static final Inverted INSTANCE = new Inverted();
        public static final MapCodec<Inverted> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public String typeId() { return "isekai:inverted"; }
        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
    }

    /** Scale the count/density of generated features by {@code factor} (1.0 = unchanged). */
    record CountScale(double factor) implements RemapStrategy {
        public CountScale {
            if (factor < 0) throw new IllegalArgumentException("factor < 0");
        }
        public static final MapCodec<CountScale> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("factor").forGetter(CountScale::factor)
        ).apply(i, CountScale::new));

        @Override public String typeId() { return "isekai:count_scale"; }
        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
    }

    /** Identity mapping: pass through vanilla unchanged. */
    record Identity() implements RemapStrategy {
        public static final Identity INSTANCE = new Identity();
        public static final MapCodec<Identity> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public String typeId() { return "isekai:identity"; }
        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
    }

    /**
     * Caller-supplied non-linear mapping: input {@code t in [0,1]} -> output {@code t' in [0,1]}.
     * Datapack consumers cannot use this variant; use {@link BandSplit} or {@link Pipe} instead.
     */
    record NonLinear(Function<Float, Float> mapping) implements RemapStrategy {
        @Override public String typeId() { return "isekai:non_linear"; }
        @Override public MapCodec<? extends RemapStrategy> codec() {
            throw new UnsupportedOperationException(
                    "RemapStrategy.NonLinear is Java-only and cannot be serialized to JSON");
        }
    }

    /** Fully custom: (vanillaRange, playableRange) -> remapped range. Java-only. */
    record Custom(BiFunction<VerticalRange, VerticalRange, VerticalRange> fn) implements RemapStrategy {
        @Override public String typeId() { return "isekai:custom"; }
        @Override public MapCodec<? extends RemapStrategy> codec() {
            throw new UnsupportedOperationException(
                    "RemapStrategy.Custom is Java-only and cannot be serialized to JSON");
        }
    }

    /** Apply chain in order, each operating on the previous result. Must be non-empty. */
    record Pipe(List<RemapStrategy> chain) implements RemapStrategy {
        public Pipe {
            chain = List.copyOf(chain);
            if (chain.isEmpty()) {
                throw new IllegalArgumentException("Pipe requires at least one strategy");
            }
        }
        public static final MapCodec<Pipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.lazyInitialized(() -> CODEC).listOf().fieldOf("chain").forGetter(Pipe::chain)
        ).apply(i, Pipe::new));

        @Override public String typeId() { return "isekai:pipe"; }
        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
    }

    private static Codec<RemapStrategy> buildDispatchCodec() {
        Map<String, MapCodec<? extends RemapStrategy>> registry = new LinkedHashMap<>();
        registry.put("isekai:linear",      Linear.MAP_CODEC);
        registry.put("isekai:band_split",  BandSplit.MAP_CODEC);
        registry.put("isekai:fixed_range", FixedRange.MAP_CODEC);
        registry.put("isekai:inverted",    Inverted.MAP_CODEC);
        registry.put("isekai:count_scale", CountScale.MAP_CODEC);
        registry.put("isekai:identity",    Identity.MAP_CODEC);
        registry.put("isekai:pipe",        Pipe.MAP_CODEC);
        // "isekai:non_linear" and "isekai:custom" intentionally omitted — Java-only.
        Map<String, MapCodec<? extends RemapStrategy>> frozen = Map.copyOf(registry);

        return Codec.STRING.dispatch(
                "type",
                RemapStrategy::typeId,
                typeId -> {
                    MapCodec<? extends RemapStrategy> mc = frozen.get(typeId);
                    if (mc == null) {
                        throw new IllegalArgumentException(
                                "Unknown RemapStrategy type: '" + typeId
                                        + "'. Known types: " + frozen.keySet()
                                        + " (note: 'isekai:non_linear' and 'isekai:custom' are Java-only)");
                    }
                    return mc;
                });
    }
}
