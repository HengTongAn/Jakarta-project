package com.hengtongan.computerstore.util.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing utilities based on BCrypt.
 * Plain-text passwords are never stored; only salted hashes are kept.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /**
     * BCrypt cost factor for {@link #hash}: 2^12 rounds, matching the current
     * OWASP recommendation (raised from the earlier hard-coded 10). Cost only
     * affects NEW hashes -- {@code check} reads the cost embedded in each
     * stored hash, so existing {@code $2a$10$} passwords verify unchanged.
     */
    private static final int BCRYPT_COST = 12;

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    public static boolean check(String plainPassword, String bcryptHash) {
        if (plainPassword == null || bcryptHash == null || bcryptHash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, bcryptHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}