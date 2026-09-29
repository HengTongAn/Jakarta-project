-- ============================================================
-- Two-Factor Authentication (2FA) Support
-- ============================================================

CREATE TABLE IF NOT EXISTS two_factor_secrets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    secret_key VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_2fa_user (user_id),
    INDEX idx_2fa_enabled (enabled)
) ENGINE = InnoDB;

-- Existing plaintext secrets must be re-enrolled through the application
-- after configuring COMPUTERSTORE_2FA_ENCRYPTION_KEY. The application refuses
-- to authenticate with legacy plaintext values rather than exposing them.
