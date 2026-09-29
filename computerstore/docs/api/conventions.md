# API conventions

## Post/Redirect/Get

A successful `POST` **redirects**. It never renders. That is what stops a refresh
from re-submitting an order, and it is why the failure paths can be render
instead.

Failure is the mirror image: a `POST` that fails re-renders the form the
customer came from, with the reason. A lost cart returns them to the cart with
a message; a rejected card returns them to checkout.

## Flash messages

`util/web/Flash.java` carries a message across exactly one redirect, keyed off
the session. The pattern is always the same three lines:

```java
Flash.setMessage(request, "Your order has been placed.");
response.sendRedirect(request.getContextPath() + "/account/orders");
```

`Flash.getMessage(request)` reads and clears. Because the read clears, a second
render after the same redirect shows nothing — which is the intended behaviour,
and the reason a view that forgets to read the flash loses the message rather
than showing it twice.

## Status codes

| Code | When |
|---|---|
| 200 | Successful `GET`, and `POST` responses that render (mostly failures, and the deliberate read-only renders). |
| 302 | After a successful `POST`, after login, and when `AuthenticationFilter` bounces an anonymous user to `/login`. |
| 400 | Validation failure the servlet chooses to surface as such. |
| 403 | `AdminAuthorizationFilter` — logged in, wrong role. Also the dedicated `/WEB-INF/views/errors/403.jsp`. |
| 401 | A JSON endpoint reached without a session user. `/cart/count` still answers `{"count":0}` so the badge has something to render. |
| 404 | Unknown path, or an entity id that does not resolve. |
| 500 | Unhandled exception, forwarded to `/error`, which renders `500.jsp`. |
| 429 | Rate limit exceeded, from `RateLimitingFilter` or `UserRateLimitingFilter`. |

## Error handling

`web.xml` declares four error pages: `403`, `404`, `500`, and a catch-all
`<exception-type>java.lang.Exception</exception-type>` forwarded to `/error`.

`ErrorServlet` reads `jakarta.servlet.error.status_code` and
`jakarta.servlet.error.exception`, logs the exception class and a message, then
forwards to `403.jsp`, `404.jsp`, or `500.jsp` according to the status. It also
has a JSON branch: if the request wants JSON it returns
`{"error": "...", "status": N}` instead of a page.

The important property is that the exception **class name and message go to the
log, not to the customer**. `500.jsp` shows a generic message. Any exception
message that could contain a SQL fragment, a card field, or a filesystem path
must not reach the response body.

## CSRF

Every `POST` requires the token, read from the `csrfToken` parameter or the
`X-CSRF-Token` header. There are no exemptions — see
[../security/csrf.md](../security/csrf.md) for why `/login` and `/register`
are included.

`components/csrf.jspf` renders the hidden input for forms. A view that posts
without including it will fail in the browser, not silently — which is the
desired failure mode, but it is worth knowing when a form is authored by hand.

See [../security/csrf.md](../security/csrf.md).

## Authorisation

Two filters, and they are distinct on purpose:

- `AuthenticationFilter` — *are you logged in?* Anonymous gets a **302** to
  `/login?return=<path>`, because the reasonable response is to log in.
- `AdminAuthorizationFilter` — *are you allowed?* Non-admin gets a **403**,
  because logging in again will not help.

`User.isAdmin()` is `role == ADMIN || role == SUPER_ADMIN`. `isSuperAdmin()` is
the narrower check and is used where only `SUPER_ADMIN` should proceed.

## Authorisation beyond roles

Being an admin is not sufficient for row-level access. Endpoints that take an
id in the path re-check ownership before rendering. `/payment/card/<orderId>`
forwards to a redirect when the order is not the session user's, and
`/account/orders` scopes its query to the session user. This is the
`ownOrder` guard in `CardPaymentServlet`, and it is the pattern other
id-in-path endpoints should follow.

## Input validation

Validation happens in `util/validation`, not in servlets, and the server is
always the authority. Client-side validation is a usability affordance. The
clearest example in the codebase is the card form: the browser applies
`required` only while the card panel is showing, but the server re-validates
every field regardless of what the browser did.

Numbers and strings that reach SQL go through prepared statements with bound
parameters. There is no string concatenation of user input into SQL anywhere in
the repository layer.

## JSON responses

There is no JSON API in the general sense. JSON appears in three places, each
written by hand with `util/json/MiniJson` rather than a library:

- `/cart/count` — the nav badge.
- `/error` — the JSON error branch.
- Live dashboard and report updates driven by `dashboard-live.js` and
  `reports-live.js`.

`MiniJson` is minimal by design: it has an escaping test, and adding a key means
escaping the value. Do not use it for anything with nested user input until
that is done deliberately.
