package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.PasswordResetCode;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.web.ErrorHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

public class PasswordResetCodeRepository {

    private static final String SELECT_COLUMNS
        = "code_id, user_id, email, code_hash, expires_at, used_at, created_at, failed_attempts";

    private PasswordResetCode mapRow(ResultSet rs) throws SQLException {
        PasswordResetCode code = new PasswordResetCode();
        code.setCodeId(rs.getInt("code_id"));
        code.setUserId(rs.getInt("user_id"));
        code.setEmail(rs.getString("email"));
        code.setCodeHash(rs.getString("code_hash"));
        code.setExpiresAt(rs.getTimestamp("expires_at"));
        code.setUsedAt(rs.getTimestamp("used_at"));
        code.setCreatedAt(rs.getTimestamp("created_at"));
        code.setFailedAttempts(rs.getInt("failed_attempts"));
        return code;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    public int create(PasswordResetCode code) {
        String sql = "INSERT INTO password_reset_codes (user_id, email, code_hash, expires_at) VALUES (?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, code.getUserId());
            ps.setString(2, code.getEmail());
            ps.setString(3, code.getCodeHash());
            ps.setTimestamp(4, code.getExpiresAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("creating password reset code", e);
        }
    }

    public PasswordResetCode findByEmailAndCodeHash(String email, String codeHash) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM password_reset_codes WHERE email = ? AND code_hash = ? ORDER BY created_at DESC LIMIT 1";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, codeHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding password reset code", e);
        }
        return null;
    }

    /**
     * Loads a code by primary key.
     *
     * <p>This is the lookup the final reset step must use: the owning account is
     * read from the row itself, so a caller can never choose whose password gets
     * rewritten by supplying a different email.</p>
     */
    public PasswordResetCode findByCodeId(int codeId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM password_reset_codes WHERE code_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, codeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding password reset code by id", e);
        }
        return null;
    }

    /**
     * Charges one wrong guess against an email's outstanding code and returns
     * the new total. The row is found by email because a wrong guess matches no
     * hash, so there is no code_id to increment directly. Returns 0 when the
     * email has no live code left to charge.
     */
    public int recordFailedAttempt(String email) {
        String pickSql = "SELECT code_id FROM password_reset_codes "
                + "WHERE email = ? AND used_at IS NULL AND expires_at > ? "
                + "ORDER BY created_at DESC LIMIT 1";
        String bumpSql = "UPDATE password_reset_codes SET failed_attempts = failed_attempts + 1 "
                + "WHERE code_id = ?";
        String countSql = "SELECT failed_attempts FROM password_reset_codes WHERE code_id = ?";
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            int codeId;
            try (PreparedStatement ps = c.prepareStatement(pickSql)) {
                ps.setString(1, email);
                ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        c.rollback();
                        return 0;
                    }
                    codeId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(bumpSql)) {
                ps.setInt(1, codeId);
                ps.executeUpdate();
            }
            int total;
            try (PreparedStatement ps = c.prepareStatement(countSql)) {
                ps.setInt(1, codeId);
                try (ResultSet rs = ps.executeQuery()) {
                    total = rs.next() ? rs.getInt(1) : 0;
                }
            }
            c.commit();
            return total;
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("recording failed reset attempt", e);
        }
    }

    public void markUsed(int codeId) {
        String sql = "UPDATE password_reset_codes SET used_at = ? WHERE code_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, codeId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("marking password reset code used", e);
        }
    }

    /** Atomically claims an unexpired, unused code for a reset transaction. */
    public boolean claimForUse(Connection c, int codeId) throws SQLException {
        String sql = "UPDATE password_reset_codes SET used_at = ? "
                + "WHERE code_id = ? AND used_at IS NULL AND expires_at > ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            ps.setTimestamp(1, now);
            ps.setInt(2, codeId);
            ps.setTimestamp(3, now);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Revokes every outstanding code for an email (e.g. on a new request).
     */
    public void invalidateForEmail(String email) {
        String sql = "UPDATE password_reset_codes SET used_at = ? WHERE email = ? AND used_at IS NULL";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            ps.setString(2, email);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("revoking password reset codes", e);
        }
    }
}
