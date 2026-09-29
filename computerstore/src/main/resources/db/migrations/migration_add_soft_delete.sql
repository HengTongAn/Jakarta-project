-- Run once against an existing computer_store database before deploying
-- History & Trash.  These columns make deletion recoverable.
ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE products ADD COLUMN deleted_by INT NULL;
ALTER TABLE products ADD COLUMN delete_reason VARCHAR(255) NULL;
CREATE INDEX idx_products_deleted_at ON products (deleted_at);
ALTER TABLE brands ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE brands ADD COLUMN deleted_by INT NULL;
ALTER TABLE brands ADD COLUMN delete_reason VARCHAR(255) NULL;
CREATE INDEX idx_brands_deleted_at ON brands (deleted_at);
ALTER TABLE categories ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE categories ADD COLUMN deleted_by INT NULL;
ALTER TABLE categories ADD COLUMN delete_reason VARCHAR(255) NULL;
CREATE INDEX idx_categories_deleted_at ON categories (deleted_at);
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN deleted_by INT NULL;
ALTER TABLE users ADD COLUMN delete_reason VARCHAR(255) NULL;
CREATE INDEX idx_users_deleted_at ON users (deleted_at);
