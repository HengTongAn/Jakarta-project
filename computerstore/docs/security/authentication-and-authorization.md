# Authentication and authorization

## Passwords

`util/security/PasswordUtil` is a thin BCrypt wrapper:

```java
BCrypt.hashpw(plainPassword, BCrypt.gensalt(10))
BCrypt.checkpw(plainPassword, storedHash)
```

Cost 10, per-password salt, verification is constant-time. `password_hash` is
`VARCHAR(100)`, which is enough for a cost-10 BCrypt hash and is not enough for
anything longer — so a plaintext password cannot be stored by accident either.

`DefaultCredentialsChecker` runs at startup and warns about accounts still using
a default or sample password. It is a warning, not a lockout.

## Sessions

30-minute timeout (`web.xml` `<session-timeout>`). The session holds the user id
and the CSRF token, not the user object.

`SessionUserRefreshFilter` runs on every request, throttled to one database read
per interval, and re-reads the user. Three things follow from that:

- **A demoted admin loses admin access immediately**, without a re-login. The
  role is re-read, and `AdminAuthorizationFilter` runs after it.
- **A promoted customer gains it the same way.**
- **A soft-deleted account's session is ended at once**, not at timeout.

This closes the window where a revoked account keeps working for as long as its
session lives. It is the main reason the filter chain is ordered the way it is.

## Two-factor authentication

`infrastructure/security/TwoFactorAuthService` implements TOTP (Google Authenticator,
`com.googleauth:googleauth`) at `/account/2fa`.

The shared secret is **encrypted at rest**. The key comes from
`COMPUTERSTORE_2FA_ENCRYPTION_KEY`, or the
`computerstore.2fa.encryption.key` system property. If neither is present the
service **refuses to provision** rather than falling back to plaintext — the
failure mode is a customer who cannot enable 2FA, not a secret in the database.

Per-user enablement and the secret live in `two_factor_secrets`, which cascades
on user delete.

## The two authorization filters

They answer different questions and produce different responses on purpose.

### `AuthenticationFilter` — *are you logged in?*

Mapped to `/admin`, `/admin/*`, `/cart`, `/cart/*`, `/checkout`, `/payment/aba`,
`/payment/aba/*`, `/payment/card`, `/payment/card/*`, `/account`, `/account/*`.

Anonymous → **302** to `/login?return=<path>`. The reasonable response is to log
in, so it is a redirect. The return path is the context path stripped, and it is
re-anchored only if it already starts with the context path, so a crafted
`return` cannot bounce a customer to another host.

It also records `last_active_at`, throttled, for the presence indicator.

### `AdminAuthorizationFilter` — *are you allowed?*

Mapped to `/admin`, `/admin/*`, and it runs **after** `AuthenticationFilter` so
the user is already known to be logged in.

Not an admin → **403**, forwarding to `WEB-INF/views/errors/403.jsp`. Logging in
again will not help, so a redirect would be a loop.

```java
if (user == null || !user.isAdmin()) {
    request.getRequestDispatcher("/WEB-INF/views/errors/403.jsp").forward(request, response);
    return;
}
```

`User.isAdmin()` is `role == ADMIN || role == SUPER_ADMIN`.
`isSuperAdmin()` is the narrower check.

If these two filters are ever reordered, every anonymous request to `/admin`
returns 403 instead of redirecting to login, and the login page becomes
unreachable from there. That is the whole reason the order is documented in
[../architecture/request-lifecycle.md](../architecture/request-lifecycle.md).

## The coverage list is a maintenance burden

`AuthenticationFilter` is mapped to an explicit list of prefixes. **A new URL
family is unprotected by default.** A new `/api/...` or `/reports` prefix serves
anonymously until someone adds it to `web.xml`.

When you add a URL family, decide at that moment: does it need a session, and
does it need a role? Then edit `web.xml`. There is no test that fails if you
forget — the filter is configuration, and configuration is not compiled.

## Row-level checks

Passing the filters means "a valid user of an allowed role". It says nothing
about *which* rows that user may see. That is per-endpoint, and it is where
identity bugs actually live.

The pattern, from `CardPaymentServlet`:

```java
// /payment/card/<orderId> -- an order id belonging to another customer must
// not render, and must not confirm the order exists.
if (!ownOrder(orderId)) {
    response.sendRedirect(request.getContextPath() + "/account/orders");
    return;
}
```

Deliberately a **redirect**, not a 404 and not a 403. All three leak the
existence of the row; a redirect leaks nothing. The convention to follow for any
endpoint that takes an id in the path:

1. Resolve the row by that id.
2. Check ownership explicitly. Do not rely on the query having been scoped.
3. On failure, redirect somewhere generic. Do not differentiate "does not exist"
   from "not yours".

`/account/orders` scopes its query to the session user rather than filtering
afterwards, which is stronger — a missing row cannot be forgotten. Prefer
scoping in SQL where you can.

## Path traversal

Two servlets serve files from disk by path: `ProductImageServlet`
(`/product-images/*`) and `AvatarImageServlet` (`/avatars/*`). Both resolve the
path under a configured base directory and must reject anything that escapes it.
`UploadConfig` resolves the base from `COMPUTERSTORE_UPLOAD_DIR`, then
`computerstore.upload.dir`, then `$HOME/computerstore-uploads`.

`FileUploadUtil` has its own test coverage. If you extend either servlet to
accept a new path form, extend the test with a `../` case.

## Input validation

`util/validation/ValidationUtil` holds the field-level rules; servlets call it
and services call it, and the service call is the one that matters because the
browser is not trusted.

Card validation is the strictest case in the codebase and is documented
separately: [payment-data-handling.md](payment-data-handling.md).

## SQL injection

Prepared statements with bound parameters in every repository. No user input is
concatenated into SQL. `ORDER BY` and similar structural clauses are the
exception that always needs care — a column name cannot be a bound parameter, so
any place that sorts must map a user-supplied sort key to a fixed set of
whitelisted SQL fragments. Check that when you add a sort.

## Related

- [csrf.md](csrf.md)
- [rate-limiting.md](rate-limiting.md)
- [known-issues.md](known-issues.md)
