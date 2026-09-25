package com.example.computer_store.core.repository;

import com.example.computer_store.infrastructure.cache.CacheManager;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.infrastructure.persistence.DBConnection;
import com.example.computer_store.util.web.ErrorHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class UserRepository {

    private static final String SELECT_COLUMNS =
            "user_id, username, password_hash, full_name, email, avatar_url, role, last_active_at, created_at, deleted_at";

    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getInt("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setRole(User.Role.valueOf(rs.getString("role")));
        u.setLastActiveAt(rs.getTimestamp("last_active_at"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        u.setAvatarUrl(rs.getString("avatar_url"));
        u.setDeletedAt(rs.getTimestamp("deleted_at"));
        return u;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    public User findByUsername(String username) {
        return findCached("user:username:" + username, () -> findUserByUsername(username));
    }

    private User findUserByUsername(String username) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE username = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding user by username", e);
        }
        return null;
    }

    public User findByEmail(String email) {
        return findCached("user:email:" + email, () -> findUserByEmail(email));
    }

    private User findUserByEmail(String email) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE email = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding user by email", e);
        }
        return null;
    }

    public User findById(int userId) {
        return findCached("user:id:" + userId, () -> findUserById(userId));
    }

    private User findUserById(int userId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE user_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding user by id", e);
        }
        return null;
    }

    /**
     * Returns the cached user row or loads it, always handing callers a
     * defensive copy: several callers mutate the returned object (e.g. the
     * login flow clears the password hash before stashing the user in the
     * session), and the cache entry must never be corrupted by that.
     * <p>
     * Cache entries expire via the USER_CACHE TTL and are invalidated on
     * every user write (see the invalidation calls below), so the
     * session-refresh role guarantee (see SessionUserRefreshFilter) still
     * holds within one JVM.
     */
    private User findCached(String key, Supplier<User> loader) {
        if (!CacheManager.isCacheEnabled()) {
            return loader.get();
        }
        User cached = (User) CacheManager.getUser(key);
        if (cached != null) {
            return copyOf(cached);
        }
        User loaded = loader.get();
        if (loaded != null) {
            CacheManager.putUser(key, loaded);
            return copyOf(loaded);
        }
        return null;
    }

    private static User copyOf(User source) {
        User copy = new User();
        copy.setUserId(source.getUserId());
        copy.setUsername(source.getUsername());
        copy.setPasswordHash(source.getPasswordHash());
        copy.setFullName(source.getFullName());
        copy.setEmail(source.getEmail());
        copy.setAvatarUrl(source.getAvatarUrl());
        copy.setRole(source.getRole());
        copy.setLastActiveAt(source.getLastActiveAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setDeletedAt(source.getDeletedAt());
        return copy;
    }

    public int create(User user) {
        String sql = "INSERT INTO users (username, password_hash, full_name, email, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getRole().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("creating user", e);
        } finally {
            // A new username/email is now available for lookups.
            if (CacheManager.isCacheEnabled()) {
                CacheManager.invalidateAllUsers();
            }
        }
    }

    public void update(User user) {
        String sql = "UPDATE users SET full_name = ?, email = ?, username = ?, role = ? WHERE user_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getUsername());
            ps.setString(4, user.getRole().name());
            ps.setInt(5, user.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating user", e);
        } finally {
            // Role/email/username changes must be visible to the next lookup.
            if (CacheManager.isCacheEnabled()) {
                CacheManager.invalidateAllUsers();
            }
        }
    }

    public void updatePassword(int userId, String newHash) {
        try (Connection c = conn()) {
            updatePassword(c, userId, newHash);
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating password", e);
        } finally {
            if (CacheManager.isCacheEnabled()) {
                CacheManager.invalidateAllUsers();
            }
        }
    }

    /** Updates a password on the caller's transaction connection. */
    public void updatePassword(Connection c, int userId, String newHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public List<User> findAll() {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users ORDER BY created_at DESC, user_id DESC";
        List<User> users = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("listing all users", e);
        }
        return users;
    }

    public List<User> findByRole(User.Role role) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM users WHERE role = ? ORDER BY full_name";
        List<User> users = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("listing users by role", e);
        }
        return users;
    }

    public List<User> findAdmins() {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM users WHERE role IN ('ADMIN', 'SUPER_ADMIN') ORDER BY full_name";
        List<User> users = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("listing admin users", e);
        }
        return users;
    }

    public long countByRole(User.Role role) {
        String sql = "SELECT COUNT(*) FROM users WHERE role = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("counting users by role", e);
        }
        return 0;
    }

    public void updateAvatar(int userId, String avatarUrl) {
        String sql = "UPDATE users SET avatar_url = ? WHERE user_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, avatarUrl);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating avatar", e);
        } finally {
            if (CacheManager.isCacheEnabled()) {
                CacheManager.invalidateAllUsers();
            }
        }
    }

    // Note: updateLastActive intentionally does NOT invalidate the user cache;
    // it runs on every authenticated request (AuthenticationFilter) and only
    // touches last_active_at, which no authentication decision reads. The
    // cache TTL bounds its staleness.

    public void updateLastActive(int userId) {
        String sql = "UPDATE users SET last_active_at = NOW() WHERE user_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating user activity", e);
        }
    }
}
