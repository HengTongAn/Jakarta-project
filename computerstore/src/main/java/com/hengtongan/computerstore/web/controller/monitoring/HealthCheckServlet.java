package com.hengtongan.computerstore.web.controller.monitoring;

import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

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
 * (`/health`). Returns 200 OK when healthy, 503 Service Unavailable when the
 * database is unreachable.
 *
 * <h2>Why anonymous callers get less</h2>
 *
 * <p>A load-balancer probe cannot hold a session, so {@code /health} has to
 * answer anonymously -- but this endpoint previously published its whole
 * breakdown to anyone who asked: connection-pool occupancy, free disk space,
 * and the raw {@link SQLException} message on failure, which names the database
 * host. None of that is needed to decide whether to route traffic, and all of
 * it is reconnaissance for whoever is scanning.</p>
 *
 * <p>So there are two responses. Anonymous callers get the liveness answer only
 * ({@code {"status":"UP"}} plus the HTTP code), which is what a probe needs. An
 * admin session, or a configured {@code computerstore.health.token} header, gets
 * the full breakdown. Exception text is never echoed either way; it goes to the
 * log where it belongs.</p>
 */
@WebServlet("/health")
public class HealthCheckServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckServlet.class);

    /** Shared secret for probes that cannot hold a session but should see detail. */
    private static final String TOKEN_PROPERTY = "computerstore.health.token";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        boolean healthy = true;

        // Always run the DB check: the status code is the probe's signal, and
        // that must not depend on who is asking.
        String dbStatus = checkDatabase();
        healthy &= "UP".equals(dbStatus);
        String diskStatus = checkDiskSpace();
        healthy &= "UP".equals(diskStatus);

        response.setContentType("application/json;charset=UTF-8");
        if (healthy) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"status\": \"").append(healthy ? "UP" : "DOWN").append('"');

        if (!maySeeDetail(request)) {
            json.append(", \"detail\": \"available to administrators\"");
            json.append('}');
        } else {
            json.append(", \"timestamp\": \"").append(Instant.now()).append("\",\n  \"checks\": {\n");
            json.append("    \"database\": \"").append(dbStatus).append("\",\n");

            Map<String, Object> pool = DBConnection.getPoolStats();
            json.append("    \"dbPool\": {");
            if (pool.isEmpty()) {
                json.append("\"status\": \"not-initialised\"");
            } else {
                json.append("\"active\": ").append(pool.get("active")).append(",")
                        .append("\"idle\": ").append(pool.get("idle")).append(",")
                        .append("\"waiting\": ").append(pool.get("waiting")).append(",")
                        .append("\"total\": ").append(pool.get("total"));
            }
            json.append("},\n");
            json.append("    \"diskSpace\": \"").append(diskStatus).append("\"\n");
            json.append("  }\n}");
        }

        try (PrintWriter writer = response.getWriter()) {
            writer.write(json.toString());
        }
    }

    /**
     * Whether this caller may read the detailed breakdown: an admin session, or
     * a request carrying the configured probe token.
     *
     * <p>The token is compared in constant time and is only consulted when it
     * was actually configured -- an unset property must not degrade into "any
     * request that sends an empty header is an admin".</p>
     */
    private boolean maySeeDetail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object user = session.getAttribute("user");
            if (user instanceof User u && u.getRole() != User.Role.CUSTOMER) {
                return true;
            }
        }
        String configured = System.getProperty(TOKEN_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            return false;
        }
        String supplied = request.getHeader("X-Health-Token");
        if (supplied == null || supplied.length() != configured.length()) {
            return false;
        }
        // Constant-time compare: a byte-wise early exit leaks the token length
        // prefix by prefix to anything that can time the endpoint.
        int diff = 0;
        for (int i = 0; i < configured.length(); i++) {
            diff |= configured.charAt(i) ^ supplied.charAt(i);
        }
        return diff == 0;
    }

    /**
     * @return {@code "UP"}, or {@code "DOWN"} with a short reason code. The
     *         underlying exception text is logged, never returned: it names the
     *         database host and user, which is exactly what an unauthenticated
     *         caller should not learn.
     */
    private String checkDatabase() {
        try (Connection conn = DBConnection.getConnection()) {
            if (conn.isValid(2000)) {
                return "UP";
            }
            return "DOWN - connection not valid";
        } catch (SQLException e) {
            LOGGER.warn("Health check database connection failed", e);
            return "DOWN - connection error";
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
