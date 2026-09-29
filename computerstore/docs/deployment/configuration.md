# Configuration

There are three layers, and they are not interchangeable. Knowing which one a
setting lives in tells you what changing it costs.

| Layer | Where | Changing it needs | Read by |
|---|---|---|---|
| Environment variable | process environment | a restart | `AppConfig` |
| System property | `-D` on the JVM | a restart | `AppConfig`, some filters directly |
| Bundled properties file | `config/db.properties`, `config/mail.properties` | a restart, and a rebuild to repackage | `AppConfig` |
| `app_settings` table | MySQL | **nothing** — takes effect within the cache TTL | `PaymentConfig`, `SupportChannelService` |

## Giving Tomcat the environment

Tomcat does **not** read `.env`. Exporting it in your shell only helps if that
same shell starts Tomcat:

```bash
set -a; . ./.env; set +a
/opt/tomcat/bin/startup.sh
```

Under a service manager or the Tomcat service wrapper, put the variables in the
unit's `Environment=` lines instead — otherwise a restart from anywhere else
comes up without the database credentials and fails on the first query.

`-D` system properties belong in `CATALINA_OPTS`.

## Layer 1–3: `AppConfig`

One resolution function, with this precedence:

```
environment variable  →  system property  →  bundled properties file  →  default
```

`AppConfig.get(envVar, propertyKey, defaultValue)` — pass `null` for either
variable name to skip that layer. The first non-blank value wins and is trimmed.

The precedence function `AppConfig.resolve(...)` is package-private so
`AppConfigTest` can exercise every arm without mutating the process environment.

### Keys read through `AppConfig`

| Env var | Property | Default | Purpose |
|---|---|---|---|
| `DB_URL` | `db.url` | none | Full JDBC URL. |
| `DB_USERNAME` | `db.username` | none | |
| `DB_PASSWORD` | `db.password` | none | |
| `DB_POOL_MAX` | `db.pool.max` | `50` | HikariCP maximum pool size. |
| `DB_POOL_MIN` | `db.pool.min` | `10` | HikariCP minimum idle. |
| `COMPUTERSTORE_2FA_ENCRYPTION_KEY` | `computerstore.2fa.encryption.key` | none | Encrypts TOTP secrets. |

`DB_URL` is a full JDBC URL, not a database name.

### System properties read directly

Not through `AppConfig` — read with `System.getProperty` at their own call site.

| Property | Default | Effect |
|---|---|---|
| `computerstore.session.cookie.secure` | `false` | Forces `Secure` on cookies even on a plain-HTTP connector. Set `true` behind a TLS-terminating proxy. |
| `security.hsts.enabled` | `false` | Adds `Strict-Transport-Security`. |
| `computerstore.compression.enabled` | `true` | Disables the gzip filter. |
| `computerstore.migration.autoRun` | `true` | Runs migrations at startup. |
| `computerstore.migration.failOnError` | `false` | Aborts startup on a migration error. |
| `computerstore.cache.enabled` | `true` | Disables the cache layer. |
| `computerstore.monitoring.enabled` | `true` | Disables query monitoring. |
| `computerstore.trust-forwarded-headers` | `false` | **Security-relevant.** Trusts `X-Forwarded-For` for rate limiting and audit IPs. Only enable behind a proxy you control. |
| `computerstore.audit.database.enabled` | — | Writes audit rows to MySQL rather than only the log file. |
| `computerstore.pool.preWarm` | `false` | Fills the connection pool at startup. |
| `computerstore.upload.dir` | `$HOME/computerstore-uploads` | Upload directory. Env: `COMPUTERSTORE_UPLOAD_DIR`. |

`computerstore.trust-forwarded-headers` deserves a second look. With it on and
no proxy in front, a client can set `X-Forwarded-For` and evade per-IP rate
limiting while forging audit records.

## Layer 4: `app_settings`

Runtime-editable, no restart, no rebuild. Read through a small repository with a
short cache TTL, so a change takes effect within that window rather than
instantly.

| Key | Meaning |
|---|---|
| `store_name` | Name in the header and page titles. |
| `mail_notifications_enabled` | Master switch for order email. |
| `payment.aba.enabled` | Offer ABA Payway. |
| `payment.aba.simulate` | Answer locally instead of calling the gateway. |
| `payment.aba.api_url` | Gateway base URL. |
| `payment.aba.merchant_id` | Merchant identifier. |
| `payment.aba.username` | Gateway username. |
| `payment.aba.shop_name` | Name shown to the customer. |
| `payment.aba.currency` | Transaction currency. |
| `payment.card.enabled` | Offer card payment. |
| `payment.card.simulate` | Authorise locally instead of calling an acquirer. |
| `support.facebook.url` | Footer social link. |
| `support.messenger.url` | Footer social link. |
| `support.telegram.url` | Footer social link. |
| `support.x.url` | Footer social link. |

`setting_value` is `VARCHAR(500)`. It is not a place for a secret: the value is
rendered into pages and is readable by anyone who can log in as an admin.

### Two settings that are not in the table

`payment.aba.secret` and `COMPUTERSTORE_2FA_ENCRYPTION_KEY` are resolved but
**never** written through the admin UI. `AdminPaymentsServlet` reports only
whether a secret is *set*, as `secretSet`, and the form has no field for it. Keep
it that way — an admin form that round-trips a secret is a secret in a database
that the admin list page can read.

### `payment.aba.simulated_result_<orderId>`

A per-order override: set the outcome for a specific simulated payment so a
screenshot or a test can show a decline. `PaymentService.setSimulatedResult`
writes one. These accumulate; the one currently present is tied to a real paid
order, so it is inert but is not junk.

## The availability rule

A payment option is offered only when it is **enabled and actually chargeable**:

```java
isCardAvailable() == isCardEnabled() && (isCardSimulated() || isCardReady())
```

`isCardReady()` is a hard-coded `false` today, because there is no acquirer
integration. So `payment.card.enabled=true` with `payment.card.simulate=false`
**withholds** the card option instead of offering something that cannot work.
`cardBlockingReason()` explains why, and `/admin/payments` shows it.

For ABA, `isReady()` requires `merchant_id`, `username`, and `secret` to all be
present — which is why only the simulated path has been exercised.

## Changing a setting

| To change | Do this | Effective |
|---|---|---|
| A payment switch | `/admin/payments` | Within the cache TTL |
| Footer social links | `/admin/support` | Within the cache TTL |
| `store_name` | `app_settings` row | Within the cache TTL |
| DB credentials | `.env` and/or `config/db.properties` | Restart |
| A filter behaviour | System property in `CATALINA_OPTS` | Restart |
| The schema | Add a migration | Next startup |

## Things not to do

- **Do not commit `.env`.** It is git-ignored, and a database password has zero
  occurrences in history. Keep it that way.
- **Do not put a secret in `app_settings`.** `VARCHAR(500)`, rendered into pages.
- **Do not enable `computerstore.trust-forwarded-headers` without a proxy.**
- **Do not use `payment.card.simulate=false` expecting real card payments.**
  There is nothing behind it.
- **Do not use `payment.aba.simulate=false` without merchant credentials and a
  check of the real endpoint paths and field names.** The live ABA path has never
  been executed against the real gateway; `transId` versus `TRANS_ID`, the
  header names, and the currency all need confirming against ABA's documentation
  first.
