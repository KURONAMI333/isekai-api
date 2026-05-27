package com.kuronami.isekaiapi.api;

import com.kuronami.isekaiapi.api.query.IsekaiQuery;
import com.kuronami.isekaiapi.api.remap.IsekaiRemap;
import com.kuronami.isekaiapi.impl.IsekaiQueryImpl;
import com.kuronami.isekaiapi.impl.IsekaiRemapImpl;

/**
 * Public facade for consumers. Two access points:
 * <ul>
 *   <li>{@link #query()} — read vanilla + modded worldgen rules</li>
 *   <li>{@link #remap()} — declare your worldshape transformation</li>
 * </ul>
 *
 * <p>Both facades are backed by snapshot/registry state populated at
 * {@code ServerAboutToStartEvent} and refreshed on every datapack reload — queries
 * return real data once the server has started, and declarations take effect via the
 * accompanying NeoForge biome / structure modifiers
 * ({@code isekai_api:apply_worldshape} / {@code isekai_api:apply_worldshape_structures}).
 *
 * <p>Internal-use snapshot bridging (used by lifecycle hooks, biome modifier phase
 * appliers, and the structure-placement Mixin) lives in
 * {@code com.kuronami.isekaiapi.impl.IsekaiInternal}, not here. The public surface stays
 * minimal: query + remap.
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
