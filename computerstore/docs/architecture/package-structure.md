# Package structure and dependency rules

Base package: `com.hengtongan.computerstore`.

```
com.hengtongan.computerstore
├── core/            business layer, depends only on domain + java
│   ├── config       AppConfig, AppContext, PaymentConfig
│   ├── domain       entities, enums
│   ├── exception    typed exceptions
│   ├── repository   JDBC repositories
│   └── service      business logic and transactions
├── infrastructure   anything that talks outside the JVM
│   ├── cache        CacheManager
│   ├── monitoring   QueryMonitor, metrics
│   ├── optimization query/memory tuning
│   ├── payment      AbaPaywayClient
│   ├── persistence  DBConnection, migration runner, repositories' JDBC plumbing
│   ├── realtime     EventHub (SSE)
│   └── security     TwoFactorAuthService
├── util             leaf helpers, no dependency on any layer above
│   ├── cache, file, json, security, time, validation, web
└── web              HTTP boundary
    ├── controller   auth / customer / admin / error / monitoring
    └── filter       monitoring / performance / security / web
```

## The rule

Dependencies point in one direction only:

```
web  ──▶  core.service  ──▶  core.repository  ──▶  infrastructure.persistence
                            core.config            infrastructure.*
                            core.domain           util
```

Concretely:

- **`web` must not contain business rules.** A servlet may parse a request, call
  a service, choose a view, and set a status. If a servlet computes a price,
  decides an order status, or writes SQL, that logic belongs in
  `core/service`.
- **`core` must not import `web` or `jakarta.servlet`.** This is what keeps the
  services testable without a servlet container. It is also why services take
  plain values and domain objects, not `HttpServletRequest`.
- **`core.service` must not import `core.repository`?** — No. Services *do* call
  repositories; that is their job. The rule is the reverse: repositories must
  not call services or hold rules.
- **`util` must not depend on any layer above it.** `util` is a leaf. If
  something in `util` needs a repository, the code belongs elsewhere.
- **`infrastructure` is where platform specifics live.** `HikariCP`, the MySQL
  driver, the HTTP client for the payment gateway, and `MessageDigest` live
  there or in `util`. Nothing in `core.domain` should know that a database
  exists.

## Where new code goes

| You are adding | Put it in |
|---|---|
| A page | A servlet in `web/controller/<area>` + a JSP in `WEB-INF/views/<area>` |
| A new business rule | `core/service` |
| A new table or a new query | `core/repository` + a migration |
| A new configuration switch an admin must flip | `app_settings` + a reader in `core/config` |
| A new platform capability | `infrastructure/<capability>` |
| A validation rule | `util/validation` |

## Why `AppConfig` sits in `core.config`

`AppConfig` resolves environment variables, system properties, and bundled
property files. It is in `core` rather than `infrastructure` because services
depend on it and services must not depend on `infrastructure` for anything that
is not platform plumbing. It is a pure function over four candidate strings —
`AppConfig.resolve(env, property, file, default)` is package-private precisely so
it can be unit tested without mutating the process environment.

`PaymentConfig` sits beside it for the same reason, and additionally reads
`app_settings` from the database. It is documented in [payments.md](payments.md).

## Entities

`core/domain/entity` holds plain data classes: `User`, `Product`, `Order`,
`Payment`, `Review`, `Brand`, `Category`, and so on. They are mutable JavaBeans
with no-arg constructors, because they are populated by hand-written row
mappers. Behaviour that is genuinely the entity's (a status transition, a
computed display value) is allowed on the entity; behaviour that needs the
database is not.

Note for JSP authors: an entity's `isX()` accessor is **not** resolvable as the
EL property `x`. Use `<c:set>` and string coercion, or add an explicit `getX()`.
This bites regularly and is the reason several views cache a boolean in a
request attribute first.

## Exceptions

`core/exception` holds the typed failures a service raises so the web layer can
translate them into a status code. `api/conventions.md` lists which exception
maps to which response.
