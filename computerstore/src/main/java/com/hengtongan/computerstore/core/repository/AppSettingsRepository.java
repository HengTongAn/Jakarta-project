package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Read/write access to the {@code app_settings} key/value table.
 * <p>
 * Deliberately untyped: the table is a small settings bag, and the alternative
 * is a migration every time the site grows a new tunable. Callers that know the
 * shape of the value they are reading (see
 * {@link com.hengtongan.computerstore.core.service.SupportChannelService}) are
 * responsible for validating it.
 */
public class AppSettingsRepository {

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    /**
     * Every setting, as a mutable map. Returns an empty map rather than throwing
     * when the table is absent, so a store that has not run the migration yet
     * renders with defaults instead of failing the whole page.
     */
    public Map<String, String> findAll() {
        String sql = "SELECT setting_key, setting_value FROM app_settings";
        Map<String, String> settings = new HashMap<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        } catch (SQLException e) {
            return new HashMap<>();
        }
        return settings;
    }

    /** Inserts the setting, or overwrites the value if the key already exists. */
    public void save(String key, String value) {
        String sql = """
                INSERT INTO app_settings (setting_key, setting_value)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
                """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving app setting " + key, e);
        }
    }
}
