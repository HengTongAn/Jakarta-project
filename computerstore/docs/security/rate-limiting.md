# Rate limiting

Two filters with different jobs. Both return **429 Too Many Requests** and both
keep state in a `ConcurrentHashMap` in the filter instance, reaping expired
entries at most once a minute.

## Per-IP — `RateLimitingFilter`

Mapped to the credential endpoints only: `/login`, `/register`, `/forgot`.

| Limit | Default | Property |
|---|---|---|
| Per IP, per minute | 10 | `computerstore.rl.perIp.maxPerMinute` |
| Per IP, per hour | 60 | `computerstore.rl.perIp.maxPerHour` |
| **Whole system**, per minute | 200 | `computerstore.rl.global.maxPerMinute` |

It is mapped to those three paths rather than `/*` on purpose. A limit on `/*`
would make a customer browsing the catalogue compete with a brute-force attempt
for the same budget, and the customer would be the one throttled.

### The global ceiling is the interesting part

The per-IP limits alone do not stop a distributed attack: a thousand distinct IPs
make a thousand attempts each and every one is under its own limit. So there is
a **whole-system budget of 200 POSTs per minute** across `/login`, `/register`,
and `/forgot` combined. Per-IP limits apply first; the global one is the net
underneath.

This is a single `synchronized` counter on the filter instance, which is a true
whole-system budget only because `web.xml` registers exactly one filter
instance. That assumption is written down in the code, because it is the kind of
thing that breaks silently if someone registers the filter twice or switches to
a deployment that fans requests across instances.

## Per-user — `UserRateLimitingFilter`

Mapped to `/cart`, `/cart/*`, `/checkout`, `/account`, `/account/*`, `/admin`,
`/admin/*`. Keyed on the session user id, not the IP.

| Role | Per minute | Per hour |
|---|---|---|
| `CUSTOMER` | 30 | 200 |
| `ADMIN` | 60 | 500 |

The IP limit cannot help here — an authenticated abuse case is one account, and
the per-user limit is what bounds it. Admins get more because admin pages are
legitimately heavier, and a throttled admin looks like an outage.

`clearUserRateLimit(userId)` exists for tests and for a future "sign out
everywhere".

## The forwarded-header trap

`getClientIp` reads `X-Forwarded-For` — but only when
`-Dcomputerstore.trust-forwarded-headers=true`. Otherwise it uses
`request.getRemoteAddr()`.

**Do not enable that property without a proxy you control in front.** With no
proxy, a client sets the header itself, and every per-IP limit is trivially
evaded by sending a rotating value. The same header feeds the audit log's
`ip_address`, so it forges the audit record too.

The same property is read by `AuditLogger` and by the IP-based limiter, and it
must be set for both to be correct together. Set it once, deliberately, at
startup.

## Properties are read at construction

Both filters read their limits in field initialisers, so changing a property
needs a **restart**, not a request. `Integer.getInteger` returns `null` for a
malformed value, and the unboxing would throw — the defaults are only safe
because the properties are absent by default.

## In-memory state, so per-instance

The counters are in the JVM. On a single Tomcat that is the whole system. On a
cluster, N instances means N times the budget, and the global ceiling in
particular stops being global. If this is ever deployed to more than one node,
both limits need a shared store (Caffeine is already a dependency, but a local
cache is not shared either) and the global counter needs to move out of the
filter instance.

That is a real limitation of the current design, recorded here rather than
implied away.

## What limiting does not cover

- **Password spraying across accounts.** One attempt per account, many accounts,
  all under a per-IP limit. Account lockout would address this; there is none.
- **A slow, low-rate attack.** Ten attempts a minute is a thousand a day, which
  is enough against a weak password.
- **Anything not in a filter mapping.** Adding an unauthenticated endpoint
  outside `/login`, `/register`, `/forgot` gets no IP limit at all.
