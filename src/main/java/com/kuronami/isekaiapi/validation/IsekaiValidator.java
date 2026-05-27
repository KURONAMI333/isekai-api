package com.kuronami.isekaiapi.validation;

import com.kuronami.isekaiapi.IsekaiApi;

/**
 * v0.1 skeleton for the {@code /isekai validate <namespace>} command and startup-time
 * JSON schema check. Spec §5.5.8:
 * <ul>
 *   <li>scan {@code data/<ns>/isekai/} on startup; log any schema violations and skip
 *       the offending file rather than crashing</li>
 *   <li>{@code /isekai validate <namespace>} re-runs the scan against one namespace</li>
 *   <li>{@code isekai.validation_strict_mode}: when true, reject the entire datapack
 *       on any validation error instead of skipping just the bad file</li>
 * </ul>
 *
 * <p>v0.1: stub returns ValidationResult.ok() unconditionally. Functional validator (JSON
 * schema checking against WorldshapeDescriptor codec + cross-field rules like
 * {@code playable_range.min_y < max_y} or {@code ore_strategy.bands sum == 1.0}) lands
 * with the datapack reload pipeline in v0.2.
 */
public final class IsekaiValidator {

    private IsekaiValidator() {}

    public static ValidationResult validateNamespace(String namespace) {
        IsekaiApi.LOGGER.info("[Isekai v0.1 stub] validateNamespace({}) -> ok (no-op)", namespace);
        return ValidationResult.ok(0);
    }

    public record ValidationResult(int filesChecked, int errorsFound, java.util.List<String> messages) {
        public static ValidationResult ok(int filesChecked) {
            return new ValidationResult(filesChecked, 0, java.util.List.of());
        }
        public boolean isOk() {
            return errorsFound == 0;
        }
    }
}
