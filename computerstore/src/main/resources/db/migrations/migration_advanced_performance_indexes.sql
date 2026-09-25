-- ============================================================
-- Migration: Advanced Performance Indexes for High-Throughput
-- 
-- This migration adds specialized indexes to optimize for:
-- 1. Rapid page navigation (catalog browsing)
-- 2. Dashboard queries (admin operations)
-- 3. Search functionality (full-text and partial matching)
-- 4. Analytics queries (reporting)
-- 5. Real-time inventory checks
-- ============================================================

-- ------------------------------------------------------------
-- COMPOSITE COVERING INDEXES
-- These indexes include all columns needed for common queries
-- to avoid table lookups (covering index optimization)
-- ------------------------------------------------------------

-- Product catalog search with status and soft-delete filtering
-- Covers: status, deleted_at, category_id, brand_id, price, name, stock_quantity
CREATE INDEX idx_covering_products_catalog ON products (status, deleted_at, category_id, brand_id, price, name(100), stock_quantity);

-- Product detail page load
-- Covers: product_id, status, deleted_at, category_id, brand_id
CREATE INDEX idx_covering_products_detail ON products (product_id, status, deleted_at, category_id, brand_id);

-- Order history for customer dashboard
-- Covers: user_id, status, order_date, total_amount
CREATE INDEX idx_covering_orders_user_history ON orders (user_id, status, order_date DESC, total_amount);

-- Admin order management dashboard
-- Covers: status, order_date DESC, user_id, total_amount
CREATE INDEX idx_covering_orders_admin ON orders (status, order_date DESC, user_id, total_amount);

-- ------------------------------------------------------------
-- SPECIALIZED INDEXES FOR SPECIFIC USE CASES
-- ------------------------------------------------------------

-- Rapid stock availability checks for cart/checkout
-- Used by: CartRepository.addItemAtomic, OrderServiceImpl.checkout
CREATE INDEX idx_products_stock_check ON products (product_id, status, stock_quantity, deleted_at);

-- SKU lookup for product management
-- Used by: ProductRepository.findBySku
CREATE INDEX idx_products_sku ON products (sku(50), deleted_at);

-- Trending products calculation (units sold + recency)
-- Used by: ProductRepository.findTrending
CREATE INDEX idx_products_trending ON products (status, deleted_at, product_id DESC, stock_quantity);

-- New arrivals (recently created)
-- Used by: ProductServlet.showList (new arrivals section)
CREATE INDEX idx_products_new_arrivals ON products (created_at DESC, status, deleted_at);

-- Cart items per user with product info
-- Used by: CartRepository.findItemsByUser
CREATE INDEX idx_cart_user_product ON cart_items (user_id, product_id, added_at DESC);

-- Order items per order for detail view
-- Used by: OrderRepository.findItemsByOrderId
CREATE INDEX idx_order_items_order_product ON order_items (order_id, product_id, quantity);

-- Review moderation queue
-- Used by: ReviewService.getPage
CREATE INDEX idx_reviews_moderation ON reviews (status, created_at DESC, product_id);

-- User authentication lookup
-- Used by: UserRepository.findByUsername, UserRepository.findByEmail
CREATE INDEX idx_users_auth_username ON users (username(50), deleted_at);
CREATE INDEX idx_users_auth_email ON users (email(100), deleted_at);

-- Admin user management
-- Used by: UserRepository.findByRole, UserRepository.findAdmins
CREATE INDEX idx_users_admin ON users (role, deleted_at, full_name(100), created_at DESC);

-- Audit log search and timeline
-- Used by: AuditLogRepository.search
CREATE INDEX idx_audit_search ON audit_logs (action_type, created_at DESC, actor(50));

-- Inventory log feed
-- Used by: InventoryRepository.listRecent
CREATE INDEX idx_inventory_feed ON inventory_logs (product_id, created_at DESC, action);

-- Password reset token lookup
-- Used by: PasswordResetRepository.findByToken
CREATE INDEX idx_password_reset_token ON password_reset_tokens (token_hash, used_at, expires_at);

-- ------------------------------------------------------------
-- FULL-TEXT SEARCH INDEXES (for enhanced search)
-- ------------------------------------------------===========

-- Full-text search on product names and descriptions
-- Requires: InnoDB with FULLTEXT support (MySQL 5.6+)
CREATE FULLTEXT INDEX idx_ft_products_search ON products (name, description);

-- Full-text search on reviews
CREATE FULLTEXT INDEX idx_ft_reviews_search ON reviews (title, review_text);

-- ------------------------------------------------------------
-- PARTIAL INDEXES (MySQL 8.0+ using functional indexes)
-- -----------------------------------------------------------
-- NOTE: Standard MySQL doesn't support PostgreSQL-style WHERE clauses in CREATE INDEX.
-- The following functional indexes provide similar filtering capabilities.
-- MySQL functional indexes cannot refer to auto-increment columns.

-- Index only active products (non-deleted, non-discontinued)
-- This reduces index size and improves query performance
CREATE INDEX idx_products_active ON products ((CASE WHEN deleted_at IS NULL AND status <> 'DISCONTINUED' THEN 1 END));

-- Index only pending orders for admin dashboard
-- Using total_amount instead of order_id (auto-increment) to avoid MySQL restriction
CREATE INDEX idx_orders_pending ON orders ((CASE WHEN status = 'PENDING' THEN total_amount END));

-- Index only pending reviews for moderation
-- Using created_at instead of review_id (auto-increment) to avoid MySQL restriction
CREATE INDEX idx_reviews_pending ON reviews ((CASE WHEN status = 'PENDING' THEN created_at END));

-- ------------------------------------------------------------
-- ANALYTICS AND REPORTING INDEXES
-- ------------------------------------------------------------

-- Revenue calculation by date range
CREATE INDEX idx_orders_revenue ON orders (order_date, status, total_amount);

-- Items sold analytics
CREATE INDEX idx_order_items_analytics ON order_items (product_id, quantity, subtotal);

-- Product performance metrics
CREATE INDEX idx_products_metrics ON products (stock_quantity, price, status, created_at);

-- Customer activity analytics
CREATE INDEX idx_users_activity ON users (last_active_at DESC, role, created_at);

-- ------------------------------------------------------------
-- INDEX STATISTICS UPDATE
-- -----------------------------------------------------------

-- Update index statistics for better query planning
ANALYZE TABLE products;
ANALYZE TABLE orders;
ANALYZE TABLE order_items;
ANALYZE TABLE cart_items;
ANALYZE TABLE users;
ANALYZE TABLE reviews;
ANALYZE TABLE audit_logs;
ANALYZE TABLE inventory_logs;
