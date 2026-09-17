package com.example.computer_store.dao;

import com.example.computer_store.model.AuditLog;
import com.example.computer_store.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AuditLogDAO {
    private Connection conn() throws SQLException { return DBConnection.getConnection(); }

    public void save(AuditLog log) throws SQLException {
        String sql = "INSERT INTO audit_logs (action_type, action_name, actor, resource_type, "
                + "resource_id, details, ip_address) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, log.getActionType().name());
            ps.setString(2, log.getActionName());
            setNullable(ps, 3, log.getActor()); setNullable(ps, 4, log.getResourceType());
            setNullable(ps, 5, log.getResourceId()); setNullable(ps, 6, log.getDetails());
            setNullable(ps, 7, log.getIpAddress()); ps.executeUpdate();
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
                + "(original_audit_id, action_type, action_name, actor, resource_type, resource_id, details, ip_address, created_at) "
                + "SELECT audit_id, action_type, action_name, actor, resource_type, resource_id, details, ip_address, created_at "
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

    /** Permanently removes one log entry after an explicit admin confirmation. */
    public boolean deleteById(int auditId) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement("DELETE FROM audit_logs WHERE audit_id = ?")) {
            ps.setInt(1, auditId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException("Error deleting audit log", e); }
    }

    private void appendFilters(StringBuilder sql, List<Object> params, String typeFilter,
                               String actorFilter, String keyword, LocalDate from, LocalDate to) {
        if (notBlank(typeFilter)) { sql.append(" AND action_type = ?"); params.add(typeFilter); }
        if (notBlank(actorFilter)) { sql.append(" AND actor = ?"); params.add(actorFilter); }
        if (notBlank(keyword)) {
            sql.append(" AND (action_name LIKE ? OR resource_type LIKE ? OR resource_id LIKE ? OR details LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like); params.add(like); params.add(like); params.add(like);
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
        log.setCreatedAt(rs.getTimestamp("created_at")); return log;
    }

    private void setNullable(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isEmpty()) ps.setNull(index, Types.VARCHAR); else ps.setString(index, value);
    }
    private boolean notBlank(String value) { return value != null && !value.trim().isEmpty(); }
}
