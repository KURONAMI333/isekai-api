package com.kuronami.isekaiapi.gametest.ext;

import com.kuronami.isekaiapi.api.predicate.EvaluationContext;
import com.kuronami.isekaiapi.api.predicate.SpatialPredicate;
import com.mojang.serialization.MapCodec;

/**
 * A third-party {@link SpatialPredicate} variant that lives ONLY in the (jar-excluded) gametest
 * tree. Registered from {@link IsekaiTestExtensions} under {@code isekai_api_test:x_parity}, it
 * proves a non-Isekai mod can add a predicate that then evaluates through the same
 * {@link EvaluationContext} seam as the built-ins. Matches positions with an even X coordinate.
 */
public record XParityPredicate() implements SpatialPredicate {

    public static final XParityPredicate INSTANCE = new XParityPredicate();
    public static final MapCodec<XParityPredicate> MAP_CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(EvaluationContext ctx) {
        return (ctx.pos().getX() & 1) == 0;
    }

    @Override
    public MapCodec<? extends SpatialPredicate> codec() {
        return MAP_CODEC;
    }
}
