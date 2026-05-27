package com.kuronami.isekaiapi.api;

import com.kuronami.isekaiapi.api.query.IsekaiQuery;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.impl.IsekaiQueryImpl;
import com.kuronami.isekaiapi.impl.IsekaiRemapImpl;

/**
 * Public facade for consumers. Access points:
 * <ul>
 *   <li>{@link #query()} — read vanilla + modded worldgen rules</li>
 *   <li>{@link #remap()} — declare your worldshape transformation</li>
 * </ul>
 *
 * <p>v0.1 status: API surface frozen; implementations are no-op stubs that log and return
 * empty results. Functional implementations land with density function primitives in v0.2.
 */
public final class Isekai {
    private static final IsekaiQuery QUERY = new IsekaiQueryImpl();
    private static final IsekaiRemap REMAP = new IsekaiRemapImpl();

    private Isekai() {}

    public static IsekaiQuery query() {
        return QUERY;
    }

    public static IsekaiRemap remap() {
        return REMAP;
    }
}
