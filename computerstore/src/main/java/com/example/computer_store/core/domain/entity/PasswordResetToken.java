package com.example.computer_store.core.domain.entity;

import java.sql.Timestamp;

public class PasswordResetToken {

    /** How long a reset link stays valid (minutes). */
    public static final long EXPIRY_MINUTES = 30;

    private int tokenId;
    private int userId;
    private String tokenHash;
    private Timestamp expiresAt;
    private Timestamp usedAt;
    private Timestamp createdAt;

    public PasswordResetToken() {
    }

    /** A token is usable only when it exists and has not expired yet. */
    public boolean isUsable() {
        return usedAt == null && expiresAt != null
                && expiresAt.getTime() > System.currentTimeMillis();
    }

    public int getTokenId() {
        return tokenId;
    }

    public void setTokenId(int tokenId) {
        this.tokenId = tokenId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
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
}