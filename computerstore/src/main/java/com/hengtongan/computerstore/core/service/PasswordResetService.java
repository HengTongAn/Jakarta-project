package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.repository.PasswordResetTokenRepository;
import com.hengtongan.computerstore.core.repository.PasswordResetCodeRepository;
import com.hengtongan.computerstore.core.repository.UserRepository;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.PasswordResetToken;
import com.hengtongan.computerstore.core.domain.entity.PasswordResetCode;
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
 *
 * <p>A second, code-based flow runs alongside it: a 6-digit code mailed to the
 * address, then verified, then a new password. See
 * {@link #requestResetWithCode}, {@link #verifyCode} and
 * {@link #completeResetWithCode}.</p>
 */
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userDAO = new UserRepository();
    private final PasswordResetTokenRepository tokenDAO = new PasswordResetTokenRepository();
    private final PasswordResetCodeRepository codeDAO = new PasswordResetCodeRepository();

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Wrong codes tolerated against one issued code before it is burned and the
     * customer must request a new one. A 6-digit code has a million possible
     * values, so without a ceiling the space is grindable; five guesses turns a
     * brute force into a non-attack.
     */
    public static final int MAX_CODE_ATTEMPTS = 5;

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
                // Login reads the user from cache; without this the previous
                // password keeps working and the new one is rejected.
                userDAO.invalidateCachedUsers();
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

    /**
     * Step 1 of the 6-digit code flow: generates and emails a 6-digit code.
     * Only the SHA-256 hash is stored; the raw code leaves this method by email.
     */
    public void requestResetWithCode(String email, String baseUrl) {
        if (!ValidationUtil.isValidEmail(email)) {
            throw new ValidationException("Please enter a valid email address.");
        }
        User user = userDAO.findByEmail(email.trim());
        if (user == null) {
            return;
        }

        codeDAO.invalidateForEmail(email.trim());

        String rawCode = generateSixDigitCode();
        PasswordResetCode code = new PasswordResetCode();
        code.setUserId(user.getUserId());
        code.setEmail(email.trim());
        code.setCodeHash(hash(rawCode));
        code.setExpiresAt(new Timestamp(System.currentTimeMillis()
                + PasswordResetCode.EXPIRY_MINUTES * 60_000));
        codeDAO.create(code);

        String body = "Hello " + user.getFullName() + ",\n\n"
                + "We received a request to reset your password. Your verification code is:\n\n"
                + rawCode + "\n\n"
                + "This code expires in " + PasswordResetCode.EXPIRY_MINUTES
                + " minutes and can be used only once. If you didn't ask for this, ignore this email.\n\n"
                + "Apach_PC/STORE";

        boolean sent = EmailUtil.send(user.getEmail(), "Password Reset Code - Apach_PC/STORE", body);
        if (!sent) {
            if (Boolean.parseBoolean(System.getProperty("computerstore.password.reset.devCodes", "false"))) {
                LOGGER.info("[password-reset-code] DEMO code for {}: {}", user.getEmail(), rawCode);
            }
        }
    }

    /**
     * Step 2 of the 6-digit code flow: verifies the user-provided code.
     * Returns the codeId for use in the final step.
     *
     * <p>Every wrong guess is charged against the code that was actually issued
     * for this email. Once {@link #MAX_CODE_ATTEMPTS} is reached the code is
     * burned, so the flow cannot be used to grind the 6-digit key space.</p>
     */
    public int verifyCode(String email, String code) {
        if (ValidationUtil.isBlank(email) || ValidationUtil.isBlank(code)) {
            throw new ValidationException("Email and code are required.");
        }
        if (!code.matches("\\d{6}")) {
            throw new ValidationException("Please enter a valid 6-digit code.");
        }

        PasswordResetCode resetCode = codeDAO.findByEmailAndCodeHash(email.trim(), hash(code.trim()));
        if (resetCode == null || !resetCode.isUsable() || resetCode.isExhausted(MAX_CODE_ATTEMPTS)) {
            chargeFailedAttempt(email.trim());
            throw new ValidationException("Invalid or expired code. Please request a new one.");
        }
        return resetCode.getCodeId();
    }

    /**
     * Records a wrong guess and burns the code once the budget is spent.
     * Failures here must never mask the caller's "invalid code" error, so a
     * database problem is logged and swallowed.
     */
    private void chargeFailedAttempt(String email) {
        try {
            int attempts = codeDAO.recordFailedAttempt(email);
            if (attempts >= MAX_CODE_ATTEMPTS) {
                codeDAO.invalidateForEmail(email);
                LOGGER.info("[password-reset-code] burned code for {} after {} failed attempts",
                        email, attempts);
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Could not record failed reset attempt for {}: {}", email, e.getMessage());
        }
    }

    /**
     * Step 3 of the 6-digit code flow: sets the new password after code verification.
     *
     * <p>{@code codeId} comes from the session (set during verifyCode) and
     * {@code verifiedEmail} is the address whose code was actually verified.
     * The account that gets rewritten is read from the code row, never from a
     * request parameter: an attacker can verify a code for their own address,
     * but supplying a victim's email here must not be able to redirect the
     * password change at them.</p>
     */
    public void completeResetWithCode(int codeId, String verifiedEmail,
                                     String newPassword, String confirmPassword) {
        if (ValidationUtil.isBlank(newPassword)) {
            throw new ValidationException("New password is required.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match.");
        }
        UserService.validatePasswordStrength(newPassword);

        PasswordResetCode resetCode = codeDAO.findByCodeId(codeId);
        if (resetCode == null || !resetCode.isUsable()) {
            throw new ValidationException("Invalid or expired code. Please request a new one.");
        }
        if (ValidationUtil.isBlank(verifiedEmail)
                || !resetCode.getEmail().equalsIgnoreCase(verifiedEmail.trim())) {
            throw new ValidationException("This code was not issued for this account. Please start over.");
        }

        try (java.sql.Connection c = com.hengtongan.computerstore.infrastructure.persistence.DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!codeDAO.claimForUse(c, codeId)) {
                    throw new ValidationException("Invalid or expired code. Please request a new one.");
                }
                userDAO.updatePassword(c, resetCode.getUserId(), PasswordUtil.hash(newPassword));
                c.commit();
                // Login reads the user from cache; without this the previous
                // password keeps working and the new one is rejected.
                userDAO.invalidateCachedUsers();
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

    private static String generateSixDigitCode() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
