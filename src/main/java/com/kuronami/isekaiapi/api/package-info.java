/**
 * Public consumer API for Isekai: {@link com.kuronami.isekaiapi.api.Isekai} exposes two facades,
 * and {@link com.kuronami.isekaiapi.api.wood.WoodSet} declares wooden species:
 *
 * <ul>
 *   <li>{@link com.kuronami.isekaiapi.api.query.IsekaiQuery} — read vanilla + modded worldgen
 *       rules (placed features, structures, mob spawns, density functions).</li>
 *   <li>{@link com.kuronami.isekaiapi.api.remap.IsekaiRemap} — declare worldshape
 *       transformations that take effect via the accompanying NeoForge biome /
 *       structure modifiers ({@code isekai_api:apply_worldshape}, {@code
 *       isekai_api:apply_worldshape_structures}).</li>
 *   <li>{@link com.kuronami.isekaiapi.api.wood.WoodSet} — register a complete wooden species
 *       (log through boat) into the consumer's own registries from one builder chain.</li>
 * </ul>
 *
 * <p>This is the only package consumers should import from. Anything under
 * {@code com.kuronami.isekaiapi.impl} is internal and may change between versions.
 */
package com.kuronami.isekaiapi.api;
