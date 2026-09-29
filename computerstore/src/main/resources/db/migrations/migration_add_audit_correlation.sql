-- Run once for existing databases before the correlation / auto-retention features.
-- Adds per-request and per-session correlation ids to the audit trail.
-- (audit_logs_archive already gets request_id / session_id from
-- migration_add_audit_archive.sql, so nothing to alter there.)
ALTER TABLE audit_logs
    ADD COLUMN request_id VARCHAR(64) NULL AFTER ip_address;
ALTER TABLE audit_logs
    ADD COLUMN session_id VARCHAR(64) NULL AFTER request_id;
CREATE INDEX idx_audit_request ON audit_logs (request_id);
