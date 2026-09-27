-- Payment support: method + status on the order, and a per-attempt record.
--
-- The order row is the customer-visible truth ("was this paid?"), so the method
-- and its status live there where every existing order query already sees them.
-- The payments table is the append-only attempt history: a customer can retry
-- after a failure, and the gateway callbacks are matched against it, so it needs
-- to hold more than one row per order.
--
-- payment_method / payment_status are VARCHAR rather than ENUM on purpose. The
-- order status column is an ENUM, but payment providers arrive over time and an
-- ENUM silently rejects an unknown value; a VARCHAR plus the validation in
-- PaymentService keeps an unmapped provider visible instead of fatal.
--
-- Registered in DatabaseMigrationRunner.discoverMigrations().
ALTER TABLE orders
  ADD COLUMN payment_method      VARCHAR(20)  NOT NULL DEFAULT 'COD',
  ADD COLUMN payment_provider    VARCHAR(20)  NULL,
  ADD COLUMN payment_status      VARCHAR(20)  NOT NULL DEFAULT 'UNPAID',
  ADD COLUMN payment_transaction VARCHAR(64)  NULL,
  ADD COLUMN paid_at             TIMESTAMP    NULL;

CREATE TABLE IF NOT EXISTS payments (
  payment_id    INT AUTO_INCREMENT PRIMARY KEY,
  order_id      INT          NOT NULL,
  provider      VARCHAR(20)  NOT NULL,
  transaction_id VARCHAR(64) NULL,
  amount        DECIMAL(10,2) NOT NULL,
  currency      VARCHAR(3)   NOT NULL DEFAULT 'USD',
  status        VARCHAR(20)  NOT NULL,
  message       VARCHAR(255) NULL,
  -- The gateway hands back a base64 PNG and hands it back only once, so it has
  -- to be stored or a page refresh leaves the customer with nothing to scan.
  -- MEDIUMTEXT rather than BLOB because it arrives already base64-encoded and is
  -- dropped straight into an <img src="data:...">.
  qr_image      MEDIUMTEXT   NULL,
  aba_phone     VARCHAR(32)  NULL,
  created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One live attempt per order is the common case (find the newest, re-confirm it).
CREATE INDEX idx_payments_order ON payments (order_id);

-- A duplicated transaction id would mean a gateway callback matched the wrong
-- order. Nothing should ever insert one, so let the database say so rather than
-- the payment silently attaching to the wrong customer. This unique index also
-- serves every lookup on transaction_id, so no separate plain index is needed.
CREATE UNIQUE INDEX uq_payments_transaction ON payments (transaction_id);
