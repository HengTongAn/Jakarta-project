package com.hengtongan.computerstore.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the configuration precedence
 * (environment variable → system property → bundled file → default) without
 * touching the process environment. The environment arm is exercised through
 * the package-private {@link com.hengtongan.computerstore.core.config.AppConfig#resolve} so no test mutates
 * {@link System#getenv()}.
 */
class AppConfigTest {

    private static final String TEST_KEY = "computerstore.test.never-used.key";

    @AfterEach
    void clearTestSystemProperty() {
        System.clearProperty(TEST_KEY);
    }

    @Test
    void resolvePrecedenceEnvOverSystemPropertyOverFileOverDefault() {
        assertEquals("env", com.hengtongan.computerstore.core.config.AppConfig.resolve("env", "sys", "file", "default"));
        assertEquals("env", com.hengtongan.computerstore.core.config.AppConfig.resolve("env", "sys", null, "default"));
        assertEquals("sys", com.hengtongan.computerstore.core.config.AppConfig.resolve(null, "sys", "file", "default"));
        assertEquals("file", com.hengtongan.computerstore.core.config.AppConfig.resolve(null, null, "file", "default"));
        assertEquals("default", com.hengtongan.computerstore.core.config.AppConfig.resolve(null, null, null, "default"));
    }

    @Test
    void resolveSkipsBlankValuesAndTrims() {
        assertEquals("sys", com.hengtongan.computerstore.core.config.AppConfig.resolve("   ", " sys ", "file", "default"));
        assertEquals("file", com.hengtongan.computerstore.core.config.AppConfig.resolve(null, "", "  file  ", "default"));
        assertEquals("default", com.hengtongan.computerstore.core.config.AppConfig.resolve(null, null, " ", "default"));
    }

    @Test
    void getHonoursSystemPropertyThenFallsBackToDefault() {
        // No env var for this key, never present in the bundled files -> default.
        assertEquals("default", com.hengtongan.computerstore.core.config.AppConfig.get(null, TEST_KEY, "default"));

        System.setProperty(TEST_KEY, "from-system-property");
        assertEquals("from-system-property", com.hengtongan.computerstore.core.config.AppConfig.get(null, TEST_KEY, "default"));
    }
}