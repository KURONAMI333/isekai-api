package com.kuronami.isekaiapi.impl;

import com.kuronami.isekaiapi.api.Isekai;
import com.kuronami.isekaiapi.api.query.IsekaiQuery;

/**
 * Internal access bridge for impl-class consumers (biome / structure modifiers,
 * evaluators, lifecycle hooks). Not part of the public consumer API — external mods
 * should go through {@link Isekai#query()} / {@link Isekai#remap()}.
 *
 * <p>This class exists so the public {@link Isekai} facade doesn't have to expose
 * snapshot-publishing methods at top level. The lifecycle code calls
 * {@link #publishSnapshot}; phase appliers and the Mixin evaluator call
 * {@link #currentSnapshot}.
 */
public final class IsekaiInternal {

    private IsekaiInternal() {}

    /** Publish a freshly-scanned snapshot to the query cache. */
    public static void publishSnapshot(VanillaRuleSnapshot snapshot) {
        ((IsekaiQueryImpl) Isekai.query()).setSnapshot(snapshot);
    }

    /** Read the current snapshot. Returns {@link VanillaRuleSnapshot#EMPTY} if unset. */
    public static VanillaRuleSnapshot currentSnapshot() {
        return ((IsekaiQueryImpl) Isekai.query()).getSnapshot();
    }
}
