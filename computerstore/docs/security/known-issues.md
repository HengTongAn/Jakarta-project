# Known issues

Open gaps, written plainly. None of these are hidden behind a "coming soon" —
each one is a decision waiting to be made, or a fix waiting to be made properly.

## 1. `/realtime` returns 500

`GET /computerstore/realtime` throws
`IllegalStateException: Servlet output streaming is not supported` when
`RealtimeStreamServlet` calls `startAsync()`.

**Cause.** All fifteen filters set `<async-supported>true</async-supported>` on
the **`<filter>`** element. Per the Servlet specification that attribute is only
meaningful on **`<filter-mapping>`**; on `<filter>` it is ignored. So **zero of
the fifteen filter mappings declare `async-supported`**, and by the time the
request reaches the servlet, an async request is not permitted.

**Fix.** Add `<async-supported>true</async-supported>` to each of the fifteen
`<filter-mapping>` elements.

**Why it is not fixed here.** It changes behaviour for every route in the
application, not just the SSE endpoint. It deserves its own change, its own
verification, and its own review — not a drive-by edit inside payment work.
`RealtimeStreamServletTest` and `EventHubDisconnectTest` exist and will need to
be run against it.

**Impact.** The live-update features — `dashboard-live.js`, `realtime.js`,
`performance-live.js`, `reports-live.js` — do not stream. The pages themselves
render; they just do not update without a refresh.

## 2. `async-supported` is a systemic misplacement

Same root cause as above, stated separately because it affects every future
async endpoint. There are two ways to write an async servlet in this
application — `@WebServlet(asyncSupported = true)`, which `RealtimeStreamServlet`
does — and neither is sufficient if the filter chain does not allow async.

If you add a streaming or websocket endpoint, this is the first thing that will
stop it.

## 3. No acquirer integration

Card payment is **simulation only**. `isCardReady()` is a hard-coded `false`
because a store cannot call Visa directly.

`payment.card.enabled=true` with `payment.card.simulate=false` withholds the
card option rather than offering something broken — deliberate, and shown to the
operator on `/admin/payments`.

Integration with Stripe, Braintree, or PayPal would also mean the card form
moves off this application's server. See
[payment-data-handling.md](payment-data-handling.md#before-shipping-an-acquirer-integration).

## 4. The live ABA path has never been executed

Every ABA behaviour that has been verified went through
`payment.aba.simulate=true`. Before setting `payment.aba.simulate=false`,
confirm against ABA Payway's official documentation:

- the exact endpoint paths under `payment.aba.api_url`
- the field names — **`transId` versus `TRANS_ID`**, and the rest
- the required request headers
- the currency parameter, and whether `USD` is accepted
- the meaning of each response code, and which of them are retryable

`AbaPaywayClient` was written against the documented shape, not against a
working call. Treat the first live attempt as untested code.

## 5. Audit logging has no coverage guarantee

`AuditLogger` records what code chooses to record. The categories are enforced
by an enum; the *coverage* is per-callsite, and nothing fails when a new
sensitive action is not logged.

A test listing the actions that must be audited, and failing when one stops
calling `AuditLogger`, would make this a guarantee instead of a convention. Not
written yet. See [audit-logging.md](audit-logging.md).

## 6. Rate limiting is per-instance

Both limiters keep counters in the filter's memory. That is correct for a single
Tomcat and wrong for a cluster: N instances is N times the budget, and the
"global" 200-per-minute auth ceiling stops being global.

The single-instance assumption is documented in `RateLimitingFilter` because it
is easy to break silently by registering the filter twice.

## 7. Four footer social links are placeholders

`support.facebook.url`, `support.messenger.url`, `support.telegram.url`, and
`support.x.url` in `app_settings` point at bare domains — `https://x.com/?lang=en`
and so on — rather than at an actual business page. The footer renders them as
links, so they are visibly wrong.

They are editable at `/admin/support`. They need the real URLs from whoever owns
the accounts.

## 8. A fresh install has 149 products, not 150

`src/main/resources/db/seed/seed-data.sql` contains **149** product tuples. The
live database contains **150**. The extra row is:

```
LAP-ACER-S233   Acer Swift 5 Ultrabook
```

It is in the database and **not** in the seed file, so a fresh install seeded
from `seed-data.sql` is missing it, and a product detail page for that SKU
returns 404 there while working here.

Unresolved: add the row to `seed-data.sql`, or delete it from the database so
the two agree. Adding it is almost certainly right — an existing product should
not disappear from new installs — but it needs the exact row, which only exists
in the live table.

The seed file also creates `brands`, `categories`, and a `users` row, so it is a
complete bootstrap rather than just a catalogue. It is not applied
automatically; see
[../deployment/local-development.md](../deployment/local-development.md#seeding-a-usable-catalogue).

## 9. There is no account lockout

`RateLimitingFilter` bounds attempts by IP, and `UserRateLimitingFilter` bounds
them per authenticated user. Neither bounds password guessing against a *single
account* from many IPs. Ten attempts a minute from one IP is a thousand a day,
which is ample against a weak password.

Account lockout, or a per-account attempt counter, would close it. Not
implemented.

## 10. `unsafe-inline` in the CSP

`script-src 'self' 'unsafe-inline'` means the CSP cannot stop an injected inline
script or an inline event handler. It does block an external
`<script src="https://...">`, which is the common injection vector, so it is a
real mitigation — just not a complete one.

Present because the application uses inline bootstrapping scripts and inline
event handlers. Removing it is a genuine project: move the inline scripts to
files, replace inline handlers with delegated listeners, move inline styles, then
add a nonce. Until then the XSS defence is JSP auto-escaping, and that is what
it should be relied on for. See
[headers-and-csp.md](headers-and-csp.md#the-unsafe-inline-problem).

## 11. `AuthenticationFilter` coverage is a manual list

The filter is mapped to an explicit set of prefixes. A new URL family is
**unprotected by default** — no test fails, because the filter is configuration
and configuration is not compiled.

Worth a test that asserts every new `@WebServlet` path is either covered by an
authentication mapping or explicitly listed as public. Not written yet.

## 12. Session timeout is a fixed 30 minutes

`<session-timeout>30</session-timeout>` in `web.xml`. Not configurable without
editing the deployment. Fine for a demonstration, coarse for production.
