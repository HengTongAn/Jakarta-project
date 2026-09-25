-- Run once for existing databases before using Admin > History > Archive old.
CREATE TABLE IF NOT EXISTS audit_logs_archive (
    archive_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_audit_id INT NOT NULL,
    action_type       ENUM('AUTH', 'ADMIN', 'DATA', 'SECURITY', 'SYSTEM') NOT NULL,
    action_name       VARCHAR(100) NOT NULL,
    actor             VARCHAR(50) NULL,
    resource_type     VARCHAR(50) NULL,
    resource_id       VARCHAR(50) NULL,
    details           VARCHAR(500) NULL,
    ip_address        VARCHAR(45) NULL,
    request_id        VARCHAR(64) NULL,
    session_id        VARCHAR(64) NULL,
    created_at        TIMESTAMP NOT NULL,
    archived_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_audit_archive_original (original_audit_id),
    INDEX idx_audit_archive_created (created_at)
) ENGINE = InnoDB;
