package com.example.computer_store.model;

import java.sql.Timestamp;

/**
 * A single entry in the system-wide audit history.
 * Immutable-ish: once written, an audit record is never edited.
 */
public class AuditLog {

    public enum ActionType {
        AUTH, ADMIN, DATA, SECURITY, SYSTEM
    }

    private int auditId;
    private ActionType actionType;
    private String actionName;
    private String actor;
    private String resourceType;
    private String resourceId;
    private String details;
    private String ipAddress;
    private String requestId;
    private String sessionId;
    private Timestamp createdAt;
    private Timestamp archivedAt;

    // Presentation helpers (not persisted).
    private String targetLabel;
    private String drillUrl;
    private String rawJson;

    public AuditLog() {
    }

    public int getAuditId() {
        return auditId;
    }

    public void setAuditId(int auditId) {
        this.auditId = auditId;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public void setTargetLabel(String targetLabel) {
        this.targetLabel = targetLabel;
    }

    public String getDrillUrl() {
        return drillUrl;
    }

    public void setDrillUrl(String drillUrl) {
        this.drillUrl = drillUrl;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Timestamp archivedAt) {
        this.archivedAt = archivedAt;
    }

    /** Compact JSON view of the full record, for the detail inspector panel. */
    public String toRawJson() {
        StringBuilder sb = new StringBuilder("{");
        appendJsonPair(sb, "audit_id", String.valueOf(auditId));
        appendJsonPair(sb, "type", actionType == null ? null : actionType.name());
        appendJsonPair(sb, "action", actionName);
        appendJsonPair(sb, "actor", actor);
        appendJsonPair(sb, "resource_type", resourceType);
        appendJsonPair(sb, "resource_id", resourceId);
        appendJsonPair(sb, "details", details);
        appendJsonPair(sb, "ip_address", ipAddress);
        appendJsonPair(sb, "request_id", requestId);
        appendJsonPair(sb, "session_id", sessionId);
        appendJsonPair(sb, "created_at", createdAt == null ? null : createdAt.toLocalDateTime().toString());
        if (archivedAt != null) {
            appendJsonPair(sb, "archived_at", archivedAt.toLocalDateTime().toString());
        }
        if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    private static void appendJsonPair(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":\"").append(jsonEscape(value)).append("\",");
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }
}