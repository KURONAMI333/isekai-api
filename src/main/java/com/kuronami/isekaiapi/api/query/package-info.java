/**
 * Read-only query surface — inspect vanilla + modded worldgen rules.
 *
 * <p>Backed by a {@code VanillaRuleSnapshot} populated at
 * {@code ServerAboutToStartEvent} and refreshed on every datapack reload. All queries
 * return immutable views and are O(1) once the cache is warm.
 *
 * <p>The four info records ({@link com.kuronami.isekaiapi.api.query.PlacedFeatureInfo},
 * {@link com.kuronami.isekaiapi.api.query.StructurePlacementInfo},
 * {@link com.kuronami.isekaiapi.api.query.MobSpawnInfo},
 * {@link com.kuronami.isekaiapi.api.query.WorldshapeSnapshot}) are pure data — they
 * carry no live registry references and are safe to retain across reloads (they will
 * just go stale; re-query for the latest values).
 */
package com.kuronami.isekaiapi.api.query;
