package com.hengtongan.computerstore.infrastructure.persistence;

import com.hengtongan.computerstore.core.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Map;

/**
 * Central JDBC connection factory backed by a HikariCP pool.
 * Connection settings are resolved by {@link AppConfig} with the documented
 * precedence (environment variable → system property → {@code config/db.properties}
 * → default): DB_URL, DB_USERNAME, DB_PASSWORD for the connection itself,
 * DB_POOL_MAX / DB_POOL_MIN for the pool size.
 *
 * <p>Every {@link #getConnection()} call now borrows a pooled connection
 * instead of opening a fresh DriverManager connection, which is what made
 * query-heavy pages (admin orders/inventory/users/dashboard) slow. The pool
 * keeps a handful of connections warm and reuses them across requests.
 */
public final class DBConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBConnection.class);

    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    private static volatile HikariDataSource dataSource;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(
                    "Failed to initialise JDBC driver: " + e.getMessage());
        }

        // Prioritize environment variables over system properties over config files
        URL = AppConfig.get("DB_URL", "db.url", null);
        USERNAME = AppConfig.get("DB_USERNAME", "db.username", null);
        PASSWORD = AppConfig.get("DB_PASSWORD", "db.password", null);
    }

    private DBConnection() {
    }

    public static Connection getConnection() throws SQLException {
        if (URL == null || USERNAME == null || PASSWORD == null) {
            throw new SQLException("Database credentials are not configured. Set DB_URL, DB_USERNAME, and DB_PASSWORD.");
        }
        if (dataSource == null) {
            synchronized (DBConnection.class) {
                if (dataSource == null) {
                    dataSource = buildPool();
                }
            }
        }
        try {
            return dataSource.getConnection();
        } catch (SQLTransientConnectionException e) {
            // HikariCP throws this when the pool is exhausted (no connection
            // became available within connectionTimeout). Surface the pool
            // state so ops can see it in the logs without probing /health.
            Map<String, Object> stats = getPoolStats();
            LOGGER.error("Connection pool exhausted: active={} idle={} waiting={} total={} ({})",
                    stats.get("active"), stats.get("idle"), stats.get("waiting"),
                    stats.get("total"), e.getMessage());
            throw e;
        }
    }

    /**
     * Current HikariCP pool statistics, or an empty map when the pool has not
     * been initialised yet. Exposed for the /health endpoint so operators can
     * see pool exhaustion at a glance.
     */
    public static Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        HikariDataSource ds = dataSource;
        if (ds == null) {
            return stats;
        }
        com.zaxxer.hikari.HikariPoolMXBean pool = ds.getHikariPoolMXBean();
        stats.put("active", pool.getActiveConnections());
        stats.put("idle", pool.getIdleConnections());
        stats.put("waiting", pool.getThreadsAwaitingConnection());
        stats.put("total", pool.getTotalConnections());
        com.zaxxer.hikari.HikariConfigMXBean config = ds.getHikariConfigMXBean();
        stats.put("max", config.getMaximumPoolSize());
        stats.put("min", config.getMinimumIdle());
        return stats;
    }

    /**
     * Closes the connection pool. Called by the application listener when the
     * webapp is shut down, so pooled connections are released instead of
     * lingering until the JVM exits.
     */
    public static void shutdown() {
        synchronized (DBConnection.class) {
            HikariDataSource ds = dataSource;
            dataSource = null;
            if (ds != null) {
                ds.close();
            }
            // Release the MySQL driver's abandoned-connection cleanup thread so
            // a redeploy does not warn about a leaked thread / unregistered JDBC driver.
            for (java.sql.Driver driver : java.util.Collections.list(java.sql.DriverManager.getDrivers())) {
                try {
                    java.sql.DriverManager.deregisterDriver(driver);
                } catch (java.sql.SQLException ignored) {
                    // Already deregistered or not owned by this webapp.
                }
            }
            try {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.uncheckedShutdown();
            } catch (Throwable ignored) {
                // Thread already shut down (or driver not the MySQL implementation).
            }
        }
    }

    private static HikariDataSource buildPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(URL);
        config.setUsername(USERNAME);
        config.setPassword(PASSWORD);
        config.setPoolName("computerstore");

        // Performance-optimized pool configuration for admin and user traffic
        String poolMax = AppConfig.get("DB_POOL_MAX", "db.pool.max", "50");
        String poolMin = AppConfig.get("DB_POOL_MIN", "db.pool.min", "10");
        int maxPool = 50;
        int minPool = 10;
        try {
            maxPool = Math.max(5, Integer.parseInt(poolMax));
            minPool = Math.min(maxPool, Math.max(2, Integer.parseInt(poolMin)));
        } catch (NumberFormatException e) {
            // Not a number -> keep the default, but say so: silent misconfiguration
            // is the failure mode the caller cannot diagnose.
            LOGGER.warn("Invalid DB pool size in config (DB_POOL_MAX='{}', DB_POOL_MIN='{}'); "
                    + "using defaults maxPool={}, minPool={}", poolMax, poolMin, maxPool, minPool);
        }
        // Sanity bounds: refuse absurd values instead of configuring them.
        if (maxPool > 200) {
            LOGGER.warn("Configured DB_POOL_MAX={} exceeds the supported maximum of 200; clamping to 200.", maxPool);
            maxPool = 200;
        }
        if (minPool > maxPool) {
            LOGGER.warn("Configured DB_POOL_MIN={} exceeds maxPool={}; clamping to maxPool.", minPool, maxPool);
            minPool = maxPool;
        }

        config.setMaximumPoolSize(maxPool);
        config.setMinimumIdle(minPool);

        // Responsive pool behaviour with headroom for burst traffic: this is
        // Hikari's *pool wait* timeout, so 1s produced 500s whenever a burst
        // momentarily exhausted the pool. 3000ms keeps failures fast without
        // flapping under load; validation stays cheap (500ms).
        config.setConnectionTimeout(3000);
        config.setValidationTimeout(500);
        config.setIdleTimeout(600000);           // Increased to 10 minutes
        config.setMaxLifetime(1800000);          // Increased to 30 minutes

        // Cross-check out-of-pool connections: any lease held longer than 60s
        // is logged with its stack trace, so a leaked connection surfaces in
        // production instead of showing up only as eventual pool exhaustion.
        config.setLeakDetectionThreshold(60_000);

        // MySQL-specific performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        config.addDataSourceProperty("netTimeoutForStreamingResults", "0");
        // TLS policy belongs to DB_URL (for example sslMode=VERIFY_IDENTITY in
        // production). Never override it here with an insecure fallback.
        config.addDataSourceProperty("serverTimezone", "UTC");
        config.addDataSourceProperty("characterEncoding", "UTF-8");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Missing MySQL JDBC driver", e);
        }
        return new HikariDataSource(config);
    }
}
