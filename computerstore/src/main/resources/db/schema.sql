-- ============================================================
-- Computer Store Management System - Database Schema
-- MySQL 8.x
-- SAFE TO RE-RUN: this file never drops an existing database.
-- To start completely fresh, drop the database explicitly yourself
-- (e.g. `mysql -e "DROP DATABASE computer_store"`), never inside this
-- file - a stray `DROP DATABASE` here would wipe whichever host the
-- script is run against.
-- ============================================================

CREATE DATABASE IF NOT EXISTS computer_store
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE computer_store;

-- ------------------------------------------------------------
-- Users (customers + admin)
-- ------------------------------------------------------------
CREATE TABLE users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    avatar_url    VARCHAR(255) NULL,
    role          ENUM('SUPER_ADMIN', 'ADMIN', 'CUSTOMER') NOT NULL DEFAULT 'CUSTOMER',
    last_active_at TIMESTAMP NULL DEFAULT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP NULL,
    deleted_by    INT NULL,
    delete_reason VARCHAR(255) NULL,
    INDEX idx_users_deleted_at (deleted_at)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Categories
-- ------------------------------------------------------------
CREATE TABLE categories (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP NULL,
    deleted_by  INT NULL,
    delete_reason VARCHAR(255) NULL,
    INDEX idx_categories_deleted_at (deleted_at)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Brands
-- ------------------------------------------------------------
CREATE TABLE brands (
    brand_id    INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP NULL,
    deleted_by  INT NULL,
    delete_reason VARCHAR(255) NULL,
    INDEX idx_brands_deleted_at (deleted_at)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Products
-- ------------------------------------------------------------
CREATE TABLE products (
    product_id     INT AUTO_INCREMENT PRIMARY KEY,
    category_id    INT NOT NULL,
    brand_id       INT NOT NULL,
    name           VARCHAR(200) NOT NULL,
    sku            VARCHAR(50)  NOT NULL UNIQUE,
    description    TEXT,
    price          DECIMAL(10, 2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    image_url      VARCHAR(500),
    highlights     TEXT,
    box_contents   VARCHAR(500),
    warranty_info  VARCHAR(255),
    source_url     VARCHAR(500),
    status         ENUM('IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK', 'DISCONTINUED') NOT NULL DEFAULT 'OUT_OF_STOCK',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at     TIMESTAMP NULL,
    deleted_by     INT NULL,
    delete_reason  VARCHAR(255) NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brands (brand_id),
    CONSTRAINT chk_product_price CHECK (price >= 0),
    CONSTRAINT chk_product_stock CHECK (stock_quantity >= 0),
    INDEX idx_product_category (category_id),
    INDEX idx_product_brand (brand_id),
    INDEX idx_product_status (status),
    INDEX idx_products_deleted_at (deleted_at)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Product specifications (rich, key/value product details)
-- ------------------------------------------------------------
CREATE TABLE product_specs (
    spec_id     INT AUTO_INCREMENT PRIMARY KEY,
    product_id  INT NOT NULL,
    spec_key    VARCHAR(100) NOT NULL,
    spec_value  VARCHAR(500) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_specs_product FOREIGN KEY (product_id)
        REFERENCES products (product_id) ON DELETE CASCADE,
    UNIQUE KEY uq_product_specs (product_id, spec_key),
    KEY idx_product_specs_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- Orders
-- ------------------------------------------------------------
CREATE TABLE orders (
    order_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    order_date   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    status       ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    INDEX idx_order_user (user_id),
    INDEX idx_order_status (status)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Order status events (the order lifecycle timeline)
-- ------------------------------------------------------------
CREATE TABLE order_status_events (
    event_id    INT AUTO_INCREMENT PRIMARY KEY,
    order_id    INT NOT NULL,
    from_status ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED') NULL,
    to_status   ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED') NOT NULL,
    changed_by  VARCHAR(50) NULL,
    note        VARCHAR(255) NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ose_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE,
    INDEX idx_ose_order (order_id)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Order items (historical prices preserved)
-- ------------------------------------------------------------
CREATE TABLE order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id      INT NOT NULL,
    product_id    INT NOT NULL,
    quantity      INT NOT NULL,
    unit_price    DECIMAL(10, 2) NOT NULL,
    subtotal      DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT chk_order_item_qty CHECK (quantity > 0),
    INDEX idx_order_item_order (order_id),
    INDEX idx_order_item_product (product_id)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Cart items (one row per user + product)
-- ------------------------------------------------------------
CREATE TABLE cart_items (
    cart_item_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    product_id   INT NOT NULL,
    quantity     INT NOT NULL,
    added_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT chk_cart_qty CHECK (quantity > 0),
    UNIQUE KEY uq_cart_user_product (user_id, product_id)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Inventory change logs
-- ------------------------------------------------------------
CREATE TABLE inventory_logs (
    log_id         INT AUTO_INCREMENT PRIMARY KEY,
    product_id     INT NOT NULL,
    old_quantity   INT NOT NULL,
    new_quantity   INT NOT NULL,
    action         VARCHAR(50) NOT NULL,
    user_id       INT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_log_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_inv_log_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- In-app mail (Gmail-style) between customers and admin
-- ------------------------------------------------------------
CREATE TABLE mail_messages (
    message_id   INT AUTO_INCREMENT PRIMARY KEY,
    sender_id    INT NOT NULL,
    recipient_id INT NOT NULL,
    subject      VARCHAR(200) NOT NULL,
    body         TEXT NOT NULL,
    read_flag    TINYINT(1) NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mail_sender FOREIGN KEY (sender_id) REFERENCES users (user_id),
    CONSTRAINT fk_mail_recipient FOREIGN KEY (recipient_id) REFERENCES users (user_id),
    INDEX idx_mail_inbox (recipient_id, read_flag, created_at),
    INDEX idx_mail_sent (sender_id, created_at)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Customer reviews (moderated; one review per user per product)
-- ------------------------------------------------------------
CREATE TABLE reviews (
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

-- ------------------------------------------------------------
-- Password reset tokens (forgot-password flow)
-- Only the SHA-256 hash of the token is stored; the raw token is emailed.
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- Two-factor authentication secrets. secret_key is AES-GCM encrypted by
-- the application; never insert plaintext TOTP secrets into this table.
-- ------------------------------------------------------------
CREATE TABLE two_factor_secrets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    secret_key VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_two_factor_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_2fa_enabled (enabled)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Audit log (record of every important action in the system)
-- No foreign keys: history must never be blocked by deletions
-- ------------------------------------------------------------
CREATE TABLE audit_logs (
    audit_id      INT AUTO_INCREMENT PRIMARY KEY,
    action_type   ENUM('AUTH', 'ADMIN', 'DATA', 'SECURITY', 'SYSTEM') NOT NULL,
    action_name   VARCHAR(100) NOT NULL,
    actor         VARCHAR(50)  NULL,
    resource_type VARCHAR(50)  NULL,
    resource_id   VARCHAR(50)  NULL,
    details       VARCHAR(500) NULL,
    ip_address    VARCHAR(45)  NULL,
    request_id    VARCHAR(64)  NULL,
    session_id    VARCHAR(64)  NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_type (action_type),
    INDEX idx_audit_actor (actor),
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_request (request_id)
) ENGINE = InnoDB;

-- Archived audit records: moved from the active table by the retention workflow.
CREATE TABLE audit_logs_archive (
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
