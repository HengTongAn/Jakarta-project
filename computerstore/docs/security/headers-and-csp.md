# Response headers and CSP

`web/filter/security/SecurityHeadersFilter` runs on `/*` and sets every header
below on every response.

## The headers

| Header | Value | Defends against |
|---|---|---|
| `X-Frame-Options` | `DENY` | Clickjacking. |
| `X-Content-Type-Options` | `nosniff` | MIME sniffing turning a text file into script. |
| `X-XSS-Protection` | `1; mode=block` | Legacy XSS filters in older browsers. |
| `Content-Security-Policy` | see below | Resource loading from unexpected origins. |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Leaking full URLs to third parties. |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=(), payment=(), usb=()` | Feature access the app never uses. |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` | Downgrade to HTTP. **Opt-in.** |

### `X-Frame-Options: DENY` and the CSP agree

The CSP also carries `frame-ancestors 'none'`, which is the modern equivalent.
Having both is deliberate: `frame-ancestors` is ignored by older browsers, and
`X-Frame-Options` by none.

`DENY` is stricter than `SAMEORIGIN` and the application genuinely has no use
case for being framed.

### HSTS is off by default

```java
private static final boolean ENABLE_HSTS = Boolean.parseBoolean(
        System.getProperty("security.hsts.enabled", "false"));
...
if (ENABLE_HSTS && request.isSecure()) {
    response.setHeader("Strict-Transport-Security", ...);
}
```

Two conditions, both required. The default is off because enabling HSTS on a
host reachable over plain HTTP is self-defeating, and because
`includeSubDomains; preload` is effectively irreversible — browsers that have
seen the header will refuse HTTP for that host and its subdomains for a year.

Turn it on with `-Dsecurity.hsts.enabled=true` **only** once HTTPS is confirmed
working end to end.

## The CSP

```
default-src 'self';
base-uri 'self';
object-src 'none';
script-src 'self' 'unsafe-inline';
style-src 'self' 'unsafe-inline';
img-src 'self' data: https:;
font-src 'self';
connect-src 'self';
frame-ancestors 'none';
form-action 'self'
```

The parts doing real work:

- **`object-src 'none'`** — no plugins, no `<object>`, no `<embed>`. Unused
  surface, removed.
- **`frame-ancestors 'none'`** — modern clickjacking defence.
- **`form-action 'self'`** — a form cannot post to another origin, which would
  neuter a lot of credential phishing.
- **`base-uri 'self'`** — stops a `<base>` tag from rewriting every relative URL
  in the page.
- **No external origins.** Bootstrap, Bootstrap Icons, and Chart.js are
  **vendored** under `/assets/vendor/`. There is no CDN in the allowlist, so
  there is no third party in the page's critical path and no third party who can
  be compromised into running script in it.
- **`connect-src 'self'`** — no outbound XHR/fetch to anywhere else, so
  exfiltration over `fetch` is blocked.

`img-src 'self' data: https:` is the one permissive directive. `data:` is for
inline images and `https:` is because product `image_url` and `source_url` may
point at external hosts. That is a deliberate trade: remote product images over
HTTP would be blocked and mixed content, so external HTTPS images are allowed.

## The `unsafe-inline` problem

`script-src 'self' 'unsafe-inline'` and `style-src 'self' 'unsafe-inline'` are
the weak point, and they are a real weakening rather than a formality.

`unsafe-inline` is present because the application uses inline scripts for
bootstrapping — `CTXPATH`, dashboard data — and inline event handlers in the
JSPs. With it enabled, a CSP **cannot** stop an injected inline `<script>` or
`onclick` attribute. The CSP blocks a `<script src="https://evil.example">`,
which is the common case, and does not block the inline variant.

So: the CSP is a genuine mitigation and it is **not** complete, and it is not a
substitute for output encoding.

Removing it is a real project, not a config change:

1. Move every inline script to a file under `/assets/js/`, passing data through
   a `data-*` attribute or a JSON endpoint rather than a generated script body.
2. Replace inline `onclick`/`onsubmit` with delegated listeners in `app.js`.
3. Move inline `<style>` to the stylesheets.
4. Then drop `unsafe-inline` and add a nonce or hash.

Until that happens, the practical rule is: **this application depends on output
encoding and JSP auto-escaping for XSS defence, not on the CSP.** `<c:out>` by
default, and `${...}` in template text is escaped unless `<c:out escapeXml="false">`
says otherwise. Grep for that before adding new output.

A note relevant to JSP specifically: **markup inside `${...}` in template text is
parsed as a real tag and never evaluated.** If you find yourself fighting that,
it is not a bug in EL — build the value first with `<c:set>` and string coercion.

## What is not covered

- **`X-XSS-Protection` is deprecated.** Every current browser has removed its
  XSS auditor, so the header does nothing except trigger a console warning on
  older Chrome. It is harmless, not helpful.
- **No `Cross-Origin-*` headers.** The app serves no cross-origin resources, so
  there is nothing to relax.
- **No `Cache-Control` on HTML.** `StaticResourceCacheFilter` covers
  `/assets/**` only. Pages are `no-cache` by the servlet container's default,
  which is right for an application where a response depends on who is logged in.
