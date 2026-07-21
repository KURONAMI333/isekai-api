package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.api.query.HeightDistribution;
import com.kuronami.isekaiapi.api.query.VerticalRange;
import com.kuronami.isekaiapi.registry.IsekaiDispatch;
import com.kuronami.isekaiapi.registry.IsekaiSpiTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Maps a vanilla {@link VerticalRange} into the consumer's playable range, and/or scales the
 * count/density of generated features. Composable via {@link Pipe}.
 *
 * <p><b>Extensible.</b> Built-in variants are registered in
 * {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries#REMAP_STRATEGY_TYPE}; third parties
 * add their own by registering a {@link MapCodec} under that key. The {@link #CODEC} dispatches on
 * a {@code "type"} field, e.g. {@code {"type": "isekai_api:linear"}} /
 * {@code {"type": "isekai_api:count_scale", "factor": 0.5}}. The legacy {@code isekai:} prefix is
 * accepted as a deprecated alias.
 *
 * <p><b>Implementing a variant.</b> Implementations must be immutable, return their registered
 * codec from {@link #codec()}, project a range via {@link #remap(VerticalRange, RemapContext)},
 * report any density multiplier via {@link #countFactor()} (default 1.0), and expose nested
 * strategies via {@link #children()} (default empty).
 *
 * @since 1.0.0
 */
public interface RemapStrategy {

    /** This variant's payload codec (no {@code "type"} field); must be the registered instance. @since 1.0.0 */
    MapCodec<? extends RemapStrategy> codec();

    /**
     * Project {@code original} into the context's playable envelope. Strategies whose semantics
     * don't affect Y placement (e.g. {@code CountScale}) return {@code original} unchanged. @since 2.0.0
     */
    VerticalRange remap(VerticalRange original, RemapContext ctx);

    /** Feature/mob density multiplier contributed by this strategy (1.0 = unchanged). @since 2.0.0 */
    default double countFactor() { return 1.0; }

    /** Nested strategies, for tree-walking. Empty for non-composite variants. @since 2.0.0 */
    default List<RemapStrategy> children() { return List.of(); }

    /** Dispatching codec keyed on a {@code "type"} field, backed by the RemapStrategy registry. */
    Codec<RemapStrategy> CODEC = IsekaiDispatch.dispatchCodec(
            IsekaiSpiTypes.REMAP_STRATEGY_REGISTRY, RemapStrategy::codec, "RemapStrategy");

    /** Proportional linear scale from vanilla [a,b] into target [a',b']. @since 1.0.0 */
    record Linear() implements RemapStrategy {
        public static final Linear INSTANCE = new Linear();
        public static final MapCodec<Linear> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
        @Override public VerticalRange remap(VerticalRange original, RemapContext ctx) {
            return ctx.linearScale(original);
        }
    }

    /**
     * Partition the target range into bands. Each {@link Band} declares the vanilla Y
     * subrange it covers plus the fraction of the playable range it should occupy. The
     * bands' {@code targetRatio}s should sum to 1.0 (validated by IsekaiValidator's
     * cross-field checks); their {@code vanillaSource} ranges should be ordered
     * low-to-high and non-overlapping (validator enforces).
     *
     * <p>Dispatches per feature: find the band whose {@code vanillaSource} contains the feature's
     * midpoint, then map proportionally into the playable range slice corresponding to that band's
     * cumulative ratio offset.
     *
     * <p>JSON:
     * <pre>{@code
     * {
     *   "type": "isekai_api:band_split",
     *   "bands": [
     *     { "vanilla_source": { "min_y": -64, "max_y": 0,   "distribution": "uniform" }, "target_ratio": 0.5 },
     *     { "vanilla_source": { "min_y": 0,   "max_y": 320, "distribution": "uniform" }, "target_ratio": 0.5 }
     *   ]
     * }
     * }</pre>
     * @since 1.0.0
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

        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }

        /**
         * Dispatch {@code original} to the matching band, then project it proportionally into that
         * band's slice of the playable range. The matching band is the one whose
         * {@code vanillaSource} contains the feature's midpoint; ties resolve to the lower-Y band.
         * If no band matches, fall back to {@link RemapContext#linearScale}.
         */
        @Override public VerticalRange remap(VerticalRange original, RemapContext ctx) {
            VerticalRange playable = ctx.playable();
            double midpoint = (original.minY() + original.maxY()) / 2.0;
            int matchIndex = -1;
            for (int i = 0; i < bands.size(); i++) {
                var band = bands.get(i);
                if (midpoint >= band.vanillaSource().minY() && midpoint <= band.vanillaSource().maxY()) {
                    matchIndex = i;
                    break;
                }
            }
            if (matchIndex < 0) {
                // Feature lies outside every declared band; degrade gracefully to full-range linear.
                int playSpan = playable.maxY() - playable.minY();
                return new VerticalRange(playable.minY(), playable.minY() + playSpan, original.distribution());
            }
            // Compute this band's slice [sliceMin, sliceMax] in the playable range.
            float cumulativeBefore = 0f;
            for (int i = 0; i < matchIndex; i++) {
                cumulativeBefore += bands.get(i).targetRatio();
            }
            var matched = bands.get(matchIndex);
            int playSpan = playable.maxY() - playable.minY();
            int sliceMin = playable.minY() + Math.round(cumulativeBefore * playSpan);
            int sliceMax = playable.minY() + Math.round((cumulativeBefore + matched.targetRatio()) * playSpan);
            // Now scale the feature's range proportionally within this slice.
            var source = matched.vanillaSource();
            int sourceSpan = source.maxY() - source.minY();
            if (sourceSpan <= 0) {
                return new VerticalRange(sliceMin, sliceMax, original.distribution());
            }
            double tMin = (original.minY() - source.minY()) / (double) sourceSpan;
            double tMax = (original.maxY() - source.minY()) / (double) sourceSpan;
            int sliceSpan = sliceMax - sliceMin;
            int newMin = sliceMin + (int) Math.round(tMin * sliceSpan);
            int newMax = sliceMin + (int) Math.round(tMax * sliceSpan);
            newMin = Math.max(sliceMin, Math.min(newMin, sliceMax));
            newMax = Math.max(newMin, Math.min(newMax, sliceMax));
            return new VerticalRange(newMin, newMax, original.distribution());
        }

        /** One band in a {@link BandSplit}: a vanilla source range + its target ratio. @since 1.0.0 */
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

    /** Hard-coded target range, ignoring vanilla. @since 1.0.0 */
    record FixedRange(int min, int max, HeightDistribution dist) implements RemapStrategy {
        public FixedRange {
            if (min > max) throw new IllegalArgumentException("min > max");
        }
        public static final MapCodec<FixedRange> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(FixedRange::min),
                Codec.INT.fieldOf("max").forGetter(FixedRange::max),
                HeightDistribution.CODEC.fieldOf("dist").forGetter(FixedRange::dist)
        ).apply(i, FixedRange::new));

        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
        @Override public VerticalRange remap(VerticalRange original, RemapContext ctx) {
            return new VerticalRange(min, max, dist);
        }
    }

    /** Axis flip: vanilla low maps to target high and vice versa. @since 1.0.0 */
    record Inverted() implements RemapStrategy {
        public static final Inverted INSTANCE = new Inverted();
        public static final MapCodec<Inverted> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
        @Override public VerticalRange remap(VerticalRange original, RemapContext ctx) {
            VerticalRange linear = ctx.linearScale(original);
            VerticalRange playable = ctx.playable();
            int newMin = playable.minY() + (playable.maxY() - linear.maxY());
            int newMax = playable.minY() + (playable.maxY() - linear.minY());
            return new VerticalRange(newMin, newMax, linear.distribution());
        }
    }

    /** Scale the count/density of generated features by {@code factor} (1.0 = unchanged). @since 1.0.0 */
    record CountScale(double factor) implements RemapStrategy {
        public CountScale {
            if (factor < 0) throw new IllegalArgumentException("factor < 0");
        }
        public static final MapCodec<CountScale> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.doubleRange(0.0, Double.MAX_VALUE).fieldOf("factor").forGetter(CountScale::factor)
        ).apply(i, CountScale::new));

        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
        // Count strategy affects feature density (via countFactor), not Y placement.
        @Override public VerticalRange remap(VerticalRange original, RemapContext ctx) { return original; }
        @Override public double countFactor() { return factor; }
    }

    /** Identity mapping: pass through vanilla unchanged. @since 1.0.0 */
    record Identity() implements RemapStrategy {
        public static final Identity INSTANCE = new Identity();
        public static final MapCodec<Identity> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
        @Override public VerticalRange remap(VerticalRange original, RemapContext ctx) { return original; }
    }

    /** Apply chain in order, each operating on the previous result. Must be non-empty. @since 1.0.0 */
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

        @Override public MapCodec<? extends RemapStrategy> codec() { return MAP_CODEC; }
        @Override public VerticalRange remap(VerticalRange original, RemapContext ctx) {
            VerticalRange acc = original;
            for (RemapStrategy child : chain) {
                acc = child.remap(acc, ctx);
            }
            return acc;
        }
        @Override public double countFactor() {
            double product = 1.0;
            for (RemapStrategy child : chain) product *= child.countFactor();
            return product;
        }
        @Override public List<RemapStrategy> children() { return chain; }
    }
}
