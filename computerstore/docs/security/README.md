# Security

What the application does, what it deliberately does not do, and where the gaps
are. Written to be audited, not to reassure.

## Index

| Page | Covers |
|---|---|
| [authentication-and-authorization.md](authentication-and-authorization.md) | Sessions, password storage, 2FA, the two authorization filters, row-level checks |
| [csrf.md](csrf.md) | Token lifecycle, constant-time comparison, exemptions |
| [rate-limiting.md](rate-limiting.md) | Per-IP and per-user limits, the forwarded-header trap |
| [headers-and-csp.md](headers-and-csp.md) | Every response header and what it defends against |
| [audit-logging.md](audit-logging.md) | What is recorded, correlation, retention |
| [payment-data-handling.md](payment-data-handling.md) | Why no card number can be stored |
| [known-issues.md](known-issues.md) | Open gaps, stated plainly |

## Summary

Implemented and verified:

- Passwords stored as **BCrypt cost-10 hashes**. No plaintext anywhere.
- **CSRF tokens** on every POST, compared in constant time.
- **Per-IP rate limiting** on credential endpoints; **per-user** elsewhere.
- **Session re-validation**: a role change, profile edit, or soft delete takes
  effect without re-login, and a deleted account's session is ended immediately.
- **Response security headers** on every request, including a CSP with no
  external origins — assets are vendored, not CDN-loaded.
- **Audit logging** of auth, admin, data, and security events, with request and
  session correlation.
- **SQL injection**: prepared statements with bound parameters throughout. No
  user input is concatenated into SQL.
- **2FA** (TOTP) with an encrypted at-rest secret.
- **Card data**: a validator that accepts only six published sandbox numbers,
  returning a type with nowhere to put a real number.

Known gaps, all recorded in [known-issues.md](known-issues.md) — ten open
items, plus a **Fixed** section at the end carrying the post-mortems:

- Four footer social URLs are placeholders.
- No acquirer integration, so card payment is simulation only.
- The live ABA path has never been executed against the real gateway.

## Things that would be wrong to assume

**"It's behind a filter, so it's authenticated."** `AuthenticationFilter` covers
a specific list of prefixes. `/admin/payments` is on it; a new `/api/...`
prefix is not. Adding a URL family means adding it to the filter, and the
default is unprotected.

**"Admin pages are safe because of AdminAuthorizationFilter."** That filter
answers "is this user an admin". It does not answer "may this admin see *this*
row". Row-level checks are per-endpoint, and `ownOrder`-style guards are a
convention that has to be followed deliberately. See
[authentication-and-authorization.md](authentication-and-authorization.md#row-level-checks).

**"CSP means XSS is handled."** `script-src 'self' 'unsafe-inline'` is in
effect. The application depends on inline bootstrapping scripts and inline event
handlers, so `unsafe-inline` is there. That directive blocks external script
injection; it does not block an injected inline handler. The CSP is a real
mitigation and it is not complete. See
[headers-and-csp.md](headers-and-csp.md#the-unsafe-inline-problem).

**"The audit log records what happened."** It records what code chose to record.
A new sensitive action is not logged because nobody added a call. The categories
exist; the coverage is per-callsite.

**"Simulation is temporary scaffolding."** The card and ABA simulation paths are
the only paths that have ever been executed. Do not reason about the live path
from the behaviour of the simulated one — they differ in exactly the parts that
are hard to test.
