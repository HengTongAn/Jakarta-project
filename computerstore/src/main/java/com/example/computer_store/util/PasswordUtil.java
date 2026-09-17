package com.example.computer_store.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing utilities based on BCrypt.
 * Plain-text passwords are never stored; only salted hashes are kept.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
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