package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.repository.PasswordResetTokenRepository;
import com.hengtongan.computerstore.core.repository.UserRepository;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.PasswordResetToken;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.infrastructure.messaging.EmailUtil;
import com.hengtongan.computerstore.util.security.PasswordUtil;
import com.hengtongan.computerstore.util.validation.ValidationUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Base64;

/**
 * Forgot-password flow using a one-time, expiring email link.
 *
 * The customer asks to reset by email; we mint a random token, store only its
 * SHA-256 hash, and email a link containing the raw token. The link is valid
 * for 30 minutes, works exactly once, and never leaks the account's password.
 */
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userDAO = new UserRepository();
    private final PasswordResetTokenRepository tokenDAO = new PasswordResetTokenRepository();

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Starts the reset flow for the address posted by the form.
     *
     * The method deliberately answers the same way whether or not an account
     * exists, so a stranger cannot guess which emails are registered. The email
     * (or, in development without SMTP, the console) is the only place the
     * link appears.
     */
    public void requestReset(String email, String baseUrl) {
        if (!ValidationUtil.isValidEmail(email)) {
            throw new ValidationException("Please enter a valid email address.");
        }
        User user = userDAO.findByEmail(email.trim());
        if (user == null) {
            return;
        }

        // A fresh request revokes any older outstanding links for this user.
        tokenDAO.invalidateForUser(user.getUserId());

        String rawToken = newToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getUserId());
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(new Timestamp(System.currentTimeMillis()
                + PasswordResetToken.EXPIRY_MINUTES * 60_000));
        tokenDAO.create(token);

        String link = baseUrl + "/reset?token=" + rawToken;
        String body = "Hello " + user.getFullName() + ",\n\n"
                + "We received a request to reset your password. Click the link below:\n\n"
                + link + "\n\n"
                + "The link expires in " + PasswordResetToken.EXPIRY_MINUTES
                + " minutes and can be used only once. If you didn't ask for this, ignore the email.\n\n"
                + "Apach_PC/STORE";

        boolean sent = EmailUtil.send(user.getEmail(), "Reset your password - Apach_PC/STORE", body);
        if (!sent) {
            // Development fallback: the link must never leak to the console by
            // default. Log it only when explicitly opted in via
            // -Dcomputerstore.password.reset.devTokens=true (local testing).
            if (Boolean.parseBoolean(System.getProperty("computerstore.password.reset.devTokens", "false"))) {
                LOGGER.info("[password-reset] DEMO link for {}: {}", user.getEmail(), link);
            }
        }
    }

    /**
     * Checks a link's token. Returns the token row on success, or null when it
     * is missing, expired or already used. Callers must never reveal why.
     */
    public PasswordResetToken validateToken(String rawToken) {
        if (ValidationUtil.isBlank(rawToken)) {
            return null;
        }
        PasswordResetToken token = tokenDAO.findByTokenHash(hash(rawToken.trim()));
        if (token == null || !token.isUsable()) {
            return null;
        }
        return token;
    }

    /**
     * Applies the new password chosen on the reset form, then burns the token
     * so the same link cannot be replayed. The customer was already proven to
     * own the address by possessing the emailed link.
     */
    public void completeReset(String rawToken, String newPassword, String confirmPassword) {
        PasswordResetToken token = validateToken(rawToken);
        if (token == null) {
            throw new ValidationException("This link is invalid or has expired. Please request a new one.");
        }
        if (ValidationUtil.isBlank(newPassword)) {
            throw new ValidationException("New password is required.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match.");
        }
        UserService.validatePasswordStrength(newPassword);

        try (java.sql.Connection c = com.hengtongan.computerstore.infrastructure.persistence.DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // The conditional update is the single-use gate: concurrent
                // requests can both read the token, but only one can claim it.
                if (!tokenDAO.claimForUse(c, token.getTokenId())) {
                    throw new ValidationException("This link is invalid or has expired. Please request a new one.");
                }
                userDAO.updatePassword(c, token.getUserId(), PasswordUtil.hash(newPassword));
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not reset password. Please try again.", e);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
