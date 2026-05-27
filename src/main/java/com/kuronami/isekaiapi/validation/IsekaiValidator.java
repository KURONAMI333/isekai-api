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

    /**
     * Result of a namespace validation pass. {@link #errors} is the canonical source of
     * truth; {@link #errorsFound()} is derived from it. {@link #filesChecked} counts
     * every JSON file that was inspected (including the ones that passed).
     */
    public record ValidationResult(int filesChecked, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(errors);
            if (filesChecked < 0) {
                throw new IllegalArgumentException("filesChecked < 0");
            }
        }

        public static ValidationResult ok(int filesChecked) {
            return new ValidationResult(filesChecked, List.of());
        }

        public int errorsFound() {
            return errors.size();
        }

        public boolean isOk() {
            return errors.isEmpty();
        }
    }
}
