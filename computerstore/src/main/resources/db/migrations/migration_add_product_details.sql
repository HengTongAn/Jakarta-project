-- ============================================================
-- Migration: Add rich product detail fields + product_specs table
-- Rich, editable product details (highlights, box contents, warranty,
-- official source URL) plus a key/value specifications table, all
-- maintained manually in the admin product form.
-- ============================================================

-- Extra product detail columns (all optional).
-- NOTE: MySQL 8 does not support "ADD COLUMN IF NOT EXISTS" (MariaDB-only).
-- Each column is added in its own statement so the migration runner can skip
-- an already-present column (error 1060 "Duplicate column name") without
-- blocking the remaining statements, e.g. when replaying on a fresh
-- schema.sql install where the columns already exist.
ALTER TABLE products ADD COLUMN highlights     TEXT         AFTER description;
ALTER TABLE products ADD COLUMN box_contents   VARCHAR(500) AFTER stock_quantity;
ALTER TABLE products ADD COLUMN warranty_info  VARCHAR(255) AFTER box_contents;
ALTER TABLE products ADD COLUMN source_url     VARCHAR(500) AFTER warranty_info;

-- Key/value specifications, rendered as a table on the product page and
-- edited row-by-row in the admin form.
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
