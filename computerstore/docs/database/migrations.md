# Migrations

`infrastructure/persistence/DatabaseMigrationRunner.java` applies, in a fixed
order, the file names listed in its `discoverMigrations()` method, skipping any
already recorded in `schema_migrations`.

**The list is hard-coded, not a directory scan.** Dropping a new file into
`db/migrations/` does nothing until it is also added to `discoverMigrations()`.
That is deliberate: the list is the execution order, and directory order is not
a dependency order. `DatabaseMigrationRunnerTest` fails if the list and the
files on disk disagree.

Controlled by:

| Property | Default | Effect |
|---|---|---|
| `computerstore.migration.autoRun` | `false` | Run migrations at context startup. Off by default; enabling it still will not create the base tables, which come only from `schema.sql`. |
| — | — | A migration that fails always aborts startup. The runner rethrows anything that is not an "already applied" error (`DatabaseMigrationRunner.executeMigration`), so there is no flag to turn this off. |

## Writing one

1. Name it `migration_<what_it_does>.sql`. There is no numbering, so the name is
   the only ordering hint; prefix related files so they sort together.
2. Add it to `DatabaseMigrationRunner.discoverMigrations()` at the position its
   dependencies allow. `DatabaseMigrationRunnerTest` fails if the list and the
   files on disk disagree, so this is not optional.
3. Make it **idempotent** where you can: `IF NOT EXISTS`, `IF EXISTS`,
   `information_schema` guards. The runner tolerates per-statement "already
   applied" errors, but relying on that is worse than writing the guard.
4. Put a comment at the top saying what it does and, if it must be run by hand
   against an existing database, say so explicitly. Several of the older
   migrations do exactly that in their header.
5. Never edit a migration that has already been applied. Add a new one. The
   runner keys on the file name, so an edited file will not re-run and the
   change will be silently absent.

## The eighteen migrations

| File | Adds |
|---|---|
| `migration_add_2fa.sql` | `two_factor_secrets` |
| `migration_add_app_settings.sql` | `app_settings` |
| `migration_add_audit_archive.sql` | `audit_logs_archive` |
| `migration_add_audit_correlation.sql` | `request_id`, `session_id` on `audit_logs` |
| `migration_add_card_payments.sql` | `payments.card_brand`, `payments.card_last4`, `chk_payments_card_last4` |
| `migration_add_image_url.sql` | `products.image_url` |
| `migration_add_indexes.sql` | Supporting indexes |
| `migration_add_mail_messages.sql` | `mail_messages` |
| `migration_add_order_lifecycle.sql` | `order_status_events`, order status columns |
| `migration_add_password_reset_tokens.sql` | `password_reset_tokens` |
| `migration_add_payments.sql` | `payments`, order payment columns |
| `migration_add_product_details.sql` | `products.highlights`, `box_contents`, `warranty_info` |
| `migration_add_reviews.sql` | `reviews` |
| `migration_add_soft_delete.sql` | `deleted_at`/`deleted_by`/`delete_reason` across tables |
| `migration_add_super_admin_role.sql` | `SUPER_ADMIN` to the role enum |
| `migration_add_trending_index.sql` | Covering index for Trending now |
| `migration_advanced_performance_indexes.sql` | Further covering indexes |
| `migration_performance_indexes.sql` | Admin/user query indexes (MySQL 8.x) |

## Idempotency is partial — know where the edge is

The runner tolerates a per-statement "already applied" error and **then records
the migration anyway**. That makes a hand-applied-but-unrecorded migration
survivable, which is the case that actually happens.

It is not a general transaction, and it is not atomic across statements. A
migration that fails halfway leaves the earlier statements applied and the file
unrecorded. Re-running will hit the already-applied statements — which the
tolerated-error path absorbs — and then the ones that genuinely did not apply.

### MySQL error codes the runner tolerates

| Code | Meaning |
|---|---|
| 1060 | Duplicate column name |
| 1061 | Duplicate key name |
| 1062 | Duplicate entry (usually a unique index on a backfill) |
| 1048 | Column cannot be null |
| 3822 | **Duplicate check constraint name** |

3822 is MySQL 8.4's code for "duplicate check constraint name". It was added
after reproducing the exact failure: a migration that had added its columns and
its `CHECK` but was never recorded, so the next boot re-ran the file and died on
the constraint name. Both the code and a message match are in the tolerated set,
so the app now boots and records the migration.

**If you add a migration that can produce a different "already done" error, add
that code here too, and reproduce the failure before claiming it works.** This
runner has been hardened twice, and both times the first attempt at "verified"
was invalid — once because a probe failed on a foreign key before it reached the
constraint it was meant to test.

## Running by hand

```bash
# what has been applied
mysql -h localhost -u "$DB_USERNAME" -p "$DB" -e "SELECT * FROM schema_migrations ORDER BY id;"

# what the runner believes exists
ls src/main/resources/db/migrations/

# apply a single file yourself, then record it so the runner does not re-run it
mysql -h localhost -u "$DB_USERNAME" -p "$DB" < src/main/resources/db/migrations/migration_foo.sql
mysql -h localhost -u "$DB_USERNAME" -p "$DB" -e \
  "INSERT INTO schema_migrations (migration_name) VALUES ('migration_foo.sql');"
```

Recording it yourself is the step people forget. If you do not, the next boot
re-runs the file and you will meet the tolerated-error path.

`DatabaseMigrationRunnerTest` covers the runner's behaviour. When you change the
tolerated-error set, extend that test.

## Checking a change did what you meant

Compare the schema before and after rather than trusting the file:

```bash
mysql -h localhost -u "$DB_USERNAME" -p "$DB" -e "SHOW CREATE TABLE payments\G"
```

`migration_add_card_payments.sql` is the worked example: the file adds
`card_brand` and `card_last4` and a `CHECK`, but what actually enforces the
four-character limit is the column type. See [payment-data.md](payment-data.md).
