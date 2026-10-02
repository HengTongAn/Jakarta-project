package com.hengtongan.computerstore.core.domain.entity;

import java.sql.Timestamp;

public class PasswordResetCode {

    /** How long a reset code stays valid (minutes). */
    public static final long EXPIRY_MINUTES = 10;

    private int codeId;
    private int userId;
    private String email;
    private String codeHash;
    private Timestamp expiresAt;
    private Timestamp usedAt;
    private Timestamp createdAt;

    /**
     * Wrong codes typed against this row. A 6-digit code has only a million
     * possibilities, so without a ceiling an attacker who triggered a reset
     * could grind the space one guess at a time.
     */
    private int failedAttempts;

    public PasswordResetCode() {
    }

    /** A code is usable only when it exists and has not expired yet. */
    public boolean isUsable() {
        return usedAt == null && expiresAt != null
                && expiresAt.getTime() > System.currentTimeMillis();
    }

    /** True once enough wrong guesses have been charged that the code must be reissued. */
    public boolean isExhausted(int maxAttempts) {
        return failedAttempts >= maxAttempts;
    }

    public int getCodeId() {
        return codeId;
    }

    public void setCodeId(int codeId) {
        this.codeId = codeId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Timestamp getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Timestamp usedAt) {
        this.usedAt = usedAt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }
}
