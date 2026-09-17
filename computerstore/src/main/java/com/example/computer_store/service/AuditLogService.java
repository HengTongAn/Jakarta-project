package com.example.computer_store.service;

import com.example.computer_store.dao.AuditLogDAO;
import com.example.computer_store.model.AuditLog;
import java.util.List;
import java.time.LocalDate;

/** Read-side queries for the admin History page. */
public class AuditLogService {
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

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

    public boolean deleteById(int auditId) {
        if (auditId <= 0) throw new IllegalArgumentException("Invalid history record.");
        return auditLogDAO.deleteById(auditId);
    }
}
