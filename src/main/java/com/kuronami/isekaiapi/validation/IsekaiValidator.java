package com.kuronami.isekaiapi.validation;

import com.kuronami.isekaiapi.IsekaiApi;

import java.util.List;

/**
 * v0.1 skeleton for the {@code /isekai validate <namespace>} command and startup-time
 * JSON schema check.
 *
 * <p>v0.2 will implement:
 * <ul>
 *   <li>Scan {@code data/<ns>/isekai/} on server start; log violations and skip offending
 *       files rather than crashing.</li>
 *   <li>Cross-field validation: {@code playable_range.min_y < max_y},
 *       {@code BandSplit.ratios} sum equals 1.0, non-overlapping layered Y ranges, etc.</li>
 *   <li>{@code isekai.validation_strict_mode} config: reject the entire datapack on any
 *       error instead of skipping per file.</li>
 * </ul>
 *
 * <p>v0.1 stub: {@link #validateNamespace(String)} returns {@link ValidationResult#ok}.
 */
public final class IsekaiValidator {

    private IsekaiValidator() {}

    public static ValidationResult validateNamespace(String namespace) {
        IsekaiApi.LOGGER.info("[Isekai v0.1 stub] validateNamespace({}) -> ok (no-op)", namespace);
        return ValidationResult.ok(0);
    }

    public record ValidationResult(int filesChecked, int errorsFound, List<String> messages) {
        public ValidationResult {
            messages = List.copyOf(messages);
        }

        public static ValidationResult ok(int filesChecked) {
            return new ValidationResult(filesChecked, 0, List.of());
        }

        public boolean isOk() {
            return errorsFound == 0;
        }
    }
}
