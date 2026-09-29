# API index

Every URL the application serves. A request path here is relative to the
context path, which is `/computerstore`.

## Public

No session required.

| Method | Path | Servlet | Notes |
|---|---|---|---|
| GET | `/` | `index.jsp` | Landing page. |
| GET, POST | `/login` | `auth/LoginServlet` | POST is CSRF-exempt. Accepts `return` to redirect back after login. |
| GET, POST | `/register` | `auth/RegisterServlet` | POST is CSRF-exempt. |
| GET, POST | `/logout` | `auth/LogoutServlet` | Ends the session. |
| GET | `/products` | `customer/ProductServlet` | Catalogue. Accepts `id` for the detail view, plus filter/sort/paging parameters. |
| POST | `/products/review` | `customer/ReviewServlet` | Submit a review. CSRF-protected. |
| GET | `/product-images/*` | `customer/ProductImageServlet` | Serves product images from disk. |
| GET | `/avatars/*` | `customer/AvatarImageServlet` | Serves user avatars from disk. |
| GET | `/cart/count` | `customer/CartCountServlet` | JSON badge count. |
| GET | `/realtime` | `infrastructure/realtime/RealtimeStreamServlet` | SSE stream. Chain must be async-capable; enforced by `AsyncSupportDescriptorTest`. |
| GET | `/health` | `monitoring/HealthCheckServlet` | Liveness/readiness probe. |
| GET | `/metrics` | `monitoring/MetricsServlet` | Counters. |
| GET | `/error` | `error/ErrorServlet` | Catch-all for unhandled exceptions. |

`/cart/count`, `/realtime`, `/health`, `/metrics`, and `/error` are not mapped
through `AuthenticationFilter`, so they are reachable anonymously. That is
intentional for the first four; `/error` is only reachable via the
`<error-page>` catch-all.

## Authenticated — customer

Behind `AuthenticationFilter`. Anonymous access redirects to
`/login?return=<path>`.

| Method | Path | Servlet | Notes |
|---|---|---|---|
| GET, POST | `/cart` | `customer/CartServlet` | View cart. |
| POST | `/cart/add` | `customer/CartServlet` | |
| POST | `/cart/update` | `customer/CartServlet` | |
| POST | `/cart/remove` | `customer/CartServlet` | |
| GET, POST | `/checkout` | `customer/CheckoutServlet` | See [payments.md](../architecture/payments.md). |
| GET, POST | `/account` | `customer/AccountServlet` | |
| GET, POST | `/account/orders` | `customer/CustomerOrdersServlet` | The order list. |
| GET, POST | `/account/profile` | `customer/ProfileServlet` | |
| GET, POST | `/account/avatar` | `customer/AvatarServlet` | Upload. |
| GET, POST | `/account/2fa` | `customer/TwoFactorSetupServlet` | |
| GET, POST | `/account/settings` | `SecuritySettingsServlet` | Declared in `web.xml`, not annotated. Mapped by `CSRFProtectionFilter` twice. |
| GET | `/payment/aba/*` | `customer/AbaPaymentServlet` | Redirect-and-return status page. |
| GET | `/payment/card/*` | `customer/CardPaymentServlet` | Synchronous card outcome page. |

## Authenticated — admin

Behind `AuthenticationFilter` **and** `AdminAuthorizationFilter`. A
non-administrator gets `403`, not a redirect — the user is authenticated, they
are simply not allowed.

| Method | Path | Servlet |
|---|---|---|
| GET | `/admin`, `/admin/` | `admin/DashboardServlet` |
| GET, POST | `/admin/products` | `admin/AdminProductsServlet` |
| GET, POST | `/admin/categories` | `admin/AdminCategoriesServlet` |
| GET, POST | `/admin/brands` | `admin/AdminBrandsServlet` |
| GET, POST | `/admin/inventory` | `admin/AdminInventoryServlet` |
| GET, POST | `/admin/orders` | `admin/AdminOrdersServlet` |
| GET, POST | `/admin/users` | `admin/AdminUsersServlet` |
| GET, POST | `/admin/reviews` | `admin/AdminReviewsServlet` |
| GET, POST | `/admin/reports` | `admin/AdminReportsServlet` |
| GET, POST | `/admin/history` | `admin/AdminHistoryServlet` |
| GET, POST | `/admin/payments` | `admin/AdminPaymentsServlet` |
| GET, POST | `/admin/support` | `admin/AdminSupportServlet` |
| GET, POST | `/admin/performance` | `admin/PerformanceMonitoringServlet` |

## How to read the list

Almost every servlet is `@WebServlet`-annotated rather than declared in
`web.xml`; `SecuritySettingsServlet` is the one exception. That means a new
endpoint is added by annotating a class, and `web.xml` is reserved for the
filter chain, error pages, session config, and listeners. To regenerate the
authoritative list:

```bash
grep -rn "@WebServlet" src/main/java
```

## Related

- [conventions.md](conventions.md) — status codes, redirects, flash messages,
  and what each failure looks like from the outside.
- [../architecture/request-lifecycle.md](../architecture/request-lifecycle.md) —
  what runs before the servlet.
