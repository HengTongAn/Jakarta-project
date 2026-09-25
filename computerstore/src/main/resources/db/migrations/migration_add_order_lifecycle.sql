-- ============================================================
-- Migration: order lifecycle (status + history timeline)
--  1. Extend orders.status with SHIPPED and REFUNDED.
--  2. Add order_status_events to record a timestamped history of
--     every state change (the "timeline" shown to admin & customer).
--  3. Backfill a creation event for existing orders.
-- ============================================================

ALTER TABLE orders
    MODIFY status ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED')
        NOT NULL DEFAULT 'PENDING';

CREATE TABLE IF NOT EXISTS order_status_events (
    event_id    INT AUTO_INCREMENT PRIMARY KEY,
    order_id    INT NOT NULL,
    from_status ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED') NULL,
    to_status   ENUM('PENDING', 'PROCESSING', 'SHIPPED', 'COMPLETED', 'CANCELLED', 'REFUNDED') NOT NULL,
    changed_by  VARCHAR(50) NULL,
    note        VARCHAR(255) NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ose_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE,
    INDEX idx_ose_order (order_id)
) ENGINE = InnoDB;

INSERT INTO order_status_events (order_id, from_status, to_status, changed_by, note)
SELECT order_id, NULL, status, 'SYSTEM', 'Order placed'
FROM orders;
