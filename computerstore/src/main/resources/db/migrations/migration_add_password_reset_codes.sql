-- Run once against an existing computer_store database before deploying
-- Forgot-password flow (Pattern B: 6-digit code sent to customer's email)
-- Only the SHA-256 hash of the code is stored; the raw code is sent by email only
CREATE TABLE IF NOT EXISTS password_reset_codes (
    code_id    INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NOT NULL,
    email      VARCHAR(100) NOT NULL,
    code_hash  CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_code_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_reset_code_user (user_id),
    INDEX idx_reset_code_email (email),
    INDEX idx_reset_code_expiry (expires_at)
) ENGINE = InnoDB;
