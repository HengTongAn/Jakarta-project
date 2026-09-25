package com.hengtongan.computerstore.web.controller.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResetPasswordServletTest {

    @BeforeEach
    void setUp() {
        ResetPasswordServlet.ProbeThrottle.clearAll();
    }

    @Test
    void testAllowsUpToMaxProbesPerWindow() {
        for (int i = 0; i < 20; i++) {
            assertTrue(ResetPasswordServlet.ProbeThrottle.allow("203.0.113.1"),
                    "attempt " + (i + 1) + " should be inside the budget");
        }
        // The 21st probe within the same minute must be refused.
        assertFalse(ResetPasswordServlet.ProbeThrottle.allow("203.0.113.1"));
    }

    @Test
    void testIPsAreCountedSeparately() {
        for (int i = 0; i < 25; i++) {
            ResetPasswordServlet.ProbeThrottle.allow("198.51.100.10");
        }
        assertFalse(ResetPasswordServlet.ProbeThrottle.allow("198.51.100.10"));
        assertTrue(ResetPasswordServlet.ProbeThrottle.allow("198.51.100.11"));
    }
}