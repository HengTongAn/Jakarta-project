-- ============================================================
-- Migration: Add image_url column to products table
-- Run this on existing databases to add image support
-- ============================================================

-- Add image_url column (MySQL 8 does not support "ADD COLUMN IF NOT EXISTS";
-- the migration runner skips it gracefully with error 1060 when the column
-- already exists, e.g. on a fresh schema.sql install).
ALTER TABLE products
ADD COLUMN image_url VARCHAR(500) AFTER stock_quantity;
