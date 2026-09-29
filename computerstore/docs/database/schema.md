# Schema

20 tables in the live database, 15 of them in `schema.sql`. The two lists differ
because migrations accumulate. `payments`, `app_settings` and
`password_reset_tokens` are created by a migration and by nothing else;
`schema_migrations` is the runner's own bookkeeping table, created by
`DatabaseMigrationRunner` rather than by a migration file. `user_preferences` is
the odd one out: it exists in the live database but in neither `schema.sql` nor
any migration, and no code reads it (see its entry below).

The live database is also **not** a reproducible record of what has run. Two
names in its `schema_migrations` table have no file in
`src/main/resources/db/migrations/` at all:
`migration_drop_broken_review_pending_index.sql` and
`migration_add_settings.sql`. The runner keys on the file name and skips
anything already recorded, so it can neither re-apply them nor notice they are
gone. A fresh install built from `schema.sql` + the 18 files on disk therefore
does not match the live database, and the difference is invisible from the repo.

When a table is missing, check `schema_migrations` before assuming the migration
has not run. See
[local-development.md](../deployment/local-development.md#database) for the
load order a fresh install needs.

## Entity relationships

```
                          ┌──────────────┐
                          │    users     │──┬──▶ password_reset_tokens
                          │  user_id PK  │  └──▶ two_factor_secrets
                          └──────┬───────┘
                                 │ 1
        ┌────────────────────────┼────────────────────────┬─────────────────┐
        │ N                      │ N                      │ N               │ N
┌───────▼────────┐        ┌──────▼───────┐        ┌───────▼────────┐  ┌─────▼──────────┐
│   cart_items   │        │    orders    │        │ inventory_logs │  │  mail_messages │
│ quantity > 0   │        │  order_id PK │        └───────┬────────┘  │ sender_id and │
└───────┬────────┘        └──────┬───────┘                │           │ recipient_id  │
        │ N                     │ 1                      │ product_id  │ both FK users  │
        │                       ├──────────────┬─────────┴──────────┘
        │                       │              │                      │
        │                       │              │                      │
┌───────▼────────┐              │ N            │ N                    │
│    products    │◀─────────────┤              │                      │
│  product_id PK │              │              │                      │
└───┬──────┬─────┘              │              │                      │
    │ N    │ N                  │              │                      │
┌───▼───┐  │  ┌──────────────┐  │              │                      │
│ brands│  │  │ product_specs│  │              │                      │
└───────┘  │  └──────────────┘  │              │                      │
           │  ┌──────────────┐  │              │                      │
           └──┤   reviews    │◀─┘ (user_id)    │                      │
              └──────────────┘                  │                      │
                                                 └──────────────────────┘
                     ┌──────────────┐
                     │    orders    │
                     └──────┬───────┘
                            │ 1
        ┌───────────────────┼───────────────────┬────────────────────┐
        │ N                 │ N                 │ N                  │ N
┌───────▼────────┐ ┌───────▼───────┐ ┌─────────▼──────┐ ┌───────────▼─────────┐
│  order_items   │ │order_status_  │ │   payments    │ │  order_status_      │
│ quantity > 0   │ │   events      │ │ amount, card_  │ │  events (1:N)       │
└────────────────┘ └───────────────┘ │ brand, last4  │ └─────────────────────┘
                                     └────────────────┘

Standalone: app_settings (key/value), schema_migrations (runner bookkeeping),
audit_logs + audit_logs_archive (append-only history).
```

## Tables

### `users`
`user_id` PK · `username` · `password_hash` · `full_name` · `email` ·
`avatar_url` · `role ENUM('SUPER_ADMIN','ADMIN','CUSTOMER')` · `last_active_at` ·
`created_at` · `deleted_at` · `deleted_by` · `delete_reason`

Keyed on `user_id`, `username`, `email`, `role`, `last_active_at`, `deleted_at`.
`password_hash` is BCrypt at cost 12 (`PasswordUtil.BCRYPT_COST`) — a hash, never
a password. The cost is a constant rather than a constructor argument so it
cannot drift per call site. Raising it only affects newly written hashes: each
hash carries its own cost in the `$2a$12$` prefix, so rows written earlier (the
seed data is still cost 10) keep verifying unchanged. Nothing re-hashes them on
login — an old row stays at its original cost until that user resets their
password.

Soft delete: `deleted_at` is null for live rows. `SessionUserRefreshFilter` ends a
session whose user has been soft-deleted.

### `products`
`product_id` PK · `category_id` FK · `brand_id` FK · `name` · `sku` · `description` ·
`highlights` · `price DECIMAL(10,2)` · `stock_quantity` · `box_contents` ·
`warranty_info` · `source_url` · `image_url` ·
`status ENUM('IN_STOCK','LOW_STOCK','OUT_OF_STOCK','DISCONTINUED')` ·
`created_at` · `updated_at` · `deleted_at` · `deleted_by` · `delete_reason`

Two `CHECK`s: `price >= 0` and `stock_quantity >= 0`. Both are real backstops
against a negative value from a bad arithmetic path.

### `orders`
`order_id` PK · `user_id` FK · `order_date` · `total_amount DECIMAL(10,2)` ·
`status ENUM('PENDING','PROCESSING','SHIPPED','COMPLETED','CANCELLED','REFUNDED')` ·
`payment_method VARCHAR(20)` · `payment_provider VARCHAR(20)` ·
`payment_status VARCHAR(20)` · `payment_transaction VARCHAR(64)` · `paid_at`

`total_amount` is a **snapshot**, not a join to `order_items`. The line items
are copied at checkout so a later price change cannot rewrite history.

### `order_items`
`order_item_id` PK · `order_id` FK `CASCADE` · `product_id` FK ·
`quantity` · `unit_price DECIMAL(10,2)` · `subtotal DECIMAL(10,2)`

`unit_price` is also a snapshot, for the same reason. `CHECK (quantity > 0)`.

### `order_status_events`
`event_id` PK · `order_id` FK `CASCADE` · `from_status` · `to_status` · `changed_by` ·
`note` · `created_at`

The order status history. Both status columns use the same six-value enum.

### `payments`
`payment_id` PK · `order_id` FK `NO ACTION` · `provider VARCHAR(20)` ·
`transaction_id VARCHAR(64)` · `amount DECIMAL(10,2)` · `currency VARCHAR(3)` ·
`status VARCHAR(20)` · `message` · `qr_image MEDIUMTEXT` · `aba_phone VARCHAR(32)` ·
`created_at` · `updated_at` · `card_brand VARCHAR(20)` · `card_last4 VARCHAR(4)`

One row **per payment attempt**, not per order — a card can be declined three
times and leave three rows, all attached to one order. `NO ACTION` on
`order_id` is deliberate: payments are financial records and should not vanish
because an order was deleted.

`card_last4` is `VARCHAR(4)` and that is the enforcing limit. See
[payment-data.md](payment-data.md).

### `cart_items`
`cart_item_id` PK · `user_id` FK `CASCADE` · `product_id` FK `NO ACTION` · `quantity` ·
`added_at`. `CHECK (quantity > 0)`.

### `reviews`
`review_id` PK · `product_id` FK `CASCADE` · `user_id` FK `CASCADE` · `rating TINYINT` ·
`title` · `review_text` · `status ENUM('PENDING','APPROVED','REJECTED')` ·
`is_verified` · `created_at` · `updated_at`. `CHECK (rating BETWEEN 1 AND 5)`.

Reviews start `PENDING`; `ReviewCountFilter` surfaces the queue to admins.

### `app_settings`
`setting_key` PK · `setting_value VARCHAR(500)` · `updated_at`

The runtime-editable configuration store. `setting_value` is `VARCHAR(500)`, so
a value must be short — a path, a URL, `true`/`false`, an id. It is not a place
for a secret. See [../deployment/configuration.md](../deployment/configuration.md).

### `audit_logs`
`audit_id` PK · `action_type ENUM('AUTH','ADMIN','DATA','SECURITY','SYSTEM')` ·
`action_name` · `actor` · `resource_type` · `resource_id` · `details` ·
`ip_address` · `request_id` · `session_id` · `created_at`

`request_id` and `session_id` are what `RequestAuditContextFilter` seeds, and
what make a session's activity reconstructable. See
[../security/audit-logging.md](../security/audit-logging.md).

### `audit_logs_archive`
The same shape plus `archive_id` and `original_audit_id`, plus `archived_at`.
Written by Admin → History → Archive.

### `two_factor_secrets`
`id` PK · `user_id` FK `CASCADE` · `secret_key` · `enabled` · `created_at` · `updated_at`

`secret_key` is the TOTP secret, **encrypted**. The key comes from
`COMPUTERSTORE_2FA_ENCRYPTION_KEY` or the `computerstore.2fa.encryption.key`
system property. If it is absent the service refuses to provision rather than
storing plaintext.

### `password_reset_tokens`
`token_id` PK · `user_id` FK `CASCADE` · `token_hash CHAR(64) UNIQUE` · `expires_at` · `used_at` · `created_at`

One row per forgotten-password request. Only the SHA-256 **hash** of the token is
stored; the raw token exists solely in the emailed link, so reading this table
does not let anyone take over an account. Tokens expire and are single-use
(`used_at`), and `idx_reset_token_expiry` exists so expired rows can be swept
by age instead of being scanned. Added by
`migration_add_password_reset_tokens.sql`.

### `mail_messages`
`message_id` PK · `sender_id` FK · `recipient_id` FK · `subject` · `body` · `read_flag` · `created_at`

The in-app mailbox behind "Contact support". One row per message and no thread
column: a reply is a new row whose subject is the original's prefixed `Re: `,
which is what lets the inbox and the sent folder be one table queried two ways.
`read_flag` rather than a `read_at` timestamp, because the badge and
mark-all-read only ever ask "is this unread". Both foreign keys are `NO ACTION`,
not `CASCADE`: every query inner-joins both users, so a message whose sender or
recipient was hard-deleted is already invisible, and users are soft-deleted in
practice. Added by `migration_add_mail_messages.sql`.

### `user_preferences` — vestigial, not in `schema.sql`
`user_id` PK/FK · `notify_order_placed` · `notify_order_status` · `updated_at`

Present in the live database but created by no migration and read by no code —
a leftover from an earlier notification design that in-app mail replaced. It is
listed here because the table really exists and a `SHOW TABLES` will show it,
but a fresh install from `schema.sql` will not have it, and nothing will miss
it. Safe to `DROP TABLE user_preferences;` if you want the two in sync.

### `product_specs`
`spec_id` PK · `product_id` FK `CASCADE` · `spec_key` · `spec_value` · `sort_order` · `created_at`

Key/value spec rows, edited by `product-spec-editor.js`.

### `inventory_logs`
`log_id` PK · `product_id` FK · `old_quantity` · `new_quantity` · `action` · `user_id` FK · `created_at`

### `brands`, `categories`
`brand_id`/`category_id` PK · `name` · `description` · `created_at` · `deleted_at` ·
`deleted_by` · `delete_reason`. Both soft-deleted.

### `schema_migrations`
`id` PK · `migration_name` · `applied_at`. The runner's bookkeeping. Do not edit
by hand except to record a migration you applied yourself — see
[migrations.md](migrations.md).

## Delete rules

| Rule | Where | Why |
|---|---|---|
| `CASCADE` | `cart_items.user_id`, `order_items.order_id`, `order_status_events.order_id`, `product_specs.product_id`, `reviews.*`, `two_factor_secrets.user_id`, `password_reset_tokens.user_id` | Genuinely dependent rows with no independent meaning. |
| `NO ACTION` | `orders.user_id`, `payments.order_id`, `order_items.product_id`, `cart_items.product_id`, `products.category_id`, `products.brand_id`, `inventory_logs.*`, `mail_messages.*` | The dependent row is a record, not a child. A product or user is soft-deleted instead, and a payment outlives its order. |

## Two things that will bite you

**Deleting a user mid-session leaves a stale `user_id` in the running
container.** The connection pool and session cache hold the old id, so every
subsequent cart insert fails on `fk_cart_user` even though login appears to
work. Restart Tomcat, or reuse the user rather than recreating it. This has
invalidated test runs before.

**`RepositoryColumnCoverageTest` is the safety net for column drift.** Hand-written
SQL means a renamed column is a runtime error. The test asserts that the column
names repositories declare still exist.
