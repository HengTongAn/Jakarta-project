package com.example.computer_store.util;

import com.example.computer_store.dao.AuditLogDAO;
import com.example.computer_store.model.AuditLog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Audit logging system for security-sensitive operations.
 * Logs authentication events, admin actions, and other critical operations.
 * Every event is mirrored to the database (audit_logs table) for the
 * admin History page. Database failures never break the application:
 * if the write fails we just log a warning to the console.
 */
public final class AuditLogger {

    private static final Logger AUDIT_LOGGER = Logger.getLogger("AUDIT_LOGGER");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private AuditLogger() {
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
            AUDIT_LOGGER.log(Level.WARNING, logMessage);
        } else {
            AUDIT_LOGGER.log(Level.INFO, logMessage);
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
        
        AUDIT_LOGGER.log(Level.INFO, logMessage);

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
        
        AUDIT_LOGGER.log(Level.INFO, logMessage);

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
        
        AUDIT_LOGGER.log(Level.WARNING, logMessage);

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
        
        AUDIT_LOGGER.log(Level.INFO, logMessage);

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
            AUDIT_LOGGER.log(Level.SEVERE, logMessage, throwable);
        } else {
            AUDIT_LOGGER.log(Level.SEVERE, logMessage);
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
            log.setIpAddress(truncate(ipAddress, 45));
            new AuditLogDAO().save(log);
        } catch (Exception e) {
            AUDIT_LOGGER.log(Level.WARNING, "Could not persist audit event (action=" + action + ")", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
