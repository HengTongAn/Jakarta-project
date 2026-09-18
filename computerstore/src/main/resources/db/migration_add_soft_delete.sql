-- Run once against an existing computer_store database before deploying
-- History & Trash.  These columns make deletion recoverable.
ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP NULL, ADD COLUMN deleted_by INT NULL, ADD COLUMN delete_reason VARCHAR(255) NULL, ADD INDEX idx_products_deleted_at (deleted_at);
ALTER TABLE brands ADD COLUMN deleted_at TIMESTAMP NULL, ADD COLUMN deleted_by INT NULL, ADD COLUMN delete_reason VARCHAR(255) NULL, ADD INDEX idx_brands_deleted_at (deleted_at);
ALTER TABLE categories ADD COLUMN deleted_at TIMESTAMP NULL, ADD COLUMN deleted_by INT NULL, ADD COLUMN delete_reason VARCHAR(255) NULL, ADD INDEX idx_categories_deleted_at (deleted_at);
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP NULL, ADD COLUMN deleted_by INT NULL, ADD COLUMN delete_reason VARCHAR(255) NULL, ADD INDEX idx_users_deleted_at (deleted_at);
