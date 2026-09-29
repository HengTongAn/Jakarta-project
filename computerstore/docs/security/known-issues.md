# Known issues

Open gaps, written plainly. None of these are hidden behind a "coming soon" —
each one is a decision waiting to be made, or a fix waiting to be made properly.

## 1. No acquirer integration

Card payment is **simulation only**. `isCardReady()` is a hard-coded `false`
because a store cannot call Visa directly.

`payment.card.enabled=true` with `payment.card.simulate=false` withholds the
card option rather than offering something broken — deliberate, and shown to the
operator on `/admin/payments`.

Integration with Stripe, Braintree, or PayPal would also mean the card form
moves off this application's server. See
[payment-data-handling.md](payment-data-handling.md#before-shipping-an-acquirer-integration).

## 2. The live ABA path has never been executed

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

## 3. Audit logging has no coverage guarantee

`AuditLogger` records what code chooses to record. The categories are enforced
by an enum; the *coverage* is per-callsite, and nothing fails when a new
sensitive action is not logged.

A test listing the actions that must be audited, and failing when one stops
calling `AuditLogger`, would make this a guarantee instead of a convention. Not
written yet. See [audit-logging.md](audit-logging.md).

### Fixed: keyword search in Admin History returned 500

Found while reading the server log rather than by testing, which is the part
worth remembering: nothing in the suite covered a *search term* being present,
so the page worked perfectly when the box was empty and failed on every real
use.

`AuditLogRepository.appendFilters` built its LIKE clause with
`ESCAPE '\'`. That is not a valid SQL string literal when backslash escaping is
on, which is the default: the `\` escapes the closing quote, so the literal
never terminates and MySQL keeps consuming what follows — including the `?`
parameter markers. The driver counted 3 parameters in a statement the DAO was
about to bind 6 values to:

```
java.sql.SQLException: Parameter index out of range (4 > number of parameters, which is 3)
  at ...AuditLogRepository.count(AuditLogRepository.java:57)
  at ...AdminHistoryServlet.doGet(AdminHistoryServlet.java:49)
```

`/admin/history` returned a 500 for any admin who typed in the search box.
Verified directly against MySQL 8.4.10: `ESCAPE '\'` is `ERROR 1064 syntax
error`; `ESCAPE '\\'` parses and returns rows.

The correct form was already in the codebase — `ProductRepository.applyFilters`
had `ESCAPE '\\\\'`, which is why product search always worked. The same clause
had been written two different ways in two files, and only one of them was
tested. Both are now `ESCAPE '\\\\'`, and
[`AuditLogRepositoryFilterSqlTest`](../../src/test/java/com/hengtongan/computerstore/core/repository/AuditLogRepositoryFilterSqlTest.java)
asserts the SQL text: six placeholders for six bound values across every filter
combination, and no `ESCAPE` literal that is not exactly `'\\'`.

The lesson generalises past this bug: a `?` inside a string that *looks* like a
parameter is not one, and the driver will not tell you which ones it found.

### Fixed: new scripts were shipped with a cache-busting token that ignored them

Found by code review, not by running the app — the pages rendered perfectly. The
defect is only visible across a *sequence* of deploys.

`header.jspf` computes `assetsVersion` as the newest last-modified time across a
hand-maintained array, and every asset reference appends it as
`?v=${assetsVersion}`. The value is cached in application scope, so it is
computed once per deploy and reused for every request after that.

Two scripts added in this change — `admin-nav.js` and `pwa.js` — were referenced
with `?v=` and were **not** in the array. A deploy that changed only one of them
would leave the version value untouched, so every page would keep requesting the
same URL. The service worker introduced in the same change made this worse rather
than better: it keys its cache on the full URL including the query string, so the
stale copy is now held in two places. The same `?v=` query that keeps assets
correct across deploys is what makes an un-listed asset permanent.

The gap was wider than the two new files. Eight assets carried no token at all —
`theme.js`, `dashboard-live.js`, `performance-live.js`, `product-spec-editor.js`,
`reports-live.js`, the Bootstrap and Bootstrap Icons stylesheets, Chart.js, and
the Bootstrap bundle — so no deploy could ever invalidate them. `performance-live.js`
is one of them, and it was edited in this change: the hit-rate fix could ship and
not reach the browser.

All 21 assets are now versioned and listed, and
[`AssetsVersionCoverageTest`](../../src/test/java/com/hengtongan/computerstore/web/config/AssetsVersionCoverageTest.java)
checks both directions so neither the array nor a reference can drift alone. The
wider lesson is that a cache-busting scheme with a hand-maintained list has to be
checked by something, because the failure mode is silence: the file on disk is
correct and only the page is wrong.

## 4. Rate limiting is per-instance

Both limiters keep counters in the filter's memory. That is correct for a single
Tomcat and wrong for a cluster: N instances is N times the budget, and the
"global" 200-per-minute auth ceiling stops being global.

The single-instance assumption is documented in `RateLimitingFilter` because it
is easy to break silently by registering the filter twice.

## 5. Four footer social links are placeholders

`support.facebook.url`, `support.messenger.url`, `support.telegram.url`, and
`support.x.url` in `app_settings` point at bare domains — `https://x.com/?lang=en`
and so on — rather than at an actual business page. The footer renders them as
links, so they are visibly wrong.

They are editable at `/admin/support`. They need the real URLs from whoever owns
the accounts.

## 6. A fresh install has 149 products, not 150

`src/main/resources/db/seed/seed-data.sql` contains **149** product tuples. The
live database contains **150**. The extra row is:

```
LAP-ACER-S233   Acer Swift 5 Ultrabook
```

It is in the database and **not** in the seed file, so a fresh install seeded
from `seed-data.sql` is missing it, and a product detail page for that SKU
returns 404 there while working here.

Verified by diffing both sides on SKU rather than trusting the counts: all 149
seed SKUs are present in the live table, there are no duplicate SKUs in the
seed, and every shared SKU also agrees on `name`. So the count difference is
this one row and not drift hidden behind it. `LAP-ACER-S233` appears in **no**
migration either — only in the live table, so it was added by hand rather than
by any scripted step.

Unresolved: add the row to `seed-data.sql`, or delete it from the database so
the two agree. Adding it is almost certainly right — an existing product should
not disappear from new installs — but it needs the exact row, which only exists
in the live table. Left as-is pending that decision.

The seed file also creates `brands`, `categories`, and a `users` row, so it is a
complete bootstrap rather than just a catalogue. It is not applied
automatically; see
[../deployment/local-development.md](../deployment/local-development.md#seeding-a-usable-catalogue).

## 7. There is no account lockout

`RateLimitingFilter` bounds attempts by IP, and `UserRateLimitingFilter` bounds
them per authenticated user. Neither bounds password guessing against a *single
account* from many IPs. Ten attempts a minute from one IP is a thousand a day,
which is ample against a weak password.

Account lockout, or a per-account attempt counter, would close it. Not
implemented.

## 8. `unsafe-inline` in the CSP

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

## 9. `AuthenticationFilter` coverage is a manual list

The filter is mapped to an explicit set of prefixes. A new URL family is
**unprotected by default** — no test fails, because the filter is configuration
and configuration is not compiled.

Worth a test that asserts every new `@WebServlet` path is either covered by an
authentication mapping or explicitly listed as public. Not written yet.

## 10. Session timeout is a fixed 30 minutes

`<session-timeout>30</session-timeout>` in `web.xml`. Not configurable without
editing the deployment. Fine for a demonstration, coarse for production.

---

# Fixed

Kept because the reasoning is reusable, and because the first diagnosis of
this one was wrong in an instructive way.

## `/realtime` returned 500 on every request

`GET /computerstore/realtime` threw `IllegalStateException: A filter or
servlet of the current chain does not support asynchronous operations` when
`RealtimeStreamServlet` called `startAsync()`, so `dashboard-live.js`,
`realtime.js`, `performance-live.js`, and `reports-live.js` never updated
without a refresh. The pages themselves rendered normally, which is what made
it look healthy.

**The first diagnosis was backwards.** This file previously claimed the
declaration sat on the wrong element and that moving it to
`<filter-mapping>` was the fix. That is the opposite of what Tomcat does.
Tomcat builds a `FilterDef` from the **`<filter>`** element and
`ApplicationFilterChain.findNonAsyncFilters()` checks
`FilterDef.getAsyncSupportedBoolean()` — established by disassembling
`ApplicationFilterChain` from Tomcat 11's `catalina.jar`. The Servlet
specification does put the declaration on `<filter-mapping>`, and Tomcat
ignores it there. Applying the "fix" as originally described reproduces the
500 exactly; that was tried before the real cause was found.

**The real cause was much smaller.** `SupportChannelFilter` and
`ReviewCountFilter` were never given the declaration at all. Both are mapped
to `/*`, so both sit on the `/realtime` chain, and one omission anywhere in
that chain fails the whole request. The original ten filters that did declare
it were fine.

**The fix** declares `<async-supported>true</async-supported>` on all fifteen
`<filter>` *and* all fifteen `<filter-mapping>` elements. Both locations, so
the descriptor is correct per the specification and correct per Tomcat.

**Why no test caught it.** `RealtimeStreamServletTest` and
`EventHubDisconnectTest` drive the servlet directly and never read the
descriptor, so both passed against a `web.xml` that made the deployed
endpoint return 500. Configuration is not compiled. `AsyncSupportDescriptorTest`
now reads the descriptor and asserts the chain invariant for every
`asyncSupported = true` servlet.

**Verified after the fix:** `/realtime` returns `200`,
`Content-Type: text/event-stream`, and the `retry: 3000` handshake, holding
the connection open as an SSE stream should. `233` tests pass. See
[../architecture/request-lifecycle.md](../architecture/request-lifecycle.md).
