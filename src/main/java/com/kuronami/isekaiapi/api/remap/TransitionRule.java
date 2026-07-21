package com.kuronami.isekaiapi.api.remap;

import com.kuronami.isekaiapi.registry.IsekaiDispatch;
import com.kuronami.isekaiapi.registry.IsekaiSpiTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How adjacent layers in a {@link LayeredDescriptor} relate at their boundary.
 * v1.0 default: {@link Hard}. {@link Blend} smooths the seam. {@link Gap} inserts
 * empty space between layers.
 *
 * <p><b>Extensible.</b> Built-in variants are registered in
 * {@link com.kuronami.isekaiapi.api.registry.IsekaiRegistries#TRANSITION_RULE_TYPE}; third parties
 * add their own by registering a {@link MapCodec} under that key. The {@link #CODEC} dispatches on
 * a {@code "type"} field, e.g. {@code {"type": "isekai_api:blend", "blend_height": 4}}. The legacy
 * {@code isekai:} prefix is accepted as a deprecated alias.
 *
 * @since 1.0.0
 */
public interface TransitionRule {

    /** This variant's payload codec (no {@code "type"} field); must be the registered instance. @since 1.0.0 */
    MapCodec<? extends TransitionRule> codec();

    /** Dispatching codec keyed on a {@code "type"} field, backed by the TransitionRule registry. */
    Codec<TransitionRule> CODEC = IsekaiDispatch.dispatchCodec(
            IsekaiSpiTypes.TRANSITION_RULE_REGISTRY, TransitionRule::codec, "TransitionRule");

    /** Adjacent layers butt-join at the boundary Y. @since 1.0.0 */
    record Hard() implements TransitionRule {
        public static final Hard INSTANCE = new Hard();
        public static final MapCodec<Hard> MAP_CODEC = MapCodec.unit(INSTANCE);

        @Override public MapCodec<? extends TransitionRule> codec() { return MAP_CODEC; }
    }

    /** Smoothly blend layers over {@code blendHeight} blocks. @since 1.0.0 */
    record Blend(int blendHeight) implements TransitionRule {
        public Blend {
            if (blendHeight < 0) throw new IllegalArgumentException("blendHeight < 0");
        }
        public static final MapCodec<Blend> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("blend_height").forGetter(Blend::blendHeight)
        ).apply(i, Blend::new));

        @Override public MapCodec<? extends TransitionRule> codec() { return MAP_CODEC; }
    }

    /** Insert {@code gapHeight} blocks of empty space between layers. @since 1.0.0 */
    record Gap(int gapHeight) implements TransitionRule {
        public Gap {
            if (gapHeight < 0) throw new IllegalArgumentException("gapHeight < 0");
        }
        public static final MapCodec<Gap> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("gap_height").forGetter(Gap::gapHeight)
        ).apply(i, Gap::new));

        @Override public MapCodec<? extends TransitionRule> codec() { return MAP_CODEC; }
    }
}
