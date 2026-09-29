package com.hengtongan.computerstore.web.controller.monitoring;

import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Health check endpoint used by load-balancer probes, monitoring and ops
 * (`/health`). Returns 200 OK with a JSON check breakdown when healthy,
 * 503 Service Unavailable when the database is unreachable.
 */
@WebServlet("/health")
public class HealthCheckServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        boolean healthy = true;
        StringBuilder details = new StringBuilder();
        details.append("{\n  \"status\": \"UP\",\n  \"timestamp\": \"").append(Instant.now()).append("\",\n  \"checks\": {\n");

        // Database check
        String dbStatus = checkDatabase();
        healthy &= "UP".equals(dbStatus);
        details.append("    \"database\": \"").append(dbStatus).append("\",\n");

        // Connection pool metrics
        Map<String, Object> pool = DBConnection.getPoolStats();
        details.append("    \"dbPool\": {");
        if (pool.isEmpty()) {
            details.append("\"status\": \"not-initialised\"");
        } else {
            details.append("\"active\": ").append(pool.get("active")).append(",")
                    .append("\"idle\": ").append(pool.get("idle")).append(",")
                    .append("\"waiting\": ").append(pool.get("waiting")).append(",")
                    .append("\"total\": ").append(pool.get("total"));
        }
        details.append("},\n");

        // Disk space check (basic)
        String diskStatus = checkDiskSpace();
        healthy &= "UP".equals(diskStatus);
        details.append("    \"diskSpace\": \"").append(diskStatus).append("\"\n");

        details.append("  }\n}");

        response.setContentType("application/json;charset=UTF-8");
        if (healthy) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            details = new StringBuilder(details.toString().replace("\"status\": \"UP\"", "\"status\": \"DOWN\""));
        }

        try (PrintWriter writer = response.getWriter()) {
            writer.write(details.toString());
        }
    }

    private String checkDatabase() {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn.isValid(2000)) {
                return "UP";
            }
            return "DOWN - connection not valid";
        } catch (SQLException e) {
            LOGGER.warn("Health check database connection failed", e);
            return "DOWN - " + e.getMessage();
        }
    }

    private String checkDiskSpace() {
        java.io.File root = new java.io.File("/");
        long freeSpace = root.getFreeSpace();
        long totalSpace = root.getTotalSpace();
        double freePercent = (double) freeSpace / totalSpace * 100;
        if (freePercent < 5.0) {
            return "DOWN - only " + String.format("%.1f", freePercent) + "% free";
        }
        return "UP";
    }
}
