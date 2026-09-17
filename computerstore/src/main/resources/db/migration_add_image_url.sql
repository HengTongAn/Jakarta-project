-- ============================================================
-- Migration: Add image_url column to products table
-- Run this on existing databases to add image support
-- ============================================================

USE computer_store;

-- Add image_url column if it doesn't exist
ALTER TABLE products 
ADD COLUMN IF NOT EXISTS image_url VARCHAR(500) AFTER stock_quantity;
