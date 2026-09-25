-- ============================================================
-- Migration: add SUPER_ADMIN role (privilege hierarchy)
--
-- 1. Extend the users.role enum with SUPER_ADMIN.
-- 2. Promote the original 'admin' account to SUPER_ADMIN (the owner).
--    The seed account is the top of the hierarchy; only it may create
--    or demote admins from now on (enforced in UserService).
-- ============================================================

ALTER TABLE users
    MODIFY role ENUM('SUPER_ADMIN', 'ADMIN', 'CUSTOMER') NOT NULL DEFAULT 'CUSTOMER';

UPDATE users
SET role = 'SUPER_ADMIN'
WHERE username = 'admin'
  AND role = 'ADMIN';
