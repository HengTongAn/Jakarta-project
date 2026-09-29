-- Run once against an existing computer_store database before deploying
-- Forgot-password flow (Pattern A: expiring single-use reset link emailed to the customer).
-- Only the SHA-256 hash of the token is stored; the raw token is sent by email only.
CREATE TABLE password_reset_tokens (
    token_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_reset_token_user (user_id),
    INDEX idx_reset_token_expiry (expires_at)
) ENGINE = InnoDB;