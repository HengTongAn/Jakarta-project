package com.hengtongan.computerstore.core.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Central configuration gateway.
 * <p>
 * Every setting is resolved with the documented precedence:
 * <strong>environment variable → system property → bundled properties file →
 * built-in default</strong>. Pass {@code null} for a layer to skip it.
 * <p>
 * The bundled property file ({@code src/main/resources/config/db.properties})
 * is a git-ignored local secret, loaded once at class-load time. A missing or
 * unreadable file simply falls through to defaults - configuration is never
 * fatal here.
 */
public final class AppConfig {

    private static final List<String> RESOURCES = List.of("/config/db.properties");

    private static final Properties BUNDLED = new Properties();
    private static final Properties DOT_ENV = new Properties();

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
        loadDotEnv();
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
        String envValue = envVar == null ? null : System.getenv(envVar);
        if (envValue == null && envVar != null) {
            envValue = DOT_ENV.getProperty(envVar);
        }

        String systemValue = propertyKey == null ? null : System.getProperty(propertyKey);
        String bundledValue = propertyKey == null ? null : BUNDLED.getProperty(propertyKey);
        if ((bundledValue == null || bundledValue.isBlank()) && propertyKey != null) {
            bundledValue = DOT_ENV.getProperty(propertyKey);
        }

        return resolve(
                envValue,
                systemValue,
                bundledValue,
                defaultValue);
    }

    private static void loadDotEnv() {
        for (Path file : findDotEnvCandidates()) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                        continue;
                    }
                    if (trimmed.startsWith("export ")) {
                        trimmed = trimmed.substring("export ".length()).trim();
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();
                    if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    DOT_ENV.setProperty(key, value);
                }
            } catch (IOException ignored) {
                // .env is optional; a missing or unreadable file is not fatal.
            }
        }
    }

    private static List<Path> findDotEnvCandidates() {
        Set<Path> candidates = new LinkedHashSet<>();
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            candidates.add(Path.of(userDir, ".env"));
            candidates.add(Path.of(userDir).getParent() == null ? null : Path.of(userDir).getParent().resolve(".env"));
        }
        String[] baseCandidates = {
                System.getenv("CATALINA_BASE"),
                System.getenv("CATALINA_HOME"),
                System.getProperty("catalina.base"),
                System.getProperty("catalina.home")
        };
        for (String base : baseCandidates) {
            if (base != null && !base.isBlank()) {
                candidates.add(Path.of(base).resolve(".env"));
            }
        }
        candidates.remove(null);
        return new ArrayList<>(candidates);
    }

    /**
     * Pure precedence function, package-private so unit tests can exercise every
     * arm without mutating the process environment.
     */
    static String resolve(String envValue, String systemPropertyValue,
            String fileValue, String defaultValue) {
        String[] candidates = { envValue, systemPropertyValue, fileValue };
        for (String value : candidates) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return defaultValue;
    }
}
