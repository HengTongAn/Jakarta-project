# Computer Store Management System

A Jakarta EE storefront for a computer-hardware shop, built as a single WAR with
hand-written JDBC, server-rendered JSPs, and no framework. Customers browse a
seeded catalogue, build a cart and check out; admins manage the catalogue,
inventory, orders, users, reviews, payments and audit history from a separate
admin area.

Everything in the repository is derived from this codebase. Where a document
and the code disagree, the code is the bug — see
[computerstore/docs/README.md](computerstore/docs/README.md).

## What it does

**Storefront** — catalogue with search/filter/sort/paging, product detail with
specs and reviews, cart, checkout, order history, profile and security settings.

**Payments** — two real paths behind one checkout:

- *ABA Payway* (Cambodian QR gateway) via `AbaPaywayClient`, with an explicit
  simulation mode when no gateway secret is configured.
- *Card* — Luhn, expiry and CVC validation server-side via `CardValidator`.
  Only the brand and last four digits are ever stored; the full number is never
  passed to the service layer or written down.

**Accounts** — register, login, logout, forgotten-password reset by email, and
optional TOTP two-factor with an encrypted secret.

**In-app mail** — a Gmail-style mailbox between customers and admin, with
threaded replies, an unread badge that updates live, and a best-effort SMTP
notifier (`EmailUtil` skips sending when no credentials are configured, so a
fresh clone still works).

**Admin** — products, categories, brands, inventory with stock movement logs,
orders with a status timeline, users, review moderation, payments, reports,
performance dashboards, mail, and an audit-log history view.

**Platform work** — a 16-filter chain (encoding, static caching, compression,
audit context, security headers, session refresh, rate limiting, authentication,
admin authorization, CSRF, badge counts, support channels), live updates over
Server-Sent Events, a service worker for offline use, `/health` and `/metrics`
endpoints, query batching and a Caffeine-backed cache, and scheduled audit-log
archival.

## Stack

| | |
|---|---|
| Language | Java 17 |
| Servlet API | Jakarta Servlet 6.1 |
| Views | JSP + JSTL under `WEB-INF/views` |
| Persistence | Hand-written JDBC over a HikariCP pool |
| Database | MySQL 8.4, schema + 18 forward-only migrations |
| Build | Maven, WAR packaging (`computerstore.war`) |
| Tests | JUnit 5 + Mockito — 235 tests, 41 classes |
| Runtime | Apache Tomcat 10.1+ (verified on 11.0.26) |
| Front end | Bootstrap 5, Bootstrap Icons, Chart.js — all vendored, no CDN |

## Running it

Prerequisites: JDK 17, Maven, MySQL 8, Tomcat 10.1+.

```bash
cd computerstore

# 1. Create the database and a user with DDL rights (needed on first boot).
mysql -u root -p -e "CREATE DATABASE computer_store CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
                    CREATE USER 'store_user'@'localhost' IDENTIFIED BY '<password>';
                    GRANT ALL PRIVILEGES ON computer_store.* TO 'store_user'@'localhost';"

# 2. Optional but recommended: load the demo catalogue and accounts.
mysql -h localhost -u store_user -p computer_store < src/main/resources/db/seed/seed-data.sql

# 3. Credentials are environment variables only — nothing secret is committed.
cp .env.example .env && $EDITOR .env      # fill in DB_URL / DB_USERNAME / DB_PASSWORD
set -a; . ./.env; set +a

# 4. Build, test, deploy.
mvn -o clean package
/opt/tomcat/bin/startup.sh
```

Then open <http://localhost:8080/computerstore>.

Tomcat does not read `.env` on its own — put the same values in
`$CATALINA_BASE/bin/setenv.sh` (chmod 600) when deploying as a service. Full
details, including the tables the app creates for itself, are in
[docs/deployment/local-development.md](computerstore/docs/deployment/local-development.md)
and [docs/deployment/configuration.md](computerstore/docs/deployment/configuration.md).

### Demo accounts

If you loaded the seed file:

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | `SUPER_ADMIN` — the store owner |
| `customer` | `customer123` | `CUSTOMER` |

These are public by design and must be rotated before the store is reachable.
`DefaultCredentialsChecker` verifies every live account against them at startup
and logs an `ERROR` naming any that are still in use; the app refuses to look
clean until they are rotated. See
[docs/deployment/oracle-cloud.md](computerstore/docs/deployment/oracle-cloud.md)
for the go-live checklist.

## Tests

```bash
cd computerstore
mvn -o test                     # 235 tests, no database required
mvn -o test -Dtest=CardValidatorTest
```

The suite is JUnit 5 + Mockito only, so it runs offline against no live
database. Beyond the usual unit tests it includes source-lint tests that read
the JSPs and `web.xml` as text and fail on invariants that would otherwise only
break at runtime — balanced JSP tags, payment controls present on the checkout
panel, every `?v=`-versioned asset listed in the cache-busting array, and
`<async-supported>` declared on both the filter and its mapping for every filter
in the chain. See
[docs/development/source-linting.md](computerstore/docs/development/source-linting.md).

## Documentation

25 documents under [`computerstore/docs`](computerstore/docs/README.md), all
maintained against the code. Start with:

- [Architecture overview](computerstore/docs/architecture/overview.md) — system
  shape and package rules.
- [Request lifecycle](computerstore/docs/architecture/request-lifecycle.md) —
  what happens, filter by filter, on a page load.
- [Payments](computerstore/docs/architecture/payments.md) — the two payment
  paths and exactly what payment data is stored.
- [Security](computerstore/docs/security/README.md) — authentication, CSRF,
  headers, rate limiting, audit logging, and known issues.
- [API reference](computerstore/docs/api/README.md) — every endpoint and the
  servlet behind it.

## Layout

```
computerstore/
  docs/                      reference documentation
  src/main/java/             core (domain, repository, service), infrastructure, util, web
  src/main/resources/db/     schema.sql, migrations/, seed/seed-data.sql
  src/main/webapp/           WEB-INF/views (JSPs), assets (vendored CSS/JS), sw.js
  src/test/java/             41 test classes
```

The engineering worth reading first is concentrated in three places: the filter
chain (`web/filter/`), the payment subsystem (`infrastructure/payment/`,
`core/service/PaymentService.java`), and the source-lint tests
(`web/view/`, `web/config/`).
