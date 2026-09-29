# CSRF protection

## The mechanism

`web/filter/security/CSRFProtectionFilter` applies to **POST only**:

```java
if (!"POST".equalsIgnoreCase(request.getMethod()) || isExcludedPath(path)) {
    chain.doFilter(request, response);
    return;
}
```

Everything else passes through untouched. A `GET` that mutates state is a
different bug and this filter does not, and cannot, help with it.

## The token

`util/security/CSRFUtil`:

- Generated once per session and stored under the session attribute
  `csrfToken`. It is **not** rotated on every request.
- Read from the request as the `csrfToken` parameter **or** the
  `X-CSRF-Token` header. The header path is what the JavaScript live-update code
  uses.
- Compared with `MessageDigest.isEqual`, not `String.equals`.

```java
// Time-constant comparison so an attacker cannot measure how many
// leading characters were correct and narrow the search.
MessageDigest.isEqual(sessionToken.getBytes(UTF_8), requestToken.getBytes(UTF_8))
```

`String.equals` returns as soon as two bytes differ, which turns token
verification into an oracle. With a 128-bit token it is not practically
exploitable, but it costs nothing to be correct and `isEqual` is the right call.

`rotateToken(session)` exists and is used where a privilege boundary is crossed.

## In the views

`WEB-INF/views/components/csrf.jspf` renders the hidden input. Include it in
every form that posts.

```jsp
<form method="post" action="...">
  <%@ include file="../components/csrf.jspf" %>
  ...
</form>
```

A form that posts without it **fails loudly in the browser** rather than
silently. That is the correct failure mode, but it presents as a 403 on a page
that looks correct, so when a new form "does not submit", check the include
first.

## Exemptions

**None.** Every POST in the application is checked, including `/login` and
`/register`.

Exempting the two unauthenticated forms is a tempting shortcut — there is no
session-bound token to protect yet — but it is exactly the case worth
protecting. A login-CSRF attack forces a victim to authenticate as an
attacker's account: whatever the victim then does (saving an address, typing a
card number) is recorded against an account the attacker can read. The token
costs nothing here, because both forms render a page first, and that page
issues the token in the session the POST is then checked against.

`EXCLUDED_PATHS` in `CSRFProtectionFilter` exists as the extension point for
this list, and it is deliberately empty. Adding a path to it should come with a
note here saying what replaces the protection.

## Two mappings for one filter

`CSRFProtectionFilter` appears **twice** in `web.xml`: once for `/*` and once
for `/account/settings`. The second is redundant — `/*` already covers it — and
harmless, since a filter mapping is not applied twice to the same request.

Worth knowing so nobody spends time on it while debugging filter counts.

## What this does not cover

- **GET-based state changes.** Not covered. `CartServlet` handles `/cart/add`,
  `/cart/update`, and `/cart/remove` as POST, which is correct.
- **Same-origin requests from XSS.** A cross-site request cannot forge a token;
  a script running on the page can read it. CSRF protection is not a defence
  against XSS, and the CSP is not complete either — see
  [headers-and-csp.md](headers-and-csp.md#the-unsafe-inline-problem).
- **Anything not reachable through the filter chain.** If a future request type
  (websocket upgrade, for example) bypasses `/*`, it bypasses the token check.
