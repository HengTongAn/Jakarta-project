package com.hengtongan.computerstore.util.security;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Logs loudly at startup while any account still uses a seeded default
 * password.
 *
 * <p>{@code db/seed/seed-data.sql} ships two public credentials (admin/admin123,
 * customer/customer123) for local development. On a reachable deployment those
 * are an instant compromise, so this check emits an ERROR naming the offending
 * account until both are rotated. Detection is by plaintext: every live user's
 * BCrypt hash is verified against the two seeded passwords, so a re-hash of the
 * same weak password (different salt/tool, still {@code admin123}) is caught
 * too - string-matching the shipped hash would silently miss that case.
 *
 * <p>It never blocks startup (the database may not be reachable yet from a
 * listener) and can be switched off only when the seeding convention itself
 * changes: {@code -Dcomputerstore.security.checkDefaultCredentials=false}.
 */
public final class DefaultCredentialsChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCredentialsChecker.class);

    // Plaintexts exactly as shipped in src/main/resources/db/seed/seed-data.sql.
    private static final String[] SEEDED_PASSWORDS = {
            "admin123",       // admin    / SUPER_ADMIN
            "customer123"     // customer / CUSTOMER
    };

    private DefaultCredentialsChecker() {
    }

    /**
     * Queries every live user and flags any whose password still verifies
     * against a seeded default. Logs an ERROR naming the accounts and returns
     * how many were found (0 = clean, or the check was disabled / could not
     * run).
     */
    public static int checkAndLog() {
        if (!Boolean.parseBoolean(System.getProperty(
                "computerstore.security.checkDefaultCredentials", "true"))) {
            return 0;
        }
        List<String> offending = new ArrayList<>();
        String sql = "SELECT username, role, password_hash FROM users "
                + "WHERE deleted_at IS NULL";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String hash = rs.getString("password_hash");
                if (hash == null || hash.isBlank()) {
                    continue;
                }
                for (String seeded : SEEDED_PASSWORDS) {
                    if (PasswordUtil.check(seeded, hash)) {
                        offending.add(rs.getString("username") + "("
                                + rs.getString("role") + ")");
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            // The pool may not be reachable yet (or tests run without a DB);
            // never block startup over the check itself.
            LOGGER.debug("Default-credentials check skipped: {}", e.getMessage());
            return 0;
        }
        if (!offending.isEmpty()) {
            LOGGER.error("SECURITY: seeded default credentials are still active for account(s): {}. "
                            + "These passwords are public knowledge - change them before this "
                            + "instance is reachable (see docs/go-live.md).",
                    String.join(", ", offending));
        }
        return offending.size();
    }
}