package com.hengtongan.computerstore.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Shared, lazy JDBC metadata helpers used by DAOs that must serve both
 * up-to-date schemas and legacy ones that predate a migration.
 *
 * <p>Callers keep their own caching layer (an optional-column list, a
 * column-list flag, ...); this utility just performs the one-shot metadata
 * probe on a fresh connection so the check always reads the live schema.
 */
public final class SchemaUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchemaUtil.class);

    private SchemaUtil() {
    }

    /**
     * True when {@code table} currently has a column named {@code column}.
     * Returns false when the metadata probe fails, letting DAOs degrade to
     * their pre-migration behaviour instead of failing - but the failure is
     * logged at WARN so a silently missed migration is not invisible.
     */
    public static boolean hasColumn(String table, String column) {
        try (Connection c = DBConnection.getConnection();
             ResultSet columns = c.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        } catch (SQLException e) {
            LOGGER.warn("Schema detection failed for {}.{} (assuming column absent): {}",
                    table, column, e.getMessage());
            return false;
        }
    }
}