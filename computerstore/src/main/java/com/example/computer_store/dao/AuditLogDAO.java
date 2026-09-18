package com.example.computer_store.dao;

import com.example.computer_store.model.AuditAlert;
import com.example.computer_store.model.AuditLog;
import com.example.computer_store.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuditLogDAO {
    private Connection conn() throws SQLException { return DBConnection.getConnection(); }

    public void save(AuditLog log) throws SQLException {
        String sql = "INSERT INTO audit_logs (action_type, action_name, actor, resource_type, "
                + "resource_id, details, ip_address, request_id, session_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, log.getActionType().name());
            ps.setString(2, log.getActionName());
            setNullable(ps, 3, log.getActor()); setNullable(ps, 4, log.getResourceType());
            setNullable(ps, 5, log.getResourceId()); setNullable(ps, 6, log.getDetails());
            setNullable(ps, 7, log.getIpAddress());
            setNullable(ps, 8, log.getRequestId()); setNullable(ps, 9, log.getSessionId());
            ps.executeUpdate();
        }
    }

    public List<AuditLog> search(String typeFilter, String actorFilter, String keyword,
                                 LocalDate from, LocalDate to, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_logs WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, typeFilter, actorFilter, keyword, from, to);
        sql.append(" ORDER BY audit_id DESC LIMIT ? OFFSET ?");
        List<AuditLog> logs = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1; for (Object p : params) ps.setObject(i++, p);
            ps.setInt(i++, limit); ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) logs.add(mapLog(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Error searching audit logs", e); }
        return logs;
    }

    public int count(String typeFilter, String actorFilter, String keyword, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, typeFilter, actorFilter, keyword, from, to);
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 1; i <= params.size(); i++) ps.setObject(i, params.get(i - 1));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw new RuntimeException("Error counting audit logs", e); }
        return 0;
    }

    public List<String> distinctActors() {
        String sql = "SELECT DISTINCT actor FROM audit_logs WHERE actor IS NOT NULL AND actor <> '' ORDER BY actor";
        List<String> actors = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) actors.add(rs.getString(1));
        } catch (SQLException e) { throw new RuntimeException("Error listing audit actors", e); }
        return actors;
    }

    /** Moves aged records atomically; it never discards an audit entry. */
    public int archiveBefore(LocalDate cutoff) {
        // Do not hide a duplicate or schema error with INSERT IGNORE. If the
        // archive copy cannot be proven complete, the transaction rolls back
        // and the active records remain untouched.
        String copy = "INSERT INTO audit_logs_archive "
                + "(original_audit_id, action_type, action_name, actor, resource_type, resource_id, "
                + "details, ip_address, request_id, session_id, created_at) "
                + "SELECT audit_id, action_type, action_name, actor, resource_type, resource_id, "
                + "details, ip_address, request_id, session_id, created_at "
                + "FROM audit_logs WHERE created_at < ?";
        String delete = "DELETE FROM audit_logs WHERE created_at < ?";
        try (Connection c = conn()) {
            c.setAutoCommit(false);
            try (PreparedStatement copyPs = c.prepareStatement(copy);
                 PreparedStatement deletePs = c.prepareStatement(delete)) {
                java.sql.Date date = java.sql.Date.valueOf(cutoff);
                copyPs.setDate(1, date);
                copyPs.executeUpdate();
                deletePs.setDate(1, date);
                int archived = deletePs.executeUpdate();
                c.commit();
                return archived;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                try { c.setAutoCommit(true); } catch (SQLException ignored) { }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error archiving audit logs", e);
        }
    }

    /** How many active records are older than the given cutoff (for retention prompts). */
    public int countBefore(LocalDate cutoff) {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE created_at < ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(cutoff));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw new RuntimeException("Error counting old audit logs", e); }
        return 0;
    }

    /** Read-only queries over the archived audit records. */
    public List<AuditLog> searchArchive(String typeFilter, String actorFilter, String keyword,
                                        LocalDate from, LocalDate to, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT archive_id AS audit_id, action_type, action_name, actor, "
                + "resource_type, resource_id, details, ip_address, request_id, session_id, created_at, archived_at "
                + "FROM audit_logs_archive WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, typeFilter, actorFilter, keyword, from, to);
        sql.append(" ORDER BY archive_id DESC LIMIT ? OFFSET ?");
        List<AuditLog> logs = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1; for (Object p : params) ps.setObject(i++, p);
            ps.setInt(i++, limit); ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) logs.add(mapArchiveLog(rs)); }
        } catch (SQLException e) { throw new RuntimeException("Error searching archived audit logs", e); }
        return logs;
    }

    public int countArchive(String typeFilter, String actorFilter, String keyword, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs_archive WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, typeFilter, actorFilter, keyword, from, to);
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 1; i <= params.size(); i++) ps.setObject(i, params.get(i - 1));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw new RuntimeException("Error counting archived audit logs", e); }
        return 0;
    }

    public int countToday() {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE created_at >= CURDATE()";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException("Error counting today's audit logs", e); }
        return 0;
    }

    public int countSinceDays(int days) {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw new RuntimeException("Error counting recent audit logs", e); }
        return 0;
    }

    public int failedLoginsSinceHours(int hours) {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE action_name = 'LOGIN_FAILED' "
                + "AND created_at >= DATE_SUB(NOW(), INTERVAL ? HOUR)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, hours);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw new RuntimeException("Error counting failed logins", e); }
        return 0;
    }

    public Map<String, Integer> countByType() {
        String sql = "SELECT action_type, COUNT(*) FROM audit_logs GROUP BY action_type ORDER BY 2 DESC";
        Map<String, Integer> byType = new LinkedHashMap<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) byType.put(rs.getString(1), rs.getInt(2));
        } catch (SQLException e) { throw new RuntimeException("Error counting audit logs by type", e); }
        return byType;
    }

    /** Detects suspicious patterns in recent audit data for the Alerts tab. */
    public List<AuditAlert> findAlerts() {
        List<AuditAlert> alerts = new ArrayList<>();
        alerts.addAll(burstAlerts("BRUTE_FORCE", "High", "Repeated failed logins (possible brute force)",
                "LOGIN_FAILED", "ip_address", 5, 15, "MINUTE"));
        alerts.addAll(burstAlerts("ACCOUNT_LOCKOUT_RISK", "High", "Many failed logins for the same account",
                "LOGIN_FAILED", "actor", 5, 15, "MINUTE"));
        alerts.addAll(burstAlerts("PASSWORD_CHANGE_BURST", "Medium", "Many password changes in a short window",
                "PASSWORD_CHANGE", "actor", 3, 60, "MINUTE"));
        alerts.addAll(burstAlerts("REGISTRATION_SPAM", "Medium", "Rapid account registrations from one IP",
                "REGISTER", "ip_address", 3, 10, "MINUTE"));
        return alerts;
    }

    private List<AuditAlert> burstAlerts(String type, String severity, String summary, String action,
                                         String groupColumn, int minCount, int window, String unit) {
        String selectColumn = "actor".equals(groupColumn) ? "actor" : "ip_address";
        String sql = "SELECT " + selectColumn + ", COUNT(*) c, MIN(created_at), MAX(created_at) "
                + "FROM audit_logs WHERE action_name = ? AND " + selectColumn + " IS NOT NULL "
                + "AND created_at >= DATE_SUB(NOW(), INTERVAL ? " + unit + ") "
                + "GROUP BY " + selectColumn + " HAVING c >= ?";
        List<AuditAlert> alerts = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, action);
            ps.setInt(2, window);
            ps.setInt(3, minCount);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditAlert alert = new AuditAlert(type, severity, summary,
                            "actor".equals(groupColumn) ? rs.getString(1) : null,
                            "ip_address".equals(groupColumn) ? rs.getString(1) : null,
                            rs.getInt(2), rs.getTimestamp(3), rs.getTimestamp(4));
                    alerts.add(alert);
                }
            }
        } catch (SQLException e) { throw new RuntimeException("Error detecting audit alerts", e); }
        return alerts;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String typeFilter,
                               String actorFilter, String keyword, LocalDate from, LocalDate to) {
        if (notBlank(typeFilter)) { sql.append(" AND action_type = ?"); params.add(typeFilter); }
        if (notBlank(actorFilter)) { sql.append(" AND actor = ?"); params.add(actorFilter); }
        if (notBlank(keyword)) {
            sql.append(" AND (action_name LIKE ? OR actor LIKE ? OR resource_type LIKE ? OR resource_id LIKE ?"
                    + " OR details LIKE ? OR ip_address LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like); params.add(like); params.add(like);
            params.add(like); params.add(like); params.add(like);
        }
        if (from != null) { sql.append(" AND created_at >= ?"); params.add(java.sql.Date.valueOf(from)); }
        if (to != null) { sql.append(" AND created_at < ?"); params.add(java.sql.Date.valueOf(to.plusDays(1))); }
    }

    private AuditLog mapLog(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog(); log.setAuditId(rs.getInt("audit_id"));
        log.setActionType(AuditLog.ActionType.valueOf(rs.getString("action_type")));
        log.setActionName(rs.getString("action_name")); log.setActor(rs.getString("actor"));
        log.setResourceType(rs.getString("resource_type")); log.setResourceId(rs.getString("resource_id"));
        log.setDetails(rs.getString("details")); log.setIpAddress(rs.getString("ip_address"));
        log.setRequestId(rs.getString("request_id")); log.setSessionId(rs.getString("session_id"));
        log.setCreatedAt(rs.getTimestamp("created_at")); return log;
    }

    private AuditLog mapArchiveLog(ResultSet rs) throws SQLException {
        AuditLog log = mapLog(rs);
        log.setArchivedAt(rs.getTimestamp("archived_at"));
        return log;
    }

    private void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value);
    }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
