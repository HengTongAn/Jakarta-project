package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.AuditAlert;
import com.hengtongan.computerstore.core.domain.entity.AuditLog;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.web.ErrorHandler;
import com.hengtongan.computerstore.util.time.TimeBoundaries;
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

public class AuditLogRepository {
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
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("searching audit logs", e); }
        return logs;
    }

    public int count(String typeFilter, String actorFilter, String keyword, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, typeFilter, actorFilter, keyword, from, to);
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 1; i <= params.size(); i++) ps.setObject(i, params.get(i - 1));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("counting audit logs", e); }
        return 0;
    }

    public List<String> distinctActors() {
        String sql = "SELECT DISTINCT actor FROM audit_logs WHERE actor IS NOT NULL AND actor <> '' ORDER BY actor";
        List<String> actors = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) actors.add(rs.getString(1));
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("listing audit actors", e); }
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
                // Local-midnight boundary so "older than N days" matches the
                // dates shown to admins (see TimeBoundaries).
                java.sql.Timestamp boundary = TimeBoundaries.startOfDay(cutoff);
                copyPs.setTimestamp(1, boundary);
                copyPs.executeUpdate();
                deletePs.setTimestamp(1, boundary);
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
            throw ErrorHandler.handleDatabaseError("archiving audit logs", e);
        }
    }

    /** How many active records are older than the given cutoff (for retention prompts). */
    public int countBefore(LocalDate cutoff) {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE created_at < ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, TimeBoundaries.startOfDay(cutoff));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("counting old audit logs", e); }
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
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("searching archived audit logs", e); }
        return logs;
    }

    public int countArchive(String typeFilter, String actorFilter, String keyword, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs_archive WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, typeFilter, actorFilter, keyword, from, to);
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 1; i <= params.size(); i++) ps.setObject(i, params.get(i - 1));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("counting archived audit logs", e); }
        return 0;
    }

    public int countToday() {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE created_at >= ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, TimeBoundaries.todayStart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("counting today's audit logs", e); }
        return 0;
    }

    public int countSinceDays(int days) {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE created_at >= ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, TimeBoundaries.daysAgo(days));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("counting recent audit logs", e); }
        return 0;
    }

    public int failedLoginsSinceHours(int hours) {
        String sql = "SELECT COUNT(*) FROM audit_logs WHERE action_name = 'LOGIN_FAILED' "
                + "AND created_at >= ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, TimeBoundaries.hoursAgo(hours));
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("counting failed logins", e); }
        return 0;
    }

    public Map<String, Integer> countByType() {
        String sql = "SELECT action_type, COUNT(*) FROM audit_logs GROUP BY action_type ORDER BY 2 DESC";
        Map<String, Integer> byType = new LinkedHashMap<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) byType.put(rs.getString(1), rs.getInt(2));
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("counting audit logs by type", e); }
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
        // Rolling window computed in Java so it is independent of the JDBC
        // session time zone (see TimeBoundaries).
        long seconds = switch (unit) {
            case "HOUR" -> window * 3600L;
            case "DAY" -> window * 86400L;
            default -> window * 60L; // MINUTE and anything unexpected fall back to minutes
        };
        String sql = "SELECT " + selectColumn + ", COUNT(*) c, MIN(created_at), MAX(created_at) "
                + "FROM audit_logs WHERE action_name = ? AND " + selectColumn + " IS NOT NULL "
                + "AND created_at >= ? "
                + "GROUP BY " + selectColumn + " HAVING c >= ?";
        List<AuditAlert> alerts = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, action);
            ps.setTimestamp(2, TimeBoundaries.secondsAgo(seconds));
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
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("detecting audit alerts", e); }
        return alerts;
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String typeFilter,
                               String actorFilter, String keyword, LocalDate from, LocalDate to) {
        if (notBlank(typeFilter)) { sql.append(" AND action_type = ?"); params.add(typeFilter); }
        if (notBlank(actorFilter)) { sql.append(" AND actor = ?"); params.add(actorFilter); }
        if (notBlank(keyword)) {
            // The keyword is matched literally: escape LIKE wildcards AND the
            // escape character itself, so user input can never inject % or _
            // patterns (nor a forged escape char) into the search.
            sql.append(" AND (action_name LIKE ? ESCAPE '\\' OR actor LIKE ? ESCAPE '\\' OR resource_type LIKE ? ESCAPE '\\'"
                    + " OR resource_id LIKE ? ESCAPE '\\' OR details LIKE ? ESCAPE '\\' OR ip_address LIKE ? ESCAPE '\\')");
            String like = "%" + escapeLike(keyword.trim()) + "%";
            params.add(like); params.add(like); params.add(like);
            params.add(like); params.add(like); params.add(like);
        }
        if (from != null) { sql.append(" AND created_at >= ?"); params.add(TimeBoundaries.startOfDay(from)); }
        if (to != null) { sql.append(" AND created_at < ?"); params.add(TimeBoundaries.startOfDay(to.plusDays(1))); }
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

    /** Escapes \, % and _ so a keyword is matched literally, not as wildcards. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value);
    }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
