package com.kuronami.isekaiapi.api.remap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How adjacent layers in a {@link LayeredDescriptor} relate at their boundary.
 * v1.0 default: {@link Hard}. {@link Blend} smooths the seam. {@link Gap} inserts
 * empty space between layers.
 *
 * <p>JSON form: {@code {"type": "isekai:hard"}} / {@code {"type": "isekai:blend", "blend_height": 4}}.
 */
public sealed interface TransitionRule {

    String typeId();
    MapCodec<? extends TransitionRule> codec();

    Codec<TransitionRule> CODEC = Codec.lazyInitialized(TransitionRule::buildDispatchCodec);

    /** Adjacent layers butt-join at the boundary Y. */
    record Hard() implements TransitionRule {
        public static final Hard INSTANCE = new Hard();
        public static final MapCodec<Hard> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public String typeId() { return "isekai:hard"; }
        @Override public MapCodec<? extends TransitionRule> codec() { return MAP_CODEC; }
    }

    /** Smoothly blend layers over {@code blendHeight} blocks. */
    record Blend(int blendHeight) implements TransitionRule {
        public Blend {
            if (blendHeight < 0) throw new IllegalArgumentException("blendHeight < 0");
        }
        public static final MapCodec<Blend> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("blend_height").forGetter(Blend::blendHeight)
        ).apply(i, Blend::new));

        @Override public String typeId() { return "isekai:blend"; }
        @Override public MapCodec<? extends TransitionRule> codec() { return MAP_CODEC; }
    }

    /** Insert {@code gapHeight} blocks of empty space between layers. */
    record Gap(int gapHeight) implements TransitionRule {
        public Gap {
            if (gapHeight < 0) throw new IllegalArgumentException("gapHeight < 0");
        }
        public static final MapCodec<Gap> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("gap_height").forGetter(Gap::gapHeight)
        ).apply(i, Gap::new));

        @Override public String typeId() { return "isekai:gap"; }
        @Override public MapCodec<? extends TransitionRule> codec() { return MAP_CODEC; }
    }

    private static Codec<TransitionRule> buildDispatchCodec() {
        Map<String, MapCodec<? extends TransitionRule>> registry = new LinkedHashMap<>();
        registry.put("isekai:hard",  Hard.MAP_CODEC);
        registry.put("isekai:blend", Blend.MAP_CODEC);
        registry.put("isekai:gap",   Gap.MAP_CODEC);
        Map<String, MapCodec<? extends TransitionRule>> frozen = Map.copyOf(registry);

        return Codec.STRING.dispatch(
                "type",
                TransitionRule::typeId,
                typeId -> {
                    MapCodec<? extends TransitionRule> mc = frozen.get(typeId);
                    if (mc == null) {
                        throw new IllegalArgumentException(
                                "Unknown TransitionRule type: '" + typeId
                                        + "'. Known types: " + frozen.keySet());
                    }
                    return mc;
                });
    }
}
