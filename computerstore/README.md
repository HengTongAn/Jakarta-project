# Apach_PC/STORE

A single-WAR Java EE storefront for a computer shop. Customers browse a
catalogue, add products to a cart and check out; administrators manage the
catalogue, inventory, orders, users and payment configuration.

No SPA, no build-time code generation, no message broker, no second process.
Persistence is hand-written JDBC — there is no ORM.

## Stack

| | |
|---|---|
| Language | Java 17 |
| Servlet API | Jakarta Servlet 6.1 |
| View layer | JSP + JSTL, served from `WEB-INF/views` |
| Persistence | Hand-written JDBC over a HikariCP pool |
| Database | MySQL 8.4 |
| Build | Maven — `com.hengtongan:computerstore:1.0-SNAPSHOT`, `war` packaging |
| Runtime | Apache Tomcat 10.1+ (verified on 11.0.26), context `/computerstore` |
| Tests | JUnit 5.10.2 + Mockito — 235 tests |
| Front end | Bootstrap 5, Bootstrap Icons, Chart.js — all vendored, no CDN |

## Quick start

Full detail in [docs/deployment/local-development.md](docs/deployment/local-development.md).
This is the short version.

**1. Prerequisites** — JDK 17 on `PATH`, Maven, a running MySQL 8.4, and a
Tomcat 10.1+ install.

**2. Database.** The schema comes from two places and the app needs both:

```bash
# database + user, with DDL rights
mysql -u root -p -e "CREATE DATABASE computer_store CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
                    CREATE USER 'store_user'@'localhost' IDENTIFIED BY '<password>';
                    GRANT ALL PRIVILEGES ON computer_store.* TO 'store_user'@'localhost';"

# base tables — 15 of them, including users/products/orders/cart_items
mysql -h localhost -u store_user -p computer_store < src/main/resources/db/schema.sql
```

Migrations alone will **not** work. No migration creates `users`, `products`,
`orders` or `cart_items` — those exist only in `schema.sql`.

**3. Migrations.** `computerstore.migration.autoRun` defaults to `false`, so
nothing runs unless you ask for it. Start Tomcat once with

```
-Dcomputerstore.migration.autoRun=true
```

to apply the 18 files in `src/main/resources/db/migrations/` in the order
hard-coded in `DatabaseMigrationRunner.discoverMigrations()`. That order is not
alphabetical and is not optional.

**4. Seed** (optional, but an empty catalogue is a poor demo):

```bash
mysql -h localhost -u store_user -p computer_store < src/main/resources/db/seed/seed-data.sql
```

This seeds `admin` / `admin123` and `customer` / `customer123`. **Change both
passwords before the instance is reachable by anything else.**

**5. Configure.** Credentials are environment variables only — there is no
committed fallback. Copy `.env.example` to `.env` and fill it in; Java does not
read `.env` itself, so export the values into the process environment that
starts Tomcat (`setenv.sh`, or your IDE's launch configuration).

```bash
cp .env.example .env    # then edit, then:  set -a; . ./.env; set +a
```

**6. Build and deploy.**

```bash
mvn -o clean package     # produces target/computerstore.war
```

Deploy the WAR to Tomcat and open <http://localhost:8080/computerstore/>.
`/health` is a liveness/readiness probe if you want to check it booted.

## Tests

```bash
mvn -o test
```

235 tests, no database and no network required — the suite mocks the repository
layer. Several tests are source lints that read the JSP, CSS and `web.xml`
sources as text and assert invariants over them (JSP tag balance, async-support
declarations, asset versioning, CSS gutter symmetry). Those are explained in
[docs/development/source-linting.md](docs/development/source-linting.md).

## Documentation

[docs/README.md](docs/README.md) is the index. The three places the engineering
is concentrated are the filter chain, the payment subsystem, and the source-lint
tests.

| I want to… | Read |
|---|---|
| Understand the system shape | [docs/architecture/overview.md](docs/architecture/overview.md) |
| Trace what happens on a request | [docs/architecture/request-lifecycle.md](docs/architecture/request-lifecycle.md) |
| Understand payments | [docs/architecture/payments.md](docs/architecture/payments.md) |
| Find the endpoint for a page | [docs/api/README.md](docs/api/README.md) |
| Look up a table or column | [docs/database/schema.md](docs/database/schema.md) |
| Add or debug a migration | [docs/database/migrations.md](docs/database/migrations.md) |
| Deploy a WAR | [docs/deployment/tomcat-deployment.md](docs/deployment/tomcat-deployment.md) |
| Change a setting | [docs/deployment/configuration.md](docs/deployment/configuration.md) |
| Audit the security posture | [docs/security/README.md](docs/security/README.md) |
| See what is still open | [docs/security/known-issues.md](docs/security/known-issues.md) |

## Known gaps

These are recorded rather than hidden; the full list with post-mortems is in
[docs/security/known-issues.md](docs/security/known-issues.md).

- Card payment is simulation only — there is no acquirer integration, and the
  card table physically cannot hold a full PAN.
- The ABA Payway path has never been executed against the real gateway.
- Four footer social links ship as empty placeholders.
- The seed data has cost-10 BCrypt hashes while new passwords are written at
  cost 12. Old rows still verify; nothing re-hashes them on login.
