package com.hengtongan.computerstore.infrastructure.security;

import com.hengtongan.computerstore.core.repository.UserRepository;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Two-Factor Authentication (2FA) service using TOTP (Time-based One-Time Password).
 * Integrates with Google Authenticator and compatible apps.
 */
public final class TwoFactorAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwoFactorAuthService.class);

    private static final String ISSUER = "ComputerStore";
    private static final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PROPERTY = "computerstore.2fa.encryption.key";
    private static final String KEY_ENVIRONMENT = "COMPUTERSTORE_2FA_ENCRYPTION_KEY";

    private TwoFactorAuthService() {
    }

    /**
     * Validates that the 2FA encryption key is configured.
     * Should be called at application startup if 2FA is enabled.
     */
    public static void validateConfiguration() {
        requireEncryptionKey();
        LOGGER.info("2FA encryption key validated successfully");
    }

    /**
     * Generates a new secret key for a user and returns the QR code URL.
     */
    public static TwoFactorSetupResult generateSecretKey(User user) {
        requireEncryptionKey();
        GoogleAuthenticatorKey key = gAuth.createCredentials();

        String secret = key.getKey();
        // Generate manual entry URL format: otpauth://totp/ISSUER:USERNAME?secret=SECRET
        String qrCodeUrl = "otpauth://totp/" + ISSUER + ":" + user.getUsername() + "?secret=" + secret;

        // Store the secret in the database (not yet enabled)
        try {
            storeSecretKey(user.getUserId(), encrypt(secret), false);
        } catch (SQLException e) {
            LOGGER.error("Failed to store 2FA secret key for user {}", user.getUserId(), e);
            throw new RuntimeException("Failed to setup 2FA", e);
        }

        return new TwoFactorSetupResult(secret, qrCodeUrl);
    }

    /**
     * Verifies a TOTP code against the user's <em>enabled</em> secret (login / disable).
     */
    public static boolean verifyCode(int userId, int code) {
        return verifyCode(userId, code, true);
    }

    /**
     * Verifies a TOTP code against a pending (not yet enabled) enrollment secret.
     * Used only during the enable step after {@link #generateSecretKey}.
     */
    public static boolean verifyPendingCode(int userId, int code) {
        return verifyCode(userId, code, false);
    }

    private static boolean verifyCode(int userId, int code, boolean requireEnabled) {
        try {
            String secret = getSecretKey(userId, requireEnabled);
            if (secret == null) {
                return false;
            }
            return gAuth.authorize(secret, code);
        } catch (SQLException e) {
            LOGGER.error("Failed to verify 2FA code for user {}", userId, e);
            return false;
        }
    }

    /**
     * Enables 2FA for a user after successful verification.
     */
    public static void enableTwoFactor(int userId) throws SQLException {
        updateSecretKeyEnabled(userId, true);
        LOGGER.info("2FA enabled for user ID: {}", userId);
    }

    /**
     * Disables 2FA for a user.
     */
    public static void disableTwoFactor(int userId) throws SQLException {
        updateSecretKeyEnabled(userId, false);
        LOGGER.info("2FA disabled for user ID: {}", userId);
    }

    /**
     * Checks if 2FA is enabled for a user.
     */
    public static boolean isTwoFactorEnabled(int userId) {
        try {
            return isSecretKeyEnabled(userId);
        } catch (SQLException e) {
            LOGGER.error("Failed to check 2FA status for user {}", userId, e);
            return false;
        }
    }

    /**
     * Generates a backup code for account recovery.
     */
    public static String generateBackupCode() {
        // Generate a random 8-character backup code
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return code.toString();
    }

    // Database operations

    private static void storeSecretKey(int userId, String secret, boolean enabled) throws SQLException {
        String sql = "INSERT INTO two_factor_secrets (user_id, secret_key, enabled) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE secret_key = ?, enabled = ?";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, secret);
            ps.setBoolean(3, enabled);
            ps.setString(4, secret);
            ps.setBoolean(5, enabled);
            ps.executeUpdate();
        }
    }

    private static String getSecretKey(int userId, boolean requireEnabled) throws SQLException {
        String sql = requireEnabled
                ? "SELECT secret_key FROM two_factor_secrets WHERE user_id = ? AND enabled = true"
                : "SELECT secret_key FROM two_factor_secrets WHERE user_id = ? AND enabled = false";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String encrypted = rs.getString("secret_key");
                    String secret = decrypt(encrypted);
                    if (secret == null) {
                        // A stored secret exists but cannot be decrypted: the
                        // AES key was rotated/changed or the row is a legacy
                        // format. This is the loud diagnostic the report asked
                        // for - a silent "2FA no longer works for this user".
                        LOGGER.error("2FA secret for user {} cannot be decrypted "
                                        + "(legacy plaintext row, or COMPUTERSTORE_2FA_ENCRYPTION_KEY "
                                        + "changed since enrollment). The user must re-enroll: "
                                        + "Security tab -> disable 2FA, then enable it again.",
                                userId);
                    }
                    return secret;
                }
            }
        }
        return null;
    }

    private static boolean isSecretKeyEnabled(int userId) throws SQLException {
        String sql = "SELECT enabled FROM two_factor_secrets WHERE user_id = ?";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("enabled");
                }
            }
        }
        return false;
    }

    private static void updateSecretKeyEnabled(int userId, boolean enabled) throws SQLException {
        String sql = "UPDATE two_factor_secrets SET enabled = ? WHERE user_id = ?";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * TOTP secrets are credentials. Encrypt them with a deployment-owned key,
     * rather than keeping recoverable plaintext in MySQL or source control.
     * Configure a Base64-encoded AES-256 key through the documented system
     * property or environment variable before offering 2FA to users.
     */
    private static SecretKey requireEncryptionKey() {
        String encoded = System.getProperty(KEY_PROPERTY);
        if (encoded == null || encoded.isBlank()) {
            encoded = System.getenv(KEY_ENVIRONMENT);
        }
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("2FA encryption is not configured. Set "
                    + KEY_ENVIRONMENT + " to a Base64 AES key (256-bit recommended).");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encoded.trim());
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IllegalArgumentException("AES keys must be 128, 192, or 256 bits.");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid " + KEY_ENVIRONMENT + " value.", e);
        }
    }

    private static String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, requireEncryptionKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] value = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, value, 0, iv.length);
            System.arraycopy(encrypted, 0, value, iv.length, encrypted.length);
            return "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt 2FA secret.", e);
        }
    }

    private static String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith("v1:")) {
            LOGGER.warn("Refusing a legacy plaintext 2FA secret; the encryption key may have been "
                    + "rotated or the row predates encryption. Re-enrollment is required.");
            return null;
        }
        try {
            byte[] value = Base64.getUrlDecoder().decode(encrypted.substring(3));
            if (value.length <= 12) {
                LOGGER.warn("2FA secret is truncated or corrupt; re-enrollment is required.");
                return null;
            }
            byte[] iv = java.util.Arrays.copyOfRange(value, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(value, 12, value.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, requireEncryptionKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Almost always a changed/rotated COMPUTERSTORE_2FA_ENCRYPTION_KEY.
            // Log with remediation so ops can react instead of a silent lockout.
            LOGGER.warn("Could not decrypt a 2FA secret - likely the encryption key changed since "
                    + "enrollment. Affected users must re-enroll (disable + re-enable 2FA).", e);
            return null;
        }
    }

    /**
     * Result object for 2FA setup containing secret and QR code URL.
     */
    public static class TwoFactorSetupResult {
        private final String secret;
        private final String qrCodeUrl;

        public TwoFactorSetupResult(String secret, String qrCodeUrl) {
            this.secret = secret;
            this.qrCodeUrl = qrCodeUrl;
        }

        public String getSecret() {
            return secret;
        }

        public String getQrCodeUrl() {
            return qrCodeUrl;
        }
    }
}