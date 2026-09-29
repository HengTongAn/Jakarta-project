# Architecture overview

## What this is

A single-WAR Java EE storefront. Customers browse a catalogue, add products to a
cart, and check out. Administrators manage the catalogue, inventory, orders,
users, and the payment configuration. There is no SPA, no build-time code
generation, and no second runtime process.

```
                       ┌──────────────────────────────┐
   HTTP  ─────────▶    │Tomcat, context /computerstore│
                       └───────────────┬──────────────┘
                                      │
                       15 filters, in web.xml order
                                      │
                      ┌───────────────▼──────────────┐
                      │ Servlet (auth, admin, …)     │
                      └───────────────┬──────────────┘
                                      │
                      ┌───────────────▼──────────────┐
                      │ Service  (business rules)    │
                      └───────────────┬──────────────┘
                                      │
                      ┌───────────────▼──────────────┐
                      │ Repository  (JDBC)           │
                      └───────────────┬──────────────┘
                                      │
                      ┌───────────────▼──────────────┐
                      │          MySQL 8.4           │
                      └──────────────────────────────┘
```

## The four layers

| Layer | Package | Responsibility |
|---|---|---|
| Web | `web/controller`, `web/filter` | HTTP in and out. Parse, delegate, render or redirect. |
| Service | `core/service` | Business rules, transactions, invariants. |
| Repository | `core/repository` | SQL. Row ↔ object mapping. No business logic. |
| Domain | `core/domain` | Entities and enums. Plain data plus behaviour on the entity. |

`infrastructure` sits beside those four and holds everything that talks to a
thing outside the JVM: the JDBC pool, the migration runner, the payment HTTP
client, the cache, the SSE hub, monitoring. `util` is the leaf: validation,
security primitives, small JSON and time helpers, web utilities.

The dependency rule is one-directional and is described in
[package-structure.md](package-structure.md).

## Persistence is hand-written JDBC

There is no ORM. `DBConnection` owns a `HikariCP` pool; repositories hold
`String` SQL and map rows by column name. Transactions are explicit:

```java
try (Connection c = DBConnection.getConnection()) {
    c.setAutoCommit(false);
    ...
    c.commit();
} catch (SQLException e) {
    c.rollback();
    throw e;
}
```

The benefit is that every query and every index is visible in the repository
that uses it. The cost is that column drift is a runtime error, not a compile
error — which is why `RepositoryColumnCoverageTest` exists and asserts that the
column list a repository names still exists in the database.

## Schema evolution is file-based

`DatabaseMigrationRunner` applies the file names hard-coded in its
`discoverMigrations()` method, in that order, skipping anything recorded in
`schema_migrations`. There are 18 migration files, and they are not a
directory scan — a new file is inert until it is added to that list. The runner
is partially idempotent: it tolerates per-statement
"already applied" errors and then records the migration, so a migration that was
applied by hand but never recorded is survivable. See
[../database/migrations.md](../database/migrations.md).

## Configuration is layered, and one layer is the database

Most configuration is resolved by `AppConfig` with the precedence
**environment variable → system property → bundled properties file → default**.
The bundled files (`config/db.properties`) are
git-ignored local secrets.

Feature switches that an administrator should be able to change without a
rebuild are in the `app_settings` table and reached through
`PaymentConfig` (payments), `SupportChannelService` (footer social links), and
so on. `AppSettingsRepository` caches them for a short TTL, so a change takes
effect within that window rather than instantly. See
[../deployment/configuration.md](../deployment/configuration.md).

## The view layer is JSP, and it is the fragile part

Views live under `src/main/webapp/WEB-INF/views/` and are reached by forward, not
by URL. They use JSTL and a handful of `.jspf` fragments for the shared header,
footer, admin nav, product card, status badge, and CSRF token.

JSP has no compiler in the build, so a malformed tag is not a build failure — it
is a 500 at runtime, in production, on the page a customer is looking at. Three
source-lint tests in `src/test/java/.../web/view/` exist to catch the classes of
mistake that would otherwise ship:

- `JspTagBalanceTest` — unclosed or mismatched `c:` tags.
- `PaymentPanelRequiredTest` — a `required` control inside a conditionally
  hidden payment panel, which silently disables every other payment method.
- `RepositoryColumnCoverageTest` — repository SQL naming columns the database
  does not have.

These are explained in [../development/source-linting.md](../development/source-linting.md).

## Payments

The payment subsystem is the most intricate part of the codebase because it has
to satisfy two opposing requirements: be usable in a demo with no credentials,
and be safe to switch toward real money. It is documented separately in
[payments.md](payments.md).

## The filter chain

Fifteen filters, all declared in `WEB-INF/web.xml`, ordered outermost first.
Authentication, rate limiting, and CSRF are among them, and the order is
load-bearing. Traced request by request in
[request-lifecycle.md](request-lifecycle.md).
