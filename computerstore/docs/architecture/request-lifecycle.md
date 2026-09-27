# Request lifecycle

Fifteen filters run before any servlet. They are all declared in
`src/main/webapp/WEB-INF/web.xml`, and the declaration order is the execution
order — outermost first. The order is load-bearing; see
[Why the order matters](#why-the-order-matters) at the end.

## The chain

| # | Filter | Mapped to | What it does |
|---|---|---|---|
| 1 | `EncodingFilter` | `/*` | Forces UTF-8 request and response encoding. |
| 2 | `StaticResourceCacheFilter` | `/*` | Long-lived `Cache-Control` for `/assets/**`. Versioned filenames get `immutable`; everything else a conservative 7-day TTL with `must-revalidate`. |
| 3 | `CompressionFilter` | `/*` | Buffers the response and replays it gzipped when the client accepts gzip, the type is compressible, and the body exceeds 1 KB. Skips static assets and the SSE stream. |
| 4 | `RequestAuditContextFilter` | `/*` | Seeds request id, session id, and client IP so every `AuditLogger` call during the request can be correlated. |
| 5 | `SecureCookieFilter` | `/*` | Adds `Secure` to session cookies and every other `Set-Cookie`, when the request arrived over TLS or `-Dcomputerstore.session.cookie.secure=true`. |
| 6 | `SecurityHeadersFilter` | `/*` | `X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`, `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`, and `Strict-Transport-Security`. |
| 7 | `SessionUserRefreshFilter` | `/*` | Re-reads the session user from the database (throttled) so a role change, profile edit, or soft delete takes effect without re-login. Ends the session if the account is gone. |
| 8 | `RateLimitingFilter` | `/forgot`, `/login`, `/register` | Per-IP rate limit on the credential endpoints. |
| 9 | `UserRateLimitingFilter` | `/cart`, `/cart/*`, `/checkout`, `/account`, `/account/*`, `/admin`, `/admin/*` | Per-user rate limit: 30 req/min and 200 req/hour for customers, 60/min and 500/hour for admins. |
| 10 | `AuthenticationFilter` | `/admin`, `/admin/*`, `/cart`, `/cart/*`, `/checkout`, `/payment/aba`, `/payment/aba/*`, `/payment/card`, `/payment/card/*`, `/account`, `/account/*` | Redirects anonymous users to `/login?return=<path>`. Records last-activity time, throttled, for the presence indicator. |
| 11 | `CartCountFilter` | `/*` | Puts the cart item count in request scope for the nav badge. |
| 12 | `ReviewCountFilter` | `/admin`, `/admin/*` | Puts the pending-review count in request scope for the admin nav. Admins only. |
| 13 | `SupportChannelFilter` | `/*` | Puts `supportChannels` in request scope for `footer.jspf`. A filter rather than a scriptlet because the JSP is a static include with no access to `AppContext`, and the channels must be resolved once per request rather than per footer. |
| 14 | `AdminAuthorizationFilter` | `/admin`, `/admin/*` | Forwards to `403.jsp` unless the session user `isAdmin()` — that is, role `ADMIN` or `SUPER_ADMIN`. |
| 15 | `CSRFProtectionFilter` | `/*` and `/account/settings` | Validates the CSRF token on POST. Excludes `/login` and `/register`. |

## A page request, end to end

Take `GET /computerstore/checkout` for a signed-in customer with a non-empty
cart:

1. **1–3** Encoding, caching, and compression wrap the response. The response
   will be buffered and possibly gzipped.
2. **4** A correlation id is attached to the request.
3. **5–6** Security headers and cookie attributes are added. These are added
   before anything can commit the response, which matters for the compression
   wrapper.
4. **7** The session user is refreshed if the throttle interval has elapsed.
5. **8** Not mapped here — skipped.
6. **9** The per-user limiter consumes one token for this user id.
7. **10** The user is authenticated; `last_active_at` is updated if throttled
   in.
8. **11** Cart count lands in request scope.
9. **12** Skipped — not an admin route.
10. **13** Support channels land in request scope.
11. **14** Skipped — not an admin route.
12. **15** Skipped — the method is GET, not POST.
13. `CheckoutServlet.doGet` reads the cart, the payment configuration, and the
    supporting service data, then forwards to
    `WEB-INF/views/customer/checkout.jsp`.
14. The JSP renders the shared header, which reads the cart count and support
    channels straight out of request scope, renders the page, and the buffered
    response is gzipped and written.

## CSRF in practice

`CSRFProtectionFilter` applies to POST only. The token lives in the session
under `csrfToken`, is compared in **constant time** via
`MessageDigest.isEqual`, and is read from either the `csrfToken` parameter or
the `X-CSRF-Token` header. `csrf.jspf` renders it into forms and
`RequestUtil`/`CSRFUtil` expose the header name to JavaScript.

The filter additionally maps to `/account/settings` as its own entry, which is
redundant with `/*` and harmless — but it is worth knowing that path appears
twice.

## Why the order matters

- **`RequestAuditContextFilter` before the security filters.** Otherwise a
  blocked request would be recorded without a correlation id, and an
  authentication failure would be the one event you could not tie to a session.
- **`AuthenticationFilter` before `AdminAuthorizationFilter`.** The second can
  only answer "is this an admin?" if the first has already established who the
  user is. Reversed, every anonymous request to `/admin` would 403 instead of
  redirecting to login.
- **`SecurityHeadersFilter` before `CompressionFilter` would be fine, but
  `CompressionFilter` is deliberately third** so that a buffer installed before
  anything writes cannot miss a header set later.
- **`RateLimitingFilter` is mapped to the credential endpoints only** rather than
  `/*`. Limiting `/*` would make a product-catalogue page view compete with a
  brute-force attempt for the same budget.

## Known issue: `async-supported` is on the wrong element

All fifteen filters set `<async-supported>true</async-supported>` on the
`<filter>` element. Per the Servlet specification that attribute is only
meaningful on `<filter-mapping>`; on `<filter>` it is ignored. Consequently
**zero of the fifteen filter mappings declare `async-supported`**, and the SSE
endpoint `/realtime` throws
`IllegalStateException: Servlet output streaming is not supported` when it calls
`startAsync()`.

This is pre-existing and unrelated to payments. The fix is to add
`<async-supported>true</async-supported>` to each `<filter-mapping>`. It is
recorded in [../security/known-issues.md](../security/known-issues.md) rather
than fixed here, because it changes behaviour for every route and deserves its
own change with its own verification.
