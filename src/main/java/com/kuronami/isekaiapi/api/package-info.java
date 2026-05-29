/**
 * Public consumer API for Isekai. The single entry point is {@link com.kuronami.isekaiapi.api.Isekai},
 * which exposes two facades:
 *
 * <ul>
 *   <li>{@link com.kuronami.isekaiapi.api.query.IsekaiQuery} — read vanilla + modded worldgen
 *       rules (placed features, structures, mob spawns, density functions).</li>
 *   <li>{@link com.kuronami.isekaiapi.api.remap.IsekaiRemap} — declare worldshape
 *       transformations that take effect via the accompanying NeoForge biome /
 *       structure modifiers ({@code isekai_api:apply_worldshape}, {@code
 *       isekai_api:apply_worldshape_structures}).</li>
 * </ul>
 *
 * <p>This is the only package consumers should import from. Anything under
 * {@code com.kuronami.isekaiapi.impl} is internal and may change between versions.
 */
package com.kuronami.isekaiapi.api;
