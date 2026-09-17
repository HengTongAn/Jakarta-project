package com.example.computer_store.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Central JDBC connection factory.
 * Reads connection settings from environment variables first, then falls back to db.properties file.
 * Environment variables: DB_URL, DB_USERNAME, DB_PASSWORD
 */
public final class DBConnection {

    private static final String PROPERTIES_FILE = "/db.properties";
    private static final Properties PROPERTIES;

    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    static {
        // Load properties file once
        PROPERTIES = new Properties();
        try (InputStream in = DBConnection.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (in != null) {
                PROPERTIES.load(in);
            }
        } catch (Exception e) {
            // Properties file is optional
        }
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(
                    "Failed to initialise JDBC driver: " + e.getMessage());
        }
        
        // Prioritize environment variables over properties file
        URL = getEnvOrProperty("DB_URL", "db.url", null);
        USERNAME = getEnvOrProperty("DB_USERNAME", "db.username", null);
        PASSWORD = getEnvOrProperty("DB_PASSWORD", "db.password", null);
    }

    private DBConnection() {
    }

    /**
     * Gets value from environment variable or falls back to properties file.
     */
    private static String getEnvOrProperty(String envVar, String propKey, String defaultValue) {
        // Check environment variable first
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        
        // Check system properties second
        String sysProp = System.getProperty(propKey);
        if (sysProp != null && !sysProp.trim().isEmpty()) {
            return sysProp.trim();
        }
        
        // Fall back to properties file
        String propValue = PROPERTIES.getProperty(propKey);
        if (propValue != null && !propValue.trim().isEmpty()) {
            return propValue.trim();
        }
        
        return defaultValue;
    }

    public static Connection getConnection() throws SQLException {
        if (URL == null || USERNAME == null || PASSWORD == null) {
            throw new SQLException("Database credentials are not configured. Set DB_URL, DB_USERNAME, and DB_PASSWORD.");
        }
        return java.sql.DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }
}
