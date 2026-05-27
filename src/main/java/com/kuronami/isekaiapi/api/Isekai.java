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
 * <p>v0.1: API surface frozen; density function primitives are registered and usable.
 * Query and Remap return immutable no-op stubs — the vanilla rule scanner and biome
 * modifier generator land in v0.2.
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
