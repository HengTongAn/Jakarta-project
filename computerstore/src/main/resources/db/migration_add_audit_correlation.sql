-- Run once for existing databases before the correlation / auto-retention features.
-- Adds per-request and per-session correlation ids to the audit trail.
ALTER TABLE audit_logs
    ADD COLUMN request_id VARCHAR(64) NULL AFTER ip_address,
    ADD COLUMN session_id VARCHAR(64) NULL AFTER request_id,
    ADD INDEX idx_audit_request (request_id);

ALTER TABLE audit_logs_archive
    ADD COLUMN request_id VARCHAR(64) NULL AFTER ip_address,
    ADD COLUMN session_id VARCHAR(64) NULL AFTER request_id;