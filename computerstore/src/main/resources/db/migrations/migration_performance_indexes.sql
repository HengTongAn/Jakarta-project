-- Performance covering indexes for admin and user queries (MySQL 8.x)
--
-- MySQL has no `INCLUDE (...)` clause; covering indexes list every
-- additionally-read column in the key itself (InnoDB always appends the
-- primary key to each secondary index entry, so it never needs listing).
-- All column names, data types and path-lengths are verified against
-- src/main/resources/db/schema.sql; index names use the `idx_p_` prefix to
-- avoid colliding with the indexes created by migration_add_indexes.sql
-- (idx_products_category_status, idx_products_brand_status,
-- idx_products_price_status, idx_orders_user_orderdate,
-- idx_orders_status_orderdate).
--
-- Registered last in DatabaseMigrationRunner.discoverMigrations() and
-- executed once per database via the schema_migrations table; duplicate-key
-- errors (1061) are treated as "already applied" by the runner.

-- Products
-- Low-stock / out-of-stock / in-stock listings (InventoryDAO, storefront).
CREATE INDEX idx_p_products_status_stock ON products (status, stock_quantity);

-- Category and brand browsing with price + stock filtering.
-- name(100) stays well under the 3072-byte InnoDB key limit (utf8mb4).
CREATE INDEX idx_p_products_category_status ON products (category_id, status, name(100), price, stock_quantity);
CREATE INDEX idx_p_products_brand_status ON products (brand_id, status, name(100), price, stock_quantity, category_id);

-- Price-range browsing (ProductDAO.search filters; leading-wildcard LIKE
-- terms such as name LIKE '%x%' cannot use a b-tree index, so the filter
-- columns are indexed instead of the search text).
CREATE INDEX idx_p_products_price_range ON products (price, status, name(100), stock_quantity, category_id, brand_id);
CREATE INDEX idx_p_products_search ON products (status, category_id, brand_id, price);

-- Order items: order-detail read (OrderDAO.findItems) and analytics.
CREATE INDEX idx_p_order_items_order ON order_items (order_id, product_id, quantity, unit_price, subtotal);

-- Order lifecycle timeline (OrderDAO.findStatusEvents).
CREATE INDEX idx_p_ose_order ON order_status_events (order_id, created_at DESC);

-- Users: admin user management lists (role + soft-delete filter).
CREATE INDEX idx_p_users_role ON users (role, deleted_at, full_name);

-- Cart view joins cart_items to products ordered by added_at.
CREATE INDEX idx_p_cart_user_added ON cart_items (user_id, added_at);

-- Inventory log feed (InventoryDAO.listRecent).
CREATE INDEX idx_p_inventory_created ON inventory_logs (created_at DESC);

-- Audit log browsing filtered by type/actor (AuditLogDAO.search,
-- ORDER BY audit_id DESC correlates with created_at).
CREATE INDEX idx_p_audit_type_id ON audit_logs (action_type, audit_id DESC);
CREATE INDEX idx_p_audit_actor_id ON audit_logs (actor, audit_id DESC);