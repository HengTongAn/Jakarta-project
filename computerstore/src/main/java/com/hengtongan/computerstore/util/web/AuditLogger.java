package com.hengtongan.computerstore.util.web;

import com.hengtongan.computerstore.core.repository.AuditLogRepository;
import com.hengtongan.computerstore.core.domain.entity.AuditLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Audit logging system for security-sensitive operations.
 * Logs authentication events, admin actions, and other critical operations.
 * Every event is mirrored to the database (audit_logs table) for the
 * admin History page. Database failures never break the application:
 * if the write fails we just log a warning.
 */
public final class AuditLogger {

    private static final Logger AUDIT_LOGGER = LoggerFactory.getLogger("AUDIT");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Per-request correlation data (set by RequestAuditContextFilter). Every
     * log call in the same thread picks it up automatically, so records can be
     * traced back to a session and a single HTTP request - the way real SIEM /
     * observability tools correlate events.
     */
    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    public static final class RequestContext {
        private final String requestId;
        private final String sessionId;
        private final String clientIp;

        public RequestContext(String requestId, String sessionId, String clientIp) {
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.clientIp = clientIp;
        }

        public String getRequestId() { return requestId; }
        public String getSessionId() { return sessionId; }
        public String getClientIp() { return clientIp; }
    }

    public static void bindContext(RequestContext context) {
        CONTEXT.set(context);
        if (context != null) {
            if (context.getRequestId() != null) {
                MDC.put("correlationId", context.getRequestId());
            }
            if (context.getSessionId() != null) {
                MDC.put("sessionId", context.getSessionId());
            }
        }
    }

    public static void clearContext() {
        CONTEXT.remove();
        MDC.remove("correlationId");
        MDC.remove("sessionId");
    }

    private static String requestId() {
        RequestContext ctx = CONTEXT.get();
        return ctx == null || ctx.getRequestId() == null ? null : ctx.getRequestId();
    }

    private static String sessionId() {
        RequestContext ctx = CONTEXT.get();
        return ctx == null || ctx.getSessionId() == null ? null : ctx.getSessionId();
    }

    private static String contextIp(String provided) {
        if (provided != null && !provided.isEmpty()) {
            return provided;
        }
        RequestContext ctx = CONTEXT.get();
        return ctx == null ? null : ctx.getClientIp();
    }

    /** Short, URL-safe request id used for correlation across events. */
    public static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private AuditLogger() {
    }

    /** Only trust reverse-proxy headers when explicitly configured to. */
    private static final boolean TRUST_FORWARDED_HEADERS = Boolean.parseBoolean(
            System.getProperty("computerstore.trust-forwarded-headers", "false"));

    /**
     * Extracts the real client IP from a request. X-Forwarded-For / X-Real-IP
     * are only honored when a reverse proxy has been explicitly configured to
     * sanitize them (system property {@code computerstore.trust-forwarded-headers});
     * otherwise a spoofing client could write arbitrary text into every audit row.
     */
    public static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        String ip = request.getRemoteAddr();
        if (TRUST_FORWARDED_HEADERS) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded == null || forwarded.isEmpty() || "unknown".equalsIgnoreCase(forwarded)) {
                forwarded = request.getHeader("X-Real-IP");
            }
            if (forwarded != null && !forwarded.isEmpty() && !"unknown".equalsIgnoreCase(forwarded)) {
                ip = forwarded;
            }
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * Logs an authentication event (login, logout, failed login, etc.).
     *
     * @param action The authentication action (e.g., "LOGIN_SUCCESS", "LOGIN_FAILED", "LOGOUT")
     * @param username The username involved (may be null for failed attempts)
     * @param ipAddress The IP address of the request
     * @param details Additional details about the event
     */
    public static void logAuthEvent(String action, String username, String ipAddress, String details) {
        String logMessage = String.format("[%s] AUTH: %s | User: %s | IP: %s | Details: %s",
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                action,
                username != null ? username : "UNKNOWN",
                ipAddress != null ? ipAddress : "UNKNOWN",
                details != null ? details : "N/A");

        if (action.contains("FAILED") || action.contains("INVALID")) {
            AUDIT_LOGGER.warn(logMessage);
        } else {
            AUDIT_LOGGER.info(logMessage);
        }

        persist(AuditLog.ActionType.AUTH, action, username, null, null, details, ipAddress);
    }

    /**
     * Logs an admin action (product creation, user management, etc.).
     *
     * @param action The admin action performed
     * @param adminUsername The username of the admin performing the action
     * @param targetResource The resource being acted upon (e.g., "product #123")
     * @param details Additional details about the action
     */
    public static void logAdminAction(String action, String adminUsername, String targetResource, String details) {
        String logMessage = String.format("[%s] ADMIN: %s | Admin: %s | Target: %s | Details: %s",
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                action,
                adminUsername != null ? adminUsername : "UNKNOWN",
                targetResource != null ? targetResource : "N/A",
                details != null ? details : "N/A");

        AUDIT_LOGGER.info(logMessage);

        persist(AuditLog.ActionType.ADMIN, action, adminUsername, null, targetResource, details, null);
    }

    /**
     * Logs a data modification event (order creation, inventory changes, etc.).
     *
     * @param action The data modification action
     * @param username The username of the user performing the action
     * @param resourceType The type of resource affected (e.g., "ORDER", "INVENTORY")
     * @param resourceId The ID of the affected resource
     * @param details Additional details about the modification
     */
    public static void logDataModification(String action, String username, String resourceType,
                                           String resourceId, String details) {
        String logMessage = String.format("[%s] DATA: %s | User: %s | Type: %s | ID: %s | Details: %s",
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                action,
                username != null ? username : "UNKNOWN",
                resourceType != null ? resourceType : "N/A",
                resourceId != null ? resourceId : "N/A",
                details != null ? details : "N/A");

        AUDIT_LOGGER.info(logMessage);

        persist(AuditLog.ActionType.DATA, action, username, resourceType, resourceId, details, null);
    }

    /**
     * Logs a security event (CSRF attempt, rate limit exceeded, etc.).
     *
     * @param action The security event type
     * @param ipAddress The IP address involved
     * @param details Additional details about the security event
     */
    public static void logSecurityEvent(String action, String ipAddress, String details) {
        String logMessage = String.format("[%s] SECURITY: %s | IP: %s | Details: %s",
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                action,
                ipAddress != null ? ipAddress : "UNKNOWN",
                details != null ? details : "N/A");

        AUDIT_LOGGER.warn(logMessage);

        persist(AuditLog.ActionType.SECURITY, action, null, null, null, details, ipAddress);
    }

    /**
     * Logs a system event (startup, shutdown, configuration changes, etc.).
     *
     * @param action The system event
     * @param details Additional details about the system event
     */
    public static void logSystemEvent(String action, String details) {
        String logMessage = String.format("[%s] SYSTEM: %s | Details: %s",
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                action,
                details != null ? details : "N/A");

        AUDIT_LOGGER.info(logMessage);

        persist(AuditLog.ActionType.SYSTEM, action, null, null, null, details, null);
    }

    /**
     * Logs an error event with security implications.
     *
     * @param action The error action
     * @param username The username involved (if applicable)
     * @param details Additional details about the error
     * @param throwable The exception that occurred (optional)
     */
    public static void logErrorEvent(String action, String username, String details, Throwable throwable) {
        String logMessage = String.format("[%s] ERROR: %s | User: %s | Details: %s",
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                action,
                username != null ? username : "UNKNOWN",
                details != null ? details : "N/A");

        if (throwable != null) {
            AUDIT_LOGGER.error(logMessage, throwable);
        } else {
            AUDIT_LOGGER.error(logMessage);
        }

        persist(AuditLog.ActionType.SECURITY, action, username, null, null, details, null);
    }

    /**
     * Writes a copy of the event to the audit_logs table. Called by every
     * public log method above. Never throws: the History page must not be
     * able to break real transactions, so a failed write is only a warning.
     */
    private static void persist(AuditLog.ActionType type, String action, String actor,
                                String resourceType, String resourceId, String details,
                                String ipAddress) {
        // Unit tests and offline tooling can retain normal application logs
        // without silently attempting a real production database connection.
        if (!Boolean.parseBoolean(System.getProperty("computerstore.audit.database.enabled", "true"))) {
            return;
        }
        try {
            AuditLog log = new AuditLog();
            log.setActionType(type);
            log.setActionName(truncate(action, 100));
            log.setActor(truncate(actor, 50));
            log.setResourceType(truncate(resourceType, 50));
            log.setResourceId(truncate(resourceId, 50));
            log.setDetails(truncate(details, 500));
            log.setIpAddress(truncate(contextIp(ipAddress), 45));
            log.setRequestId(truncate(requestId(), 64));
            log.setSessionId(truncate(sessionId(), 64));
            new AuditLogRepository().save(log);
        } catch (Exception e) {
            AUDIT_LOGGER.warn("Could not persist audit event (action={})", action, e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}