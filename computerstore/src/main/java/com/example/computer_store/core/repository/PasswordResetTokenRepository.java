package com.example.computer_store.core.repository;

import com.example.computer_store.core.domain.entity.PasswordResetToken;
import com.example.computer_store.infrastructure.persistence.DBConnection;
import com.example.computer_store.util.web.ErrorHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class PasswordResetTokenRepository {

    private static final String SELECT_COLUMNS
	    = "token_id, user_id, token_hash, expires_at, used_at, created_at";

    private PasswordResetToken mapRow(ResultSet rs) throws SQLException {
	PasswordResetToken token = new PasswordResetToken();
	token.setTokenId(rs.getInt("token_id"));
	token.setUserId(rs.getInt("user_id"));
	token.setTokenHash(rs.getString("token_hash"));
	token.setExpiresAt(rs.getTimestamp("expires_at"));
	token.setUsedAt(rs.getTimestamp("used_at"));
	token.setCreatedAt(rs.getTimestamp("created_at"));
	return token;
    }

    private Connection conn() throws SQLException {
	return DBConnection.getConnection();
    }

    public int create(PasswordResetToken token) {
	String sql = "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)";
	try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
	    ps.setInt(1, token.getUserId());
	    ps.setString(2, token.getTokenHash());
	    ps.setTimestamp(3, token.getExpiresAt());
	    ps.executeUpdate();
	    try (ResultSet keys = ps.getGeneratedKeys()) {
		if (keys.next()) {
		    return keys.getInt(1);
		}
	    }
	    return -1;
	} catch (SQLException e) {
	    throw ErrorHandler.handleDatabaseError("creating password reset token", e);
	}
    }

    public PasswordResetToken findByTokenHash(String tokenHash) {
	String sql = "SELECT " + SELECT_COLUMNS + " FROM password_reset_tokens WHERE token_hash = ?";
	try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
	    ps.setString(1, tokenHash);
	    try (ResultSet rs = ps.executeQuery()) {
		if (rs.next()) {
		    return mapRow(rs);
		}
	    }
	} catch (SQLException e) {
	    throw ErrorHandler.handleDatabaseError("finding password reset token", e);
	}
	return null;
    }

    public void markUsed(int tokenId) {
	String sql = "UPDATE password_reset_tokens SET used_at = ? WHERE token_id = ?";
	try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
	    ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
	    ps.setInt(2, tokenId);
	    ps.executeUpdate();
	} catch (SQLException e) {
	    throw ErrorHandler.handleDatabaseError("marking password reset token used", e);
	}
    }

    /** Atomically claims an unexpired, unused token for a reset transaction. */
    public boolean claimForUse(Connection c, int tokenId) throws SQLException {
        String sql = "UPDATE password_reset_tokens SET used_at = ? "
                + "WHERE token_id = ? AND used_at IS NULL AND expires_at > ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            ps.setTimestamp(1, now);
            ps.setInt(2, tokenId);
            ps.setTimestamp(3, now);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Revokes every outstanding token for a user (e.g. on a new request).
     */
    public void invalidateForUser(int userId) {
	String sql = "UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL";
	try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
	    ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
	    ps.setInt(2, userId);
	    ps.executeUpdate();
	} catch (SQLException e) {
	    throw ErrorHandler.handleDatabaseError("revoking password reset tokens", e);
	}
    }
}
