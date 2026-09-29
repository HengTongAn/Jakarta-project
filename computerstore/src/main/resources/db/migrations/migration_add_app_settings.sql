-- Key/value store for runtime-editable site settings.
--
-- The development database already carried an `app_settings` table (holding
-- `mail_notifications_enabled` and a stale `store_name` row) that no code read
-- and that was absent from schema.sql -- so a fresh install would not have had
-- the table at all. This migration creates it where missing and seeds the four
-- contact-support channel keys the storefront footer renders.
--
-- Values are seeded EMPTY on purpose. A support link pointing at an account the
-- store does not own sends customers to a stranger; a visibly inactive chip does
-- not. Admins paste the real destinations at /admin/support.
--
-- IF NOT EXISTS plus INSERT IGNORE keeps this safe against the existing
-- development database, which already has the table and two unrelated rows that
-- must not be disturbed. Registered in DatabaseMigrationRunner.discoverMigrations().
CREATE TABLE IF NOT EXISTS app_settings (
  setting_key   VARCHAR(100) NOT NULL,
  setting_value VARCHAR(500) NOT NULL,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES
  ('support.facebook.url',  ''),
  ('support.messenger.url', ''),
  ('support.telegram.url', ''),
  ('support.x.url',         '');
