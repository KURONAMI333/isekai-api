/**
 * Isekai-provided {@link net.minecraft.world.level.levelgen.SurfaceRules.RuleSource}
 * implementations registered into the vanilla {@code BuiltInRegistries.MATERIAL_RULE}
 * registry. Datapack consumers reference them via {@code "type": "isekai_api:<name>"}
 * inside the noise_settings {@code surface_rule} sequence.
 *
 * <p>All rules in this package read the active {@link
 * com.kuronami.isekaiapi.api.remap.WorldshapeDescriptor#blockOverrides()} for the named
 * dimension at apply-time, so the override map is hot-reloadable along with the rest of
 * the worldshape JSON.
 *
 * <ul>
 *   <li>{@link com.kuronami.isekaiapi.surfacerule.WorldshapeSurfaceTopRule}
 *       ({@code isekai_api:worldshape_surface_top}) — overrides the per-biome top block.</li>
 *   <li>{@link com.kuronami.isekaiapi.surfacerule.WorldshapeDefaultBlockRule}
 *       ({@code isekai_api:worldshape_default_block}) — overrides the default (stone-equivalent)
 *       column fill block per biome.</li>
 * </ul>
 *
 * <p>Both rules return {@code null} from {@code SurfaceRule.tryApply} when the active
 * worldshape doesn't have an entry for the current biome — letting the next rule in the
 * sequence handle the position.
 */
package com.kuronami.isekaiapi.surfacerule;
