/**
 * Phase-by-phase appliers for {@code isekai_api:apply_worldshape}.
 *
 * <ul>
 *   <li>{@link com.kuronami.isekaiapi.biomemodifier.phase.RemovePhase} — drops excluded
 *       features / carvers and clears the originals targeted by the active strategy.</li>
 *   <li>{@link com.kuronami.isekaiapi.biomemodifier.phase.AddPhase} — injects additional
 *       features / carvers and re-injects strategy-remapped placed features.</li>
 *   <li>{@link com.kuronami.isekaiapi.biomemodifier.phase.ModifyPhase} — mob spawn
 *       scaling and atmospheric overrides (temperature, downfall, colors, etc.).</li>
 *   <li>{@link com.kuronami.isekaiapi.biomemodifier.phase.BiomeMatcher} — the
 *       {@code appliesTo} filter shared by all three phases.</li>
 * </ul>
 *
 * <p>Each phase class is independently testable and groups its concerns by lifecycle
 * stage rather than by content kind.
 */
package com.kuronami.isekaiapi.biomemodifier.phase;
