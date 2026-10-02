-- ============================================================
-- Computer Store Management System - Database Schema
-- MySQL 8.x
--
-- SAFE TO RE-RUN: every statement below is `CREATE ... IF NOT EXISTS`
-- and this file never drops an existing database, so running it against a
-- populated server is a no-op. To start completely fresh, drop the
-- database explicitly yourself (e.g. `mysql -e "DROP DATABASE
-- computer_store"`), never inside this file - a stray `DROP DATABASE`
-- here would wipe whichever host the script is run against.
--
-- This file is the COMPLETE base schema. It was previously missing six
-- tables (payments, transactions, app_settings, password_reset_tokens,
-- password_reset_codes, page_experience_samples) and the payment columns
-- on orders, because those lived only in src/main/resources/db/migrations/.
-- A fresh install therefore produced a database the app could not serve
-- payments, password resets, EPT analytics or runtime settings on until
-- the migration runner happened to fix it up at startup.
--
-- The migrations still exist and still run at startup; they are
-- idempotent (IF NOT EXISTS / guarded ALTERs) and remain the mechanism for
-- evolving an EXISTING database. This file is for standing one up.
-- ============================================================

CREATE DATABASE IF NOT EXISTS computer_store
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE computer_store;

-- ------------------------------------------------------------
-- Users (customers + admin)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
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
CREATE TABLE IF NOT EXISTS categories (
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
CREATE TABLE IF NOT EXISTS brands (
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
CREATE TABLE IF NOT EXISTS products (
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
CREATE TABLE IF NOT EXISTS product_specs (
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
--
-- payment_method / payment_provider / payment_status /
-- payment_transaction / paid_at used to be added only by
-- migration_add_payments.sql, so this table was missing them. They are
-- VARCHAR rather than ENUM on purpose: payment providers arrive over time
-- and an ENUM silently rejects an unknown value, while a VARCHAR plus the
-- validation in PaymentService keeps an unmapped provider visible instead
-- of fatal.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    order_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    order_date   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    status       ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    payment_method      VARCHAR(20)  NOT NULL DEFAULT 'COD',
    payment_provider    VARCHAR(20)  NULL,
    payment_status      VARCHAR(20)  NOT NULL DEFAULT 'UNPAID',
    payment_transaction VARCHAR(64)  NULL,
    paid_at             TIMESTAMP    NULL,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    INDEX idx_order_user (user_id),
    INDEX idx_order_status (status)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Order status events (the order lifecycle timeline)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_status_events (
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
CREATE TABLE IF NOT EXISTS order_items (
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
CREATE TABLE IF NOT EXISTS cart_items (
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
--
-- old_quantity / new_quantity must describe the row before and after the
-- change that wrote this row. Deriving old from new (rather than from a
-- separately-read snapshot) is what keeps that true under concurrency.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory_logs (
    log_id         INT AUTO_INCREMENT PRIMARY KEY,
    product_id     INT NOT NULL,
    old_quantity   INT NOT NULL,
    new_quantity   INT NOT NULL,
    action         VARCHAR(50) NOT NULL,
    user_id        INT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inv_log_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_inv_log_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- In-app mail (Gmail-style) between customers and admin
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mail_messages (
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

-- ------------------------------------------------------------
-- Two-factor authentication secrets. secret_key is AES-GCM encrypted by
-- the application; never insert plaintext TOTP secrets into this table.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS two_factor_secrets (
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
CREATE TABLE IF NOT EXISTS audit_logs (
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

-- ------------------------------------------------------------
-- Runtime-editable site settings (payment config, support channels).
-- Seeded EMPTY on purpose: a support link pointing at an account the store
-- does not own sends customers to a stranger, and an inactive chip does not.
-- Admins paste the real destinations at /admin/support.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES
    ('support.facebook.url',  ''),
    ('support.messenger.url', ''),
    ('support.telegram.url', ''),
    ('support.x.url',         '');

-- ------------------------------------------------------------
-- Password reset, Pattern A: expiring single-use link emailed to the
-- customer. Only the SHA-256 hash of the token is stored; the raw token
-- exists only in the email.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
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
-- Password reset, Pattern B: 6-digit code. failed_attempts caps guessing
-- on a 10^6 space; it is what makes a short code safe to send by email.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_codes (
    code_id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    email           VARCHAR(100) NOT NULL,
    code_hash       CHAR(64) NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    used_at         TIMESTAMP NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reset_code_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_reset_code_user (user_id),
    INDEX idx_reset_code_email (email),
    INDEX idx_reset_code_expiry (expires_at)
) ENGINE = InnoDB;

-- ------------------------------------------------------------
-- Payment attempts: append-only history, one row per attempt. A customer can
-- retry after a failure, so this must hold more than one row per order.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    payment_id     INT AUTO_INCREMENT PRIMARY KEY,
    order_id       INT NOT NULL,
    provider       VARCHAR(20)  NOT NULL,
    transaction_id VARCHAR(64) NULL,
    amount         DECIMAL(10,2) NOT NULL,
    currency       VARCHAR(3)   NOT NULL DEFAULT 'USD',
    status         VARCHAR(20)  NOT NULL,
    message        VARCHAR(255) NULL,
    -- The gateway hands back a base64 PNG and hands it back only once, so it
    -- has to be stored or a page refresh leaves the customer with nothing to
    -- scan. MEDIUMTEXT rather than BLOB because it arrives already
    -- base64-encoded and is dropped straight into an <img src="data:...">.
    qr_image       MEDIUMTEXT   NULL,
    aba_phone      VARCHAR(32)  NULL,
    -- Card details are deliberately only the brand and last four digits.
    -- Nothing past four characters is ever stored or logged.
    card_brand     VARCHAR(20)  NULL,
    card_last4     VARCHAR(4)   NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    INDEX idx_payments_order (order_id),
    -- A duplicated transaction id would mean a gateway callback matched the
    -- wrong order, so let the database say so. This unique index also serves
    -- every lookup on transaction_id.
    UNIQUE KEY uq_payments_transaction (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Ledger of payments and refunds, independent of the payment_attempts above:
-- a refund is a ledger row, and /admin/transactions reads this table.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    transaction_type ENUM('PAYMENT', 'REFUND', 'PARTIAL_REFUND', 'CHARGEBACK') NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REFUNDED', 'PARTIALLY_REFUNDED', 'CHARGEBACK') NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    payment_method VARCHAR(50) NOT NULL,
    gateway_transaction_id VARCHAR(255),
    gateway_response_code VARCHAR(50),
    gateway_response_message TEXT,
    user_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_transaction_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_transaction_order (order_id),
    INDEX idx_transaction_user (user_id),
    INDEX idx_transaction_status (status),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_transaction_gateway (gateway_transaction_id),
    INDEX idx_transaction_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- Experienced Page Time (EPT) samples: real-user monitoring for the
-- storefront, measured in the browser by rum.js and reported to
-- /realtime/ept.
--
-- Everything /admin/performance showed before this was read out of the JVM:
-- cache hit rates, pool occupancy, query durations. All of it describes the
-- server, and none of it answers the question an operator actually has, which
-- is "why does it feel slow to my customers".
--
-- DELIBERATELY ABSENT: no IP address, no user id, no session id, no full URL,
-- no raw User-Agent. Each is either personal data this feature has no need
-- for, or an unbounded-cardinality key that would turn "which page type is
-- slowest" into a list of single visits. `browser` is a coarse family and
-- `page_type` is a route pattern, both bounded by construction.
--
-- server_ms is responseStart - requestStart, so it EXCLUDES connection setup
-- and TLS; ttfb_ms is responseStart from navigation start, so it includes
-- them. The gap between the two is what a slow connection looks like. Neither
-- is the server's own in-container timing, which cannot reach a script at all
-- because it is not known until the response has been written -- that lives in
-- MetricsCollector under ExperienceFilter.METRIC_SERVER_MS.
--
-- Only a sampled fraction of page views is stored (computerstore.rum.sampleRate),
-- so the counts describe the sample rather than total traffic. Latency
-- percentiles stay valid under uniform sampling even though the volume does not.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS page_experience_samples (
    sample_id      BIGINT       AUTO_INCREMENT PRIMARY KEY,
    page_type      VARCHAR(64)  NOT NULL,
    browser        VARCHAR(24)  NOT NULL,
    device         VARCHAR(16)  NOT NULL DEFAULT 'unknown',
    server_ms      INT          NOT NULL DEFAULT 0,
    ttfb_ms        INT          NOT NULL DEFAULT 0,
    -- Browser-observed, milliseconds from navigation start. NULL when the
    -- browser did not report that phase.
    interactive_ms INT          NULL,
    dom_ready_ms   INT          NULL,
    load_ms        INT          NULL,
    transfer_bytes INT          NOT NULL DEFAULT 0,
    sampled_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Every report filters by window first, then groups. These orders let the
    -- window predicate be an index range scan rather than a full table scan.
    INDEX idx_page_exp_time (sampled_at),
    INDEX idx_page_exp_type_time (page_type, sampled_at),
    INDEX idx_page_exp_browser_time (browser, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
