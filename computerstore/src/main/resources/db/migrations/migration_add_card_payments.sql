-- Card payment support: a masked brand/last4 on the attempt.
--
-- There is deliberately NO column for a card number. Not "we chose not to write
-- it", but "the table cannot hold one":
--
--   * card_last4 is VARCHAR(4) with a CHECK constraint, so the database rejects
--     anything longer than four digits. A full PAN cannot be inserted even by a
--     future bug that tries to.
--   * The demo validator additionally refuses any number outside a fixed list of
--     published test numbers, so a real card number never reaches this table.
--
-- A real card integration must not extend this table. When an acquirer is wired
-- up, the browser tokenises the card with the gateway's own client-side SDK and
-- this application only ever receives an opaque token, which belongs in
-- transaction_id. The PAN is then never in this process at all.
--
-- Registered in DatabaseMigrationRunner.discoverMigrations().
ALTER TABLE payments
  ADD COLUMN card_brand  VARCHAR(20) NULL,
  ADD COLUMN card_last4  VARCHAR(4)  NULL;

-- The load-bearing limit is the column width: card_last4 is VARCHAR(4), so a
-- straight INSERT of a full card number fails with "Data too long for column"
-- (verified against MySQL 8.4). That is what actually stops it today.
--
-- The CHECK below cannot fire while the column stays VARCHAR(4) -- it is a
-- backstop, not the mechanism. It earns its place by surviving the obvious
-- future mistake: someone widening this column to hold a "fuller" mask would
-- silently remove the only limit, whereas the constraint would still refuse
-- anything past four characters and the ALTER would fail loudly instead.
ALTER TABLE payments
  ADD CONSTRAINT chk_payments_card_last4
  CHECK (card_last4 IS NULL OR CHAR_LENGTH(card_last4) <= 4);

-- One masked card per attempt needs no index: every lookup goes through order_id.
