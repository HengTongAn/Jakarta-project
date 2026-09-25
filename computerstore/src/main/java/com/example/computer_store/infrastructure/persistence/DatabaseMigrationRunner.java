package com.example.computer_store.infrastructure.persistence;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Database migration runner that executes SQL migration files in order.
 * <p>
 * This is a simple migration system for environments where Flyway/Liquibase
 * are not desired. Migrations are SQL files in {@code src/main/resources/db/migrations/migration_*.sql}
 * that are executed once per deployment.
 * <p>
 * <strong>Usage:</strong> Enable by setting system property
 * {@code computerstore.migration.autoRun=true} (not recommended for production
 * without careful consideration). Better to run migrations manually or via CI/CD.
 * <p>
 * <strong>Production recommendation:</strong> Run migrations manually during
 * deployment using a dedicated migration tool or script.
 */
public final class DatabaseMigrationRunner implements ServletContextListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private static final String MIGRATIONS_PATH = "db/migrations/";
    private static final String MIGRATION_TABLE = "schema_migrations";
    private static final String AUTO_RUN_PROPERTY = "computerstore.migration.autoRun";

    public DatabaseMigrationRunner() {
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        if (!Boolean.parseBoolean(System.getProperty(AUTO_RUN_PROPERTY, "false"))) {
            LOGGER.info("Auto-migration disabled. Set -D{}='true' to enable (not recommended for production).", AUTO_RUN_PROPERTY);
            return;
        }

        LOGGER.warn("Auto-migration ENABLED. Running pending migrations...");
        try {
            runMigrations();
        } catch (Exception e) {
            LOGGER.error("Migration failed - application may be in inconsistent state", e);
            throw new IllegalStateException("Database migration failed", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to do
    }

    /**
     * Runs all pending migrations. Can be called manually for testing.
     */
    public static void runMigrations() throws SQLException, IOException {
        try (Connection conn = DBConnection.getConnection()) {
            ensureMigrationTable(conn);
            List<String> applied = getAppliedMigrations(conn);
            List<String> allMigrations = discoverMigrations();

            for (String migration : allMigrations) {
                if (!applied.contains(migration)) {
                    LOGGER.info("Applying migration: {}", migration);
                    executeMigration(conn, migration);
                    recordMigration(conn, migration);
                    LOGGER.info("Migration applied successfully: {}", migration);
                }
            }
        }
    }

    private static void ensureMigrationTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "migration_name VARCHAR(255) NOT NULL UNIQUE,"
                + "applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private static List<String> getAppliedMigrations(Connection conn) throws SQLException {
        List<String> applied = new ArrayList<>();
        String sql = "SELECT migration_name FROM " + MIGRATION_TABLE + " ORDER BY applied_at";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                applied.add(rs.getString("migration_name"));
            }
        }
        return applied;
    }

    static List<String> discoverMigrations() throws IOException {
        // In a real app, you'd scan the classpath. Here we list known migrations.
        // ORDER MATTERS: this is the execution order. The names are also the
        // schema_migrations.migration_name keys, so a file must NEVER be renamed
        // (only moved) once it has shipped - a rename would re-run an applied
        // migration on existing databases. DatabaseMigrationRunnerTest keeps this
        // set in sync with the files in db/migrations/.
        return Arrays.asList(
                "migration_add_soft_delete.sql",
                "migration_add_image_url.sql",
                "migration_add_audit_archive.sql",
                "migration_add_audit_correlation.sql",
                "migration_add_password_reset_tokens.sql",
                "migration_add_super_admin_role.sql",
                "migration_add_2fa.sql",
                "migration_add_order_lifecycle.sql",
                "migration_add_indexes.sql",
                "migration_add_product_details.sql",
                "migration_performance_indexes.sql",
                "migration_add_reviews.sql",
                "migration_add_trending_index.sql",
                "migration_advanced_performance_indexes.sql"
        );
    }

    private static void executeMigration(Connection conn, String migrationName) throws SQLException, IOException {
        String resourcePath = MIGRATIONS_PATH + migrationName;
        try (InputStream in = DatabaseMigrationRunner.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Migration file not found: " + resourcePath);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Parse SQL statements more carefully - handle comments and semicolons in comments
            List<String> statements = parseSqlStatements(sql);
            try (Statement stmt = conn.createStatement()) {
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        try {
                            stmt.execute(trimmed);
                        } catch (SQLException e) {
                            // Handle common "already exists" errors gracefully
                            String msg = e.getMessage().toLowerCase();
                            int errorCode = e.getErrorCode();
                            // MySQL error codes: 1060 = Duplicate column name, 1061 = Duplicate key, 
                            // 1050 = Table already exists, 1062 = Duplicate entry
                            if (errorCode == 1060 || errorCode == 1061 || errorCode == 1050 || errorCode == 1062
                                    || msg.contains("duplicate column") || msg.contains("already exists") 
                                    || msg.contains("duplicate key") || msg.contains("duplicate entry")) {
                                LOGGER.warn("Migration {} skipped (already applied): {}", migrationName, e.getMessage());
                            } else {
                                throw e;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Parses SQL file into individual statements.
     * Handles comments by removing them before splitting on semicolons.
     */
    private static List<String> parseSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        
        // First, remove single-line comments
        StringBuilder noComments = new StringBuilder();
        boolean inMultiLineComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';
            
            // Handle quotes
            if (!inMultiLineComment) {
                if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote;
                } else if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote;
                }
            }
            
            // Handle multi-line comments
            if (!inSingleQuote && !inDoubleQuote) {
                if (!inMultiLineComment && c == '/' && next == '*') {
                    inMultiLineComment = true;
                    i++; // skip next char
                    continue;
                } else if (inMultiLineComment && c == '*' && next == '/') {
                    inMultiLineComment = false;
                    i++; // skip next char
                    continue;
                }
            }
            
            // Handle single-line comments
            if (!inMultiLineComment && !inSingleQuote && !inDoubleQuote) {
                if (c == '-' && next == '-') {
                    // Skip until end of line
                    while (i < sql.length() && sql.charAt(i) != '\n' && sql.charAt(i) != '\r') {
                        i++;
                    }
                    continue;
                }
            }
            
            if (!inMultiLineComment) {
                noComments.append(c);
            }
        }
        
        // Now split on semicolons
        String[] parts = noComments.toString().split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                statements.add(trimmed);
            }
        }
        
        return statements;
    }

    private static void recordMigration(Connection conn, String migrationName) throws SQLException {
        String sql = "INSERT INTO " + MIGRATION_TABLE + " (migration_name) VALUES (?)";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, migrationName);
            ps.executeUpdate();
        }
    }

    /**
     * Checks if migrations table exists and returns its status.
     * Useful for health checks.
     */
    public static MigrationStatus getMigrationStatus() {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet tables = meta.getTables(null, null, MIGRATION_TABLE, new String[]{"TABLE"})) {
                if (!tables.next()) {
                    return new MigrationStatus(false, List.of(), "Migration table does not exist");
                }
            }
            List<String> applied = getAppliedMigrations(conn);
            List<String> all = discoverMigrations();
            List<String> pending = new ArrayList<>(all);
            pending.removeAll(applied);
            return new MigrationStatus(true, pending, applied.size() + " applied, " + pending.size() + " pending");
        } catch (Exception e) {
            return new MigrationStatus(false, List.of(), "Error checking status: " + e.getMessage());
        }
    }

    public record MigrationStatus(boolean tableExists, List<String> pendingMigrations, String summary) {}
}
