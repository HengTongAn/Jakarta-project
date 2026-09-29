# Documentation

Reference documentation for Apach_PC/STORE (`computerstore`). Everything here is
derived from the code in this repository — if a document and the code disagree,
the code is the bug and the document should be fixed in the same change.

## Where to look

| I want to… | Read |
|---|---|
| Understand the system shape | [architecture/overview.md](architecture/overview.md) |
| Know which package may call which | [architecture/package-structure.md](architecture/package-structure.md) |
| Trace what happens on a request | [architecture/request-lifecycle.md](architecture/request-lifecycle.md) |
| Understand payments | [architecture/payments.md](architecture/payments.md) |
| Find the endpoint for a page | [api/README.md](api/README.md) |
| Know why an endpoint returns 302/403 | [api/conventions.md](api/conventions.md) |
| Look up a table or column | [database/schema.md](database/schema.md) |
| Add or debug a migration | [database/migrations.md](database/migrations.md) |
| Know what payment data is stored | [database/payment-data.md](database/payment-data.md) |
| Run it locally | [deployment/local-development.md](deployment/local-development.md) |
| Deploy a WAR | [deployment/tomcat-deployment.md](deployment/tomcat-deployment.md) |
| Host it free on Oracle Cloud | [deployment/oracle-cloud.md](deployment/oracle-cloud.md) |
| Change a setting | [deployment/configuration.md](deployment/configuration.md) |
| Build and test | [development/building-and-testing.md](development/building-and-testing.md) |
| Understand the JSP lint tests | [development/source-linting.md](development/source-linting.md) |
| Install it on a phone / offline behaviour | [development/pwa.md](development/pwa.md) |
| Audit the security posture | [security/README.md](security/README.md) |

## Scope

This is a single-WAR Java EE storefront. It has no build-time secrets, no
message broker, and no second process. If a document ever needs to describe one
of those, that is a signal the document drifted — re-check the code first.

## Conventions used in these documents

- **"Must"** means there is code or a test that enforces it. Anything weaker is
  written as "should" or "currently".
- **Known issues** are recorded, not hidden. See
  [security/known-issues.md](security/known-issues.md).
- Code references use the path relative to the repository root, e.g.
  `src/main/java/.../PaymentConfig.java`.
- Measurements quoted from the live database were taken at a point in time and
  are marked as such; they are not load-bearing.

## Project shape at a glance

| | |
|---|---|
| Language / release | Java 17 |
| Servlet API | Jakarta Servlet 6.1 |
| View layer | JSP + JSTL, served from `WEB-INF/views` |
| Persistence | Hand-written JDBC over a `HikariCP` pool |
| Database | MySQL 8.4 |
| Build | Maven (`computerstore`, `1.0-SNAPSHOT`) |
| Tests | JUnit 5.10.2 — 233 tests in `mvn test` |
| Runtime | Apache Tomcat 9 (exploded or WAR under `/computerstore`) |
| Front end | Bootstrap 5 + Bootstrap Icons + Chart.js, vendored, no CDN |

The interesting engineering in this project is concentrated in three places:
the filter chain, the payment subsystem, and the source-lint tests that protect
the view layer. Those three get the most documentation.
