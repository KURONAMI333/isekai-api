package com.kuronami.isekaiapi.api;

import com.kuronami.isekaiapi.api.query.IsekaiQuery;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.impl.IsekaiQueryImpl;
import com.kuronami.isekaiapi.impl.IsekaiRemapImpl;
import com.kuronami.isekaiapi.impl.VanillaRuleSnapshot;

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

    /**
     * Internal: invoked by the lifecycle hook to publish the freshly-scanned vanilla
     * rule snapshot to the query cache. Not part of the public consumer API; consumers
     * read via {@link #query()}, not via this method.
     */
    public static void publishSnapshot(VanillaRuleSnapshot snapshot) {
        ((IsekaiQueryImpl) QUERY).setSnapshot(snapshot);
    }
}
