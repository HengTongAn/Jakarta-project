-- ============================================================
-- Migration: performance indexes
--  Composite indexes for the hot query paths. Only indexes that
--  do NOT already exist in schema.sql are created here:
--   - Catalogue filtering (WHERE deleted_at IS NULL AND status <> 'DISCONTINUED'
--     plus category/brand/price filters, ORDER BY price/name/created_at)
--   - Admin/order lookups (WHERE user_id = ? / status = ? ORDER BY order_date DESC)
--  Note: products.sku, order_items.order_id, cart_items.user_id,
--  mail_messages inbox, audit_logs.created_at are already indexed
--  by schema.sql / UNIQUE constraints, so they are skipped here.
-- ============================================================

-- Products: catalogue filter by category + soft-delete status
CREATE INDEX idx_products_category_status ON products (category_id, deleted_at, status);
CREATE INDEX idx_products_brand_status    ON products (brand_id, deleted_at, status);
CREATE INDEX idx_products_price_status    ON products (price, deleted_at, status);

-- Orders: user's order history and the admin status dashboard both
-- filter then ORDER BY order_date DESC (paged admin listing).
CREATE INDEX idx_orders_user_orderdate    ON orders (user_id, order_date DESC);
CREATE INDEX idx_orders_status_orderdate  ON orders (status, order_date DESC);
