package com.hengtongan.computerstore.util.security;

import com.hengtongan.computerstore.core.exception.ValidationException;

import java.util.List;

/**
 * Single source of truth for password strength rules.
 *
 * <p>Registration, password change, and the forgot-password reset flows all
 * enforce the same policy. It used to be copy-pasted in {@code AuthService}
 * and {@code UserService}; keeping it in one place means a rule change cannot
 * drift apart between the two flows.
 *
 * <p>The two-character-class checks are deliberately separate regex passes
 * rather than one combined pattern: each check produces a specific, actionable
 * message ("missing an uppercase letter" vs "missing a special character"),
 * which is more useful than a single generic rejection. There are four short
 * passes over a string that is at most 128 characters, so the cost is
 * irrelevant.
 *
 * <p>Common-pattern detection uses substring containment, not exact matching,
 * so a password like {@code password123} is rejected. That also means a
 * {@code HashSet} lookup cannot replace the scan: each candidate must still be
 * tested for containment, and the candidate list is seven short strings.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** Weak patterns that must not appear anywhere in the password. */
    private static final List<String> COMMON_WEAK_PATTERNS =
            List.of("password", "12345678", "qwerty", "abc123", "letmein", "admin", "welcome");

    /**
     * Validates a candidate password against the site's strength policy.
     *
     * @param password the plaintext candidate
     * @throws ValidationException with a specific reason if it does not comply
     */
    public static void validateStrength(String password) {
        if (password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long.");
        }
        if (password.length() > 128) {
            throw new ValidationException("Password must not exceed 128 characters.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new ValidationException("Password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new ValidationException("Password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new ValidationException("Password must contain at least one digit.");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new ValidationException("Password must contain at least one special character (!@#$%^&*()_+-=[]{};':\"|,.<>/?).");
        }

        String lower = password.toLowerCase();
        for (String common : COMMON_WEAK_PATTERNS) {
            if (lower.contains(common)) {
                throw new ValidationException("Password contains common weak patterns. Please choose a stronger password.");
            }
        }
    }
}