package com.example.computer_store.util.validation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Lightweight input validation helpers for form data.
 */
public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{3,30}$");

    private ValidationUtil() {
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static boolean isValidEmail(String email) {
        return !isBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidUsername(String username) {
        return !isBlank(username) && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    /** Returns null when the string cannot be parsed. */
    public static Integer parseInt(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns null when the string cannot be parsed. */
    public static BigDecimal parseDecimal(String s) {
        if (isBlank(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Validates that a string is within the specified length range.
     * @param s The string to validate
     * @param minLength Minimum allowed length (inclusive)
     * @param maxLength Maximum allowed length (inclusive)
     * @return true if the string length is within range, false otherwise
     */
    public static boolean isValidLength(String s, int minLength, int maxLength) {
        if (s == null) {
            return minLength == 0;
        }
        int length = s.trim().length();
        return length >= minLength && length <= maxLength;
    }

    /**
     * Validates that a string does not exceed the maximum length.
     * @param s The string to validate
     * @param maxLength Maximum allowed length (inclusive)
     * @return true if the string length is within limit, false otherwise
     */
    public static boolean isValidMaxLength(String s, int maxLength) {
        if (s == null) {
            return true;
        }
        return s.trim().length() <= maxLength;
    }

    /**
     * Validates that a string meets the minimum length requirement.
     * @param s The string to validate
     * @param minLength Minimum allowed length (inclusive)
     * @return true if the string length meets minimum, false otherwise
     */
    public static boolean isValidMinLength(String s, int minLength) {
        if (s == null) {
            return minLength == 0;
        }
        return s.trim().length() >= minLength;
    }

    /**
     * Validates that a string contains only safe characters (alphanumeric, spaces, and basic punctuation).
     * Use for input validation, NOT for XSS protection.
     * For XSS protection, use context-aware output encoding (JSTL <c:out>, Java Encoder, etc.)
     * @param s The string to validate
     * @return true if the string contains only safe characters, false otherwise
     */
    public static boolean containsOnlySafeCharacters(String s) {
        if (s == null) {
            return true;
        }
        // Allow alphanumeric, spaces, and basic punctuation
        return s.matches("^[a-zA-Z0-9\\s.,!?@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]*$");
    }

    /**
     * Validates that a string does not contain common XSS patterns.
     * This is a DEFENSE-IN-DEPTH check only. Primary XSS protection MUST be
     * context-aware output encoding at the point of rendering (JSTL <c:out>).
     * @param s The string to check
     * @return true if no suspicious patterns found, false otherwise
     */
    public static boolean hasNoXssPatterns(String s) {
        if (s == null) {
            return true;
        }
        String lower = s.toLowerCase();
        // Check for common XSS patterns
        return !lower.contains("<script")
                && !lower.contains("</script>")
                && !lower.contains("javascript:")
                && !lower.contains("onerror")
                && !lower.contains("onload")
                && !lower.contains("onclick")
                && !lower.contains("onmouseover")
                && !lower.contains("<iframe")
                && !lower.contains("<object")
                && !lower.contains("<embed")
                && !lower.contains("expression(")
                && !lower.contains("vbscript:");
    }
}