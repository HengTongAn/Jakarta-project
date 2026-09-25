package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.repository.AuditLogRepository;
import com.hengtongan.computerstore.core.domain.entity.AuditAlert;
import com.hengtongan.computerstore.core.domain.entity.AuditLog;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

/** Read-side queries for the admin History page. */
public class AuditLogService {
    private final AuditLogRepository auditLogDAO = new AuditLogRepository();

    public List<AuditLog> search(String type, String actor, String keyword, LocalDate from, LocalDate to,
                                 int offset, int limit) {
        return auditLogDAO.search(type, actor, keyword, from, to, offset, limit);
    }

    public int count(String type, String actor, String keyword, LocalDate from, LocalDate to) {
        return auditLogDAO.count(type, actor, keyword, from, to);
    }

    public List<String> distinctActors() {
        return auditLogDAO.distinctActors();
    }

    public int archiveBefore(LocalDate cutoff) {
        return auditLogDAO.archiveBefore(cutoff);
    }

    public int countBefore(LocalDate cutoff) {
        return auditLogDAO.countBefore(cutoff);
    }

    public List<AuditLog> searchArchive(String type, String actor, String keyword, LocalDate from, LocalDate to,
                                        int offset, int limit) {
        return auditLogDAO.searchArchive(type, actor, keyword, from, to, offset, limit);
    }

    public int countArchive(String type, String actor, String keyword, LocalDate from, LocalDate to) {
        return auditLogDAO.countArchive(type, actor, keyword, from, to);
    }

    public int countToday() {
        return auditLogDAO.countToday();
    }

    public int countSinceDays(int days) {
        return auditLogDAO.countSinceDays(days);
    }

    public int failedLoginsSinceHours(int hours) {
        return auditLogDAO.failedLoginsSinceHours(hours);
    }

    public Map<String, Integer> countByType() {
        return auditLogDAO.countByType();
    }

    public List<AuditAlert> findAlerts() {
        return auditLogDAO.findAlerts();
    }
}
