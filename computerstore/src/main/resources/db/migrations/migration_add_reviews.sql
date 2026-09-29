-- ============================================================
-- Migration: customer reviews (real, database-backed)
--  1. Create the reviews table.
--  2. One review per customer per product (unique key).
--  3. Moderation status PENDING -> APPROVED / REJECTED so
--     admins curate what customers see (admin workflow).
--  4. is_verified marks reviewers who actually purchased the
--     product (drives the "Verified purchase" badge).
-- ============================================================

USE computer_store;

CREATE TABLE IF NOT EXISTS reviews (
    review_id   INT AUTO_INCREMENT PRIMARY KEY,
    product_id  INT NOT NULL,
    user_id     INT NOT NULL,
    rating      TINYINT NOT NULL,
    title       VARCHAR(150) NULL,
    review_text TEXT NOT NULL,
    status      ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    is_verified TINYINT(1) NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES products (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    UNIQUE KEY uq_review_user_product (user_id, product_id),
    INDEX idx_review_product_status (product_id, status),
    INDEX idx_review_status_created (status, created_at),
    INDEX idx_review_verified (is_verified)
) ENGINE = InnoDB;