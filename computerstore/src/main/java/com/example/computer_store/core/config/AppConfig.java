package com.example.computer_store.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * Central configuration gateway.
 * <p>
 * Every setting is resolved with the documented precedence:
 * <strong>environment variable → system property → bundled properties file →
 * built-in default</strong>. Pass {@code null} for a layer to skip it (for
 * example the {@code mail.*} keys have no environment variable).
 * <p>
 * The bundled property files ({@code src/main/resources/config/db.properties},
 * {@code config/mail.properties}) are git-ignored local secrets, loaded once
 * at class-load time and merged into a single table ({@code db.*} and
 * {@code mail.*} keys do not collide). A missing or unreadable file simply
 * falls through to defaults - configuration is never fatal here.
 */
public final class AppConfig {

    private static final List<String> RESOURCES =
            List.of("/config/db.properties", "/config/mail.properties");

    private static final Properties BUNDLED = new Properties();

    static {
        for (String resource : RESOURCES) {
            try (InputStream in = AppConfig.class.getResourceAsStream(resource)) {
                if (in != null) {
                    BUNDLED.load(in);
                }
            } catch (IOException ignored) {
                // Optional config file: missing/broken bundles fall back to defaults.
                // The application logs loudly later when a required setting is absent.
            }
        }
    }

    private AppConfig() {
    }

    /**
     * Resolves a setting with the documented precedence
     * {@code envVar → propertyKey → bundled file → defaultValue}. Pass
     * {@code null} for {@code envVar} and/or {@code propertyKey} to skip that
     * layer entirely.
     */
    public static String get(String envVar, String propertyKey, String defaultValue) {
        return resolve(
                envVar == null ? null : System.getenv(envVar),
                propertyKey == null ? null : System.getProperty(propertyKey),
                propertyKey == null ? null : BUNDLED.getProperty(propertyKey),
                defaultValue);
    }

    /**
     * Pure precedence function, package-private so unit tests can exercise every
     * arm without mutating the process environment.
     */
    static String resolve(String envValue, String systemPropertyValue,
                          String fileValue, String defaultValue) {
        String[] candidates = {envValue, systemPropertyValue, fileValue};
        for (String value : candidates) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return defaultValue;
    }
}