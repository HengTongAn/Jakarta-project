# Product Requirements Document — ComputerStore Management System

| | |
|---|---|
| **Product** | ComputerStore Management System |
| **Document** | PRD v1.0 |
| **Status** | Draft for review |
| **Owner** | Team A (lead / reviewer) |
| **Contributors** | Team B (frontend), Team C/D (backend) |
| **Last updated** | 2026-09-25 |
| **Source of truth** | `computerstore/` codebase (release 1.2.1 + 1.3.0 perf work) |
| **Related docs** | `../README.md`, `WORKFLOW.md`, requirement deck `Computer Store Management System.pptx` |

---

## 1. Executive summary

ComputerStore is a **Jakarta EE e-commerce web application for a computer-components shop**. It delivers a complete customer storefront (browse, search, cart, atomic-stock checkout, order history, in-app mail) plus a full admin back-office (dashboard, inventory, order lifecycle, users & roles, catalog management, reviews moderation, reports, audit history), with security, realtime updates, and observability built in as first-class features rather than afterthoughts.

The product is a university Computer Science project executed at near-production quality: it satisfies the academic brief (MVC architecture on Jakarta EE + MySQL, role-based access, automatic stock deduction, relational integrity) and extends it with production-grade security (2FA, CSRF, rate limiting, audit trail), performance engineering (caching, connection pooling, compression, query indexing), realtime Server-Sent Events, and operational endpoints (health, metrics, performance monitor).

**Why this product matters:** stock management done on paper/spreadsheets loses money (overselling, stockouts, un-reconcilable records). ComputerStore replaces that with a single transactional system where inventory, sales, and customer history share one relational source of truth.

**State today:** 123 unit tests passing, load-tested at 350–530 req/s on the catalogue; deployed/known-good on Tomcat 11 + MySQL 8.4 (see `../README.md`).

---

## 2. Background & problem statement

Retailers without a point-of-sale system operate with three compounding failures (per the requirement deck):

1. **Manual inventory logs** — paper/spreadsheet tracking yields high error rates, misplaced records, and delayed stock updates at peak hours.
2. **Stock discrepancies** — no real-time quantity tracking → overselling, unexpected stockouts, unhappy customers.
3. **Opaque sales records** — no centralized transaction history → auditing is painful, customer order lookup is slow, report generation is ad hoc.

**Product solution:** a unified system that (a) stores products, categories, brands, users, and sales in a relational DB with strict integrity constraints; (b) auto-deducts stock at transaction confirmation *atomically* so inventory can never go negative; (c) separates staff controls from customer features via HTTP-session role guards; and (d) records every meaningful action in an auditable trail.

---

## 3. Goals & success metrics

### 3.1 Product goals

1. **G1 — Reliable inventory.** Eliminate negative stock and phantom oversells; every sale/refund/cancel adjusts stock transactionally.
2. **G2 — Role-separated experience.** Customers get a storefront + personal history; staff get an operations console; the store owner has a single root `SUPER_ADMIN`.
3. **G3 — Trustworthy audit trail.** Every auth, admin, and data mutation is correlated (request id, session, IP) and retained/archived per policy.
4. **G4 — Safe defaults.** Passwords hashed (BCrypt), 2FA available, CSRF-covered, rate-limited, security headers set, seeded default credentials flagged loudly.
5. **G5 — Fast enough to not be the bottleneck.** Cached reads, pooled connections, compressed responses, optimized queries.
6. **G6 — Observable.** Health, metrics, and a live performance dashboard so ops can diagnose without guessing.

### 3.2 Success metrics (measurable)

| Metric | Target | Evidence |
|---|---|---|
| Catalogue throughput | 350–530 req/s (100 clients, shared host) | load test, README |
| Cached read latency | < 100 ms | `/admin/performance` cache stats |
| DB-backed operation latency | < 500 ms | query monitor |
| Catalog self-heal staleness | ≤ 60 s after an admin write | Caffeine 60s TTL + global bust |
| No oversell | 0 occurrences | `reduceStock` conditional update + checkout transaction test |
| New-account/security incidents | brute-force blocked | rate limits + `LOGIN_FAILED` alert rules |
| Test health | 123/123 passing, hermetic | `mvn test` |
| Auth attempt economy | > 0.9 cache hit rate on steady-state reads | cache stats page |

### 3.3 Non-goals (explicitly out of scope — reaffirmed for this release)

- **No third-party payment gateways** (credit card / PayPal online). Checkout records an order; payment handling is demo-only UI.
- **No microservices / cloud scale-out**; the app is a single WAR on one Tomcat instance (caches and SSE are in-memory, single-JVM).
- **No AI/ML recommendation engine.**
- **No native mobile apps, Bluetooth/barcode scanners** (roadmap candidates only).
- **No distributed multi-store synchronization.**

---

## 4. Target users & personas

| Persona | Description | Key needs |
|---|---|---|
| **Customer** (role `CUSTOMER`) | Shopper at an in-store/online counter | Browse & search catalog, filter by category/brand/price, view specs & stock, build a cart, checkout without oversell, see order history, chat with staff via in-app mail, set up 2FA |
| **Store staff** (role `ADMIN`) | Employee operating the back-office | Manage catalog & inventory, adjust stock, process order lifecycle, moderate reviews, respond to customer mail, view reports and audit history |
| **Store owner** (role `SUPER_ADMIN`) | The one true root | Everything `ADMIN` can do, plus manage other admins and (in SQL only) the super-admin role itself; password resets for admins |
| **Ops / Sysadmin** (unauthenticated) | Deploys and monitors | Health endpoint, Prometheus metrics, performance dashboard, safe migrations, TLS deployment |

---

## 5. Scope overview

### 5.1 In scope (P0 — core, required by brief)

- Authentication (register, login, logout), password validation, session-based role guards
- Product catalog (browse, search, multi-criteria filter, product detail with specs/pricing/images)
- Inventory control (quantity tracking, automatic deduction on sale, low-stock threshold indication)
- Sales processing (cart → order with totals, order items snapshot prices, status lifecycle)
- Customer purchase history
- Staff back-office for catalog, inventory, and orders; simple sales/stock summary reports
- MVC architecture on Jakarta EE (Servlets + JSP/JSTL), JDBC + MySQL, relational integrity (FKs, CHECKs)

### 5.2 In scope (P1/P2 — extended, built on top)

- Reviews & moderation, in-app mail between customers and staff, password reset, 2FA (TOTP), avatar/product image upload
- Security hardening: CSRF, rate limiting (IP + per-user), security headers, secure cookies, audit trail with 730-day retention + alerts, default-credential detection
- Realtime SSE (stock / cart / order / mail updates)
- Performance: Hikari pooling, Caffeine caches, gzip compression, covering indexes, batch utilities
- Observability: `/health`, `/metrics`, `/admin/performance`; database migration runner

---

## 6. Functional requirements

Priorities: **P0** = must have (brief / core), **P1** = important (extended), **P2** = nice to have (polish).

### 6.1 Authentication & accounts

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-AUTH-01 | Register | P0 | As a shopper, I want to create an account so I can order. | Username (3–30 chars, `[A-Za-z0-9_]`), unique; email valid + unique; password 8–128 with upper/lower/digit/special, common passwords rejected; always creates `CUSTOMER`; session rotated on success; CSRF enforced on the POST. |
| FR-AUTH-02 | Login / logout | P0 | As a user, I want to log in and log out securely. | Username/password required; uniform "Invalid username or password" with timing equalization; soft-deleted accounts rejected ("disabled"); session id rotated at login; logout is POST-only 405 on GET; audit `LOGIN_SUCCESS` / `LOGIN_FAILED` / `LOGOUT`. |
| FR-AUTH-03 | Session guards | P0 | As the app, I must protect private routes per role. | `/admin/**`, `/cart/**`, `/checkout`, `/account/**`, `/mail/**` redirect anonymous users to login with `return` path; `/admin/**` returns 403 for non-admins; stale role changes take effect ≤ 15 s without logout. |
| FR-AUTH-04 | Profile & avatar | P1 | As a user, I want to view and edit my profile and upload an avatar. | Name/email updatable with validation; avatar upload with type/size guards; orphaned files cleaned up; audit `PROFILE_UPDATE` / `AVATAR_UPDATE`. |
| FR-AUTH-05 | Password change | P1 | As a user, I want to change my password from settings. | Old password re-verified; same strength policy; session CSRF token rotated after change; audit `PASSWORD_CHANGE`. |
| FR-AUTH-06 | Forgot / reset password | P1 | As a user, I want to reset a forgotten password by email. | 32-byte random token (SHA-256 hash stored, 30-min expiry); single active link per account; **no account enumeration** (identical response for unknown email); atomic single-use claim (conditional update); reset POST rotates CSRF token; token-probe GET throttled at 20/IP/min; base URL hardened against host-header poisoning. |
| FR-AUTH-07 | 2FA (TOTP) | P1 | As a user, I want optional two-factor authentication. | Enroll via QR/secret (pending code verified before enable); disable requires a live TOTP; secrets AES-256-GCM encrypted at rest (versioned `v1:`), plaintext/legacy rows refused; login shows 6-digit challenge step; failed 2FA re-renders challenge. |
| FR-AUTH-08 | Login rate limiting | P1 | As the app, I must slow brute force on auth endpoints. | POST-only budget on `/login /register /forgot /reset`: 10/min/IP + 60/hr/IP, plus a 200/min global budget; `429` + `Retry-After: 60`. `X-Forwarded-For` honored only when explicitly trusted. |

### 6.2 Roles & authorization

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-ROLE-01 | Role model | P0 | The app must distinguish customers from staff. | `SUPER_ADMIN > ADMIN > CUSTOMER`; `isAdmin()` true for both admin roles; stored as DB enum; admin UI lists users with role badges. |
| FR-ROLE-02 | ADMIN management | P1 | As the store owner, I want to create and manage staff accounts. | Only `SUPER_ADMIN` can manage admins; cannot grant/revoke `SUPER_ADMIN` from the UI; an admin cannot change their own role; role sourced from DB (never trusted from the session object alone) on privilege changes. |
| FR-ROLE-03 | SUPER_ADMIN is SQL-only | P1 | The root role must be tamper-proof from the app layer. | `SUPER_ADMIN` assignment happens only via DB migration/seed; service layer rejects creating/editing super admins; other supers' passwords only reset by a super. |
| FR-ROLE-04 | Fresh roles without re-login | P1 | Demotions/promotions apply without logout. | `SessionUserRefreshFilter` re-reads session user ≤ 15 s; soft-deleted or deleted accounts have sessions invalidated immediately; refresh is cache-backed and never aborts a request on DB failure. |

### 6.3 Product catalog

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-CAT-01 | Browse & paginate | P0 | As a shopper, I want to browse the catalogue in pages. | Non-discontinued products only; clear pagination; deterministic ordering (name / price / newest); escaped-LIKE search over name, sku, brand, description with `%`/`_`/`\` escaped. |
| FR-CAT-02 | Filter with live counts | P0 | As a shopper, I want to filter by category, brand, and price. | Multi-select category/brand via `IN` clauses; min/max price; sidebar counts computed against the *current* filter combination; active-filter summary chips. |
| FR-CAT-03 | Product detail | P0 | As a shopper, I want specs, price, images, stock status, and description. | Detail page with key/value specs (ordered), highlights, box contents, warranty, source URL, status pill (In/Low/Out of stock), reviews and rating summary. |
| FR-CAT-04 | Trending / new arrivals | P1 | As a shopper, I want quick discoverability strips on the home page. | Trending ranked by units sold (excluding cancelled/refunded), falling back to newest; cached via single-flight catalog memo. |
| FR-CAT-05 | Catalog freshness | P1 | Admin edits appear quickly on storefront lists. | Global catalog bust on product/brand/category writes; 60 s TTL backstop; single-flight loads so 1000 concurrent reads don't stampede the DB. |
| FR-CAT-06 | Reviews & ratings | P1 | As a shopper, I want to rate products after purchase context. | One review per user per product; rating 1–5, title ≤ 150, body 10–4000; pending moderation; verified flag; aggregate rating summary per product. |

### 6.4 Cart & checkout (sales processing)

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-SALE-01 | Cart management | P0 | As a shopper, I want to add/update/remove items. | Per-user cart rows (unique user+product); quantity clamped and always > 0; cart badge; AJAX quick-add + JSON responses with CSRF header support. |
| FR-SALE-02 | Checkout transaction | P0 | As a shopper, I want to place an order once and only once. | **Single DB transaction**: validate cart → atomically reduce stock per line → create order (PENDING) → snapshot order items at current unit price → status event "Order placed" → inventory log → clear cart → commit. Any failure rolls back everything. Cache invalidated only *after* commit. |
| FR-SALE-03 | No oversell | P0 | As the store, inventory must never go negative. | Conditional `UPDATE ... WHERE stock_quantity >= ?`; concurrent checkouts cannot double-spend; `InsufficientStockException` with remaining-quantity message; guaranteed no negative stock (DB CHECK + atomic update). |
| FR-SALE-04 | Order lifecycle | P0 | As staff, I want to move orders through a timeline. | PENDING → PROCESSING → SHIPPED → COMPLETED, plus CANCELLED (pending/processing) and REFUNDED (completed); CANCELLED/REFUNDED **atomically restock** and log it; terminal states cannot be reopened; every transition writes a timestamped status event with actor + note; concurrent transitions guarded by conditional `updateStatusIfCurrent`. |
| FR-SALE-05 | Order history | P0 | As a customer, I want my order history and details. | Customer sees only their own orders (server-enforced ownership); order detail shows items, amounts, and full status timeline. |
| FR-SALE-06 | Checkout extras | P2 | (Demo UI only) delivery address / payment method fields. | Fields exist on the checkout page for form completeness; **no card data is read or stored** (documented demo copy — do not misrepresent as a payment integration). |

### 6.5 Inventory management

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-INV-01 | Stock adjustment | P0 | As staff, I want to set stock and have status recomputed. | Admin adjusts quantity; status recomputed from rules (0 → OUT_OF_STOCK, ≤5 → LOW_STOCK, else IN_STOCK); explicit DISCONTINUED stays discontinued; `STOCK_ADJUST` logged to inventory log + audit. |
| FR-INV-02 | Inventory log | P1 | As staff, I want a history of stock changes. | Every adjust/sale/restore writes `inventory_logs` (old, new, action, user) on the *same transaction* as the change; recent 15 shown in admin UI. |
| FR-INV-03 | Low-stock visibility | P0 | As staff, I want low/out-of-stock alerts. | Low-stock / out-of-stock lists and counts in the admin inventory page and dashboard KPI cards; threshold consistent with `LOW_STOCK` rule (5). |
| FR-INV-04 | Soft delete / discontinue | P0 | Historical sales must survive product removal. | Deleting a product with order history *discontinues* it instead (soft delete with `deleted_at`/`deleted_by`/`reason` + status); storefront hides discontinued except in past orders; discontinued products cannot be re-listed by the stock recompute. |

### 6.6 Admin back-office

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-ADM-01 | Dashboard | P0 | As staff, I want KPIs at a glance. | Stat cards (totals, low-stock, revenue, recent orders/logs) + charts; cached ≤ 5 min; invalidation on relevant writes. |
| FR-ADM-02 | Catalog management | P0 | As staff, I want CRUD for products, categories, and brands. | Create/edit/delete with validation and audit logs (`PRODUCT_CREATE/UPDATE/DELETE`, `CATEGORY_*`, `BRAND_*`); spec rows maintained transactionally (≤100, de-duplicated, length-capped); product delete strategy per FR-INV-04; image upload with rollback on failed save. |
| FR-ADM-03 | Order management | P0 | As staff, I want to administer orders. | Paginated list (25/page) with status filter; detail view with timeline; status transitions per FR-SALE-04 with actor attribution; audit `ORDER_STATUS`. |
| FR-ADM-04 | User management | P1 | As an admin, I want to manage users. | Create/edit/reset-password/profile view; avatar upload (5 MB cap); role guardrails per FR-ROLE-02/03; audit `USER_CREATE/UPDATE/PASSWORD_RESET`; orphaned avatar cleanup. |
| FR-ADM-05 | Review moderation | P1 | As staff, I want to approve/reject reviews. | Status-filtered pagination; approve/reject/delete actions POST-only + audited; pending count badge on admin nav. |
| FR-ADM-06 | Reports | P1 | As staff, I want sales & stock summary reports. | `AdminReportsServlet` renders sales/stock summaries from order + inventory data. |
| FR-ADM-07 | Audit history browser | P1 | As staff, I want to search the audit trail. | Search by keyword with LIKE-escaped patterns; event list, export; alert rules surfaced (brute force, lockout risk, password-change burst, registration spam). |
| FR-ADM-08 | Admin rate limits | P1 | Admin flows must tolerate high legitimate traffic without abuse. | Per-user POST budget on `/admin/**`: 60/min + 500/hr (admins), 30/min + 200/hr (customers); 429 + Retry-After; cleanup throttled to once/minute. |

### 6.7 In-app mail & notifications

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-MAIL-01 | In-app mailbox | P1 | As a customer/staff, I want to message the other side in-app. | Inbox / sent / compose / view; reply; read toggle + read-all; unread badge; indexing supports inbox/sent queries. |
| FR-MAIL-02 | SMTP email notifications | P2 | As the app, notify by real email when configured. | Order placed + order status change emails via background SMTP sender; **never blocks the request**; skipped silently when SMTP unconfigured; dev console token logging only when explicitly enabled. |

### 6.8 Realtime updates

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-RT-01 | SSE notifications | P1 | As a user, I want live updates without polling. | `/realtime` SSE stream pushes stock changes (public), order status + cart badge (owner + admins only); 15 s heartbeats; 500-client cap rejects excess gracefully; publish happens only **after** the DB commit. |
| FR-RT-02 | Realtime topic auth | P1 | Private events must never leak. | Server-side authorization: `orders`/`cart` delivered only to the owning user or an admin; subscription-based filtering; feature-flag disabled in tests. |

### 6.9 Security & compliance (cross-cutting)

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-SEC-01 | CSRF protection | P0 | State-changing requests must require a session token. | Every POST validated (login/register **included** to prevent login-CSRF); per-session UUID token; constant-time compare; param / `X-CSRF-Token` header / multipart part all supported; token rotated after password change/reset. |
| FR-SEC-02 | Security headers | P1 | Responses must carry hardened headers. | `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, CSP (`'self'` + minimal inline), `Referrer-Policy`, `Permissions-Policy`; HSTS opt-in behind TLS. |
| FR-SEC-03 | Secure cookies | P1 | Session cookie must be `Secure` in production. | `HttpOnly` + `Secure` (auto on HTTPS or forced by system property behind a TLS proxy); URL session tracking disabled. |
| FR-SEC-04 | Audit trail | P1 | Every meaningful action must be attributable. | Audit rows carry `request_id`, `session_id`, `ip` (forwarded headers only when trusted); types AUTH/ADMIN/DATA/SECURITY/SYSTEM; column truncation; 730-day retention task archives old rows (copy-then-delete, never discards); DB write failure never breaks the request. |
| FR-SEC-05 | Default credential detection | P1 | Seeded accounts must not survive into production. | Startup scans for accounts *still verifying* against `admin123` / `customer123` (checks plaintext, not hash string) and logs loud ERROR with remediation; never blocks startup. |
| FR-SEC-06 | SSRF guard | P1 | Admin web-info fetcher must not reach internal hosts. | URL fetch denies loopback/private/link-local/metadata addresses, `ftp:`/`file:` schemes, and host-less/malformed URLs; resolution disabled in hermetic tests. |

### 6.10 Observability & operations

| ID | Requirement | Pri | User story | Acceptance criteria |
|---|---|---|---|---|
| FR-OPS-01 | Health endpoint | P1 | As ops, I want a liveness/DB check. | `GET /health` returns 200 `{"status":"UP",...}` including DB pool stats (active/idle/waiting/total/max/min) and disk check; 503 on failure. |
| FR-OPS-02 | Prometheus metrics | P1 | As ops, I want scrape-able metrics. | `GET /metrics` text exposition; admin-gated inline (endpoint is outside `/admin/*` mapping). |
| FR-OPS-03 | Performance monitor | P1 | As ops, I want live query + cache telemetry. | `/admin/performance` shows per-cache hits/misses/hit-rate/size and recent query timings; cache invalidation broadcast on every stock write; cache disabled via `-Dcomputerstore.cache.enabled=false` for clustering. |
| FR-OPS-04 | Migration runner | P1 | Schema must evolve versioned and re-runnable. | `db/migrations/migration_*.sql` applied once, in order, recorded in `schema_migrations`; opt-in auto-run flag (production recommends manual/CI); migration names are immutable keys once applied. |

---

## 7. Non-functional requirements

### 7.1 Performance
- **NFR-PERF-01**: Cached read paths serve < 100 ms; DB-backed operations < 500 ms at the reference dataset.
- **NFR-PERF-02**: Catalogue sustains 350–530 req/s under a 100-client shared-host load test with 0 real errors.
- **NFR-PERF-03**: Multi-layer caching with single-flight loaders (no read stampede); cache disabled safely for multi-instance deployments.
- **NFR-PERF-04**: Connection pooling via HikariCP (max 50 / min 10 defaults, bounded 5–200), pre-warmed on startup, statement-cache and batched-write options on.
- **NFR-PERF-05**: Gzip compression for dynamic text when enabled (currently disabled by default on Tomcat 11 — see Open Questions).
- **NFR-PERF-06**: Appenders are async except the audit appender, which intentionally blocks rather than ever dropping a security record.

### 7.2 Security
- **NFR-SEC-01**: Passwords BCrypt cost-10, never logged or returned (hash scrubbed at every service boundary).
- **NFR-SEC-02**: 2FA secrets encrypted at rest (AES-256-GCM, versioned) with a deployment-owned key.
- **NFR-SEC-03**: All SQL parameterized (JDBC `PreparedStatement`) — no string-concatenated user input; LIKE wildcards escaped.
- **NFR-SEC-04**: XSS mitigated via CSP + `<c:out>`-style escaping in views; uploads type/size constrained.

### 7.3 Reliability & integrity
- **NFR-REL-01**: All multi-table writes are single transactions on one connection; partial writes impossible.
- **NFR-REL-02**: Cache invalidation always occurs *after* commit to avoid re-caching pre-commit state.
- **NFR-REL-03**: Realtime/email/audit side-effects never block or roll back the primary transaction, and never occur after a rollback.
- **NFR-REL-04**: App startup tolerates DB down (pool warm failures non-fatal); user refresh tolerates transient DB errors.
- **NFR-REL-05**: Deployment is non-destructive (`schema.sql` never drops; seeds are opt-in and loudly flagged).

### 7.4 Usability & accessibility
- **NFR-UX-01**: Bootstrap 5 responsive UI, vendored locally (no CDN dependency).
- **NFR-UX-02**: Every POST form carries CSRF; PRG pattern prevents duplicate submissions on refresh.
- **NFR-UX-03**: Keyboard/clarity basics: semantic labels, status pills, toast flash messages with visible confirmation.
- **NFR-UX-04**: 2FA login field: `inputmode="numeric" autocomplete="one-time-code"`, `[0-9]{6}` pattern.

### 7.5 Maintainability & quality
- **NFR-MNT-01**: Layered architecture (core / web / infrastructure / util) with stable dependency direction.
- **NFR-MNT-02**: 123 hermetic unit tests must stay green (`mvn test`); new logic ships with tests.
- **NFR-MNT-03**: Every feature flag and setting documented resolution: env var → system property → bundled file → default (see `AppConfig`).
- **NFR-MNT-04**: GitHub Flow workflow; one squash-merged PR per change (`WORKFLOW.md`).

### 7.6 Compatibility
- **NFR-COMP-01**: Java 17, Tomcat 11 / Jakarta EE 11 (Servlet 6.1, JSP 4, JSTL 3), MySQL 8.x (utf8mb4).
- **NFR-COMP-02**: Works on plain HTTP in dev and behind a TLS-terminating reverse proxy (Caddy) in production; cookie Secure behavior adapts accordingly.

---

## 8. User journeys

### 8.1 Customer journey
1. **Land & browse** — opens `/products`, searches or filters by category/brand/price; saw live counts per filter.
2. **Inspect** — opens product detail: specs, highlights, status pill, reviews, rating summary.
3. **Add to cart** — quick-add (JSON) or cart page; badge updates; stock guarded at checkout.
4. **Checkout** — authenticates (rotated session; optional 2FA challenge), places order; **single transaction reserves stock**; order appears with full timeline.
5. **Track & chat** — order history in account; in-app mail with staff; realtime stock/order pushes while logged in.

### 8.2 Staff journey
1. **Sign in** to admin console (role-guarded).
2. **Dashboard** — KPIs: totals, low-stock warnings, revenue, recent activity.
3. **Manage catalog/inventory** — CRUD products/categories/brands; adjust stock (status recomputed, logged); see low/out-of-stock lists.
4. **Process orders** — advance status through lifecycle; cancel/refund automatically restocks and logs.
5. **Moderate & communicate** — approve/reject reviews; answer customer mail.
6. **Audit & report** — search history; run reports; watch `/admin/performance`.

---

## 9. Data & integration requirements

### 9.1 Schema (MySQL, InnoDB, utf8mb4)
Core tables: `users`, `categories`, `brands`, `products` (+`product_specs`), `orders` (+`order_items`, `order_status_events`), `cart_items`, `inventory_logs`, `mail_messages`, `reviews`, `password_reset_tokens`, `two_factor_secrets`, `audit_logs` (+`audit_logs_archive`), `schema_migrations`.

Key integrity rules:
- FKs enforced; `CHECK` on price ≥ 0, stock ≥ 0, quantity > 0, rating 1–5.
- Order items **snapshot** unit price/subtotal (historical prices preserved).
- Soft-delete columns (`deleted_at/by/reason`) on users/categories/brands/products without FK cycles that block history.
- Unique constraints: username/email/SKU, cart (user,product), review (user,product), 2FA (user), token hash.

### 9.2 Migrations
Versioned `migration_*.sql` files; recorded in `schema_migrations`; applied in file-list order; rename-after-ship is forbidden (test enforced). `schema.sql` is a full current-schema re-run (idempotent, no drops).

### 9.3 Integrations
- **SMTP** (Jakarta Mail, Gmail default) for password-reset and order notifications — best-effort, background, non-blocking.
- None other in scope (no payment, no external product feeds; the admin web-info fetcher is an internal utility with SSRF guards).

---

## 10. Release plan

| Milestone | Scope | Status |
|---|---|---|
| **M1 — Core (release 1.0)** | Auth + roles, catalog, inventory, cart/checkout/orders, admin basics, reports | ✅ Shipped (initial commit) |
| **M2 — Extended (release 1.1)** | Reviews, in-app mail, password reset, 2FA, uploads, audit trail, security hardening | ✅ Shipped |
| **M3 — Hardening (release 1.2.x)** | Rate limiting, session refresh, retention, default-credential detection, deploy fixes | ✅ Shipped (HEAD `4fe2486`, **1.2.1**) |
| **M3+ — Performance pass (1.3.0 in tree)** | Compression filter, repository/dashboard caching, pool pre-warm, covering indexes, batch utilities, `/admin/performance` (live, in-place updates; no 30 s page reload) | ✅ Shipped |
| **M4 — Docs & polish (proposed)** | Restore `docs/` (architecture, database, api, security, deployment, changelog); finish 1.3.0; PRD sign-off | ⬜ Next |

---

## 11. Roadmap (future)

- Barcode / QR scanner checkout integration (from brief's future scope)
- Email notifications polish: low-stock alerts + digital receipts (real attachments)
- RESTful API endpoints for a future mobile client
- Clustered deployment support: distributed cache + SSE hub (Redis/Kafka-style), documented as a known single-JVM limitation today
- Backup/recovery codes for 2FA (generation exists, redemption path not yet wired)
- Compression filter enablement on Tomcat 11 (or drop if irreconcilable)
- Extend test coverage: security filters, admin servlets, service/repository layers

---

## 12. Open questions

1. **Compression filter**: dead-by-default on Tomcat 11 (JSP-forward incompatibility). Fix it, drop it, or document the flag as the only path?
2. **`docs/` folder**: README links 13 docs that no longer exist in the repo. Restore from history, rewrite, or prune the README references?
3. **Checkout "payment" fields** (name/card/expiry/CVC collected but not read): keep as demo-only, remove, or make explicitly labeled as non-functional?
4. **2FA backup codes** generation exists but is unused — include in this release or cut?
5. **Multi-instance**: are caches/SSE/rate-limit maps acceptable as single-JVM, or should the next milestone budget for a distributed story?
6. **Metrics gating**: `/metrics` self-checks admin inline (outside the `/admin/*` filter). Should it move under the standard authz path?

---

## 13. Risks & mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Seeded `admin/admin123` reaches a live deployment | Medium | Critical | Startup default-credential scan (ERROR log); go-live checklist; force-change workflow |
| Connection-pool exhaustion under page-click bursts | Low (fixed) | High | Pool tuning, throttled refresh filters, disabled legacy count filters, leak detection |
| Cache staleness on multi-instance | Low now | Medium | TTLs bounded (60s catalog), `cache.enabled=false` escape hatch, documented limitation |
| Single-JVM SSE/rate-limit state resets on restart | Low | Low | In-memory only; acceptable for one-node deployment; roadmap tracks distributed option |
| Incomplete test coverage on security filters/servlets | High | Medium | Extend hermetic filter tests (see roadmap) before regressions surface |
| Missing `docs/` misleads contributors | High | Low | Restore docs as part of M4 |
| Password-reset email relies on SMTP config | Medium | Medium | Best-effort send; dev token logging flag; base-URL hardening against header poisoning |

---

## 14. Appendix

### 14.1 Glossary
- **SSE** — Server-Sent Events (one-way realtime push over HTTP).
- **TOTP** — Time-based One-Time Password (RFC 6238), used for 2FA.
- **PRG** — Post/Redirect/Get (prevents duplicate form submissions).
- **P0/P1/P2** — priority tiers: must-have / important / nice-to-have.
- **SUPER_ADMIN** — single root role, assignable only in SQL.

### 14.2 References
- Requirement deck: `Computer Store Management System.pptx` (formal brief, 16 slides)
- System README: `../README.md` (quick start, architecture, repository layout)
- Team workflow: `WORKFLOW.md` (GitHub Flow, branch conventions)
- Source of truth: `src/main/java/com/hengtongan/computerstore/**` — layered per README §Architecture

### 14.3 Version history

| Version | Date | Author | Change |
|---|---|---|---|
| 1.0 | 2026-09-25 | Team A | Initial PRD — captures shipped product (1.2.1) + in-tree 1.3.0 perf work; proposed M4 docs/polish milestone |