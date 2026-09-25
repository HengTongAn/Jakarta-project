package com.hengtongan.computerstore.web.controller.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckServletTest {

    @Test
    void testServletInitialization() {
        HealthCheckServlet servlet = new HealthCheckServlet();
        assertNotNull(servlet);
    }

    @Test
    void testGetMethodExists() throws Exception {
        assertDoesNotThrow(() -> {
            var method = HealthCheckServlet.class.getDeclaredMethod("doGet", 
                    jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
            assertNotNull(method);
        });
    }

    @Test
    void testCheckDatabaseMethodExists() throws Exception {
        assertDoesNotThrow(() -> {
            var method = HealthCheckServlet.class.getDeclaredMethod("checkDatabase");
            assertNotNull(method);
        });
    }

    @Test
    void testCheckDiskSpaceMethodExists() throws Exception {
        assertDoesNotThrow(() -> {
            var method = HealthCheckServlet.class.getDeclaredMethod("checkDiskSpace");
            assertNotNull(method);
        });
    }
}