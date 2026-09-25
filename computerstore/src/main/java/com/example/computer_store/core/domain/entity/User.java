package com.example.computer_store.core.domain.entity;

import java.sql.Timestamp;

public class User {

    public enum Role {
        SUPER_ADMIN, ADMIN, CUSTOMER
    }

    private int userId;
    private String username;
    private String passwordHash;
    private String fullName;
    private String email;
    private String avatarUrl;
    private Role role;
    private Timestamp lastActiveAt;
    private Timestamp createdAt;
    private Timestamp deletedAt;

    public User() {
    }

    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.SUPER_ADMIN;
    }

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(Timestamp lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public Timestamp getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Timestamp deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isActiveRecently(long thresholdMillis) {
        return lastActiveAt != null && lastActiveAt.getTime() > thresholdMillis;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}