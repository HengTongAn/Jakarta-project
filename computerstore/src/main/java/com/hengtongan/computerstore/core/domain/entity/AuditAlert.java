package com.hengtongan.computerstore.core.domain.entity;

import java.sql.Timestamp;

/**
 * A detected suspicious pattern surfaced on the History > Alerts tab.
 */
public class AuditAlert {

    private String type;
    private String severity;
    private String summary;
    private String actor;
    private String ipAddress;
    private int count;
    private Timestamp firstSeen;
    private Timestamp lastSeen;

    public AuditAlert() {
    }

    public AuditAlert(String type, String severity, String summary, String actor, String ipAddress,
                      int count, Timestamp firstSeen, Timestamp lastSeen) {
        this.type = type;
        this.severity = severity;
        this.summary = summary;
        this.actor = actor;
        this.ipAddress = ipAddress;
        this.count = count;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public Timestamp getFirstSeen() { return firstSeen; }
    public void setFirstSeen(Timestamp firstSeen) { this.firstSeen = firstSeen; }
    public Timestamp getLastSeen() { return lastSeen; }
    public void setLastSeen(Timestamp lastSeen) { this.lastSeen = lastSeen; }
}