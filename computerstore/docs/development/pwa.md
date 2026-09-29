# The PWA layer

The storefront is installable: Android Chrome, desktop Chrome and Edge offer
"Install app", and iOS Safari offers "Add to Home Screen". Installed, it launches
standalone with its own icon and no browser chrome, from a `start_url` that lands
on the catalogue.

Five files, all in `src/main/webapp/`:

| File | Purpose |
|---|---|
| `manifest.webmanifest` | name, icons, `start_url`, `scope`, colours |
| `sw.js` | the service worker: shell caching, offline page |
| `offline.html` | static page shown when a navigation fails |
| `assets/js/pwa.js` | registration, deferred until after `load` |
| `assets/images/icons/*.png` | 192, 512, maskable pair, and the iOS 180 |

`header.jspf` links the manifest, the `theme-color`, the `apple-mobile-web-app-*`
tags and `pwa.js`.

## What is cached, and what is deliberately not

**The service worker never caches HTML.** This is the decision that matters, and
it is not a limitation — it is the only safe choice for this application.

Every page here is built per request from the session. The cart, the order list,
`/account`, `/account/profile` and the entire admin area render different content
for different people. A cached document would show the previous user's cart to
the next person who opens the app on a shared or sold device, and would keep
serving a page that is already stale after a deploy. There is no correct cached
page to return, so none is stored:

- **Navigations** — network only. On failure, the static `offline.html`.
- **JSON endpoints** (`/cart/count`, the admin dashboards) — network only, for
  the same reason: per-user data, not assets.
- **`/realtime`** — passed straight through, never intercepted. It is a
  long-lived SSE response; a cache wrapper would buffer it and break streaming.

What *is* cached is the shell: stylesheets, scripts, images, icons — public,
identical for everyone, and already cache-busted by the `?v=<assetsVersion>`
query that `header.jspf` appends, so a deploy changes the URL and the worker
refetches instead of serving last week's stylesheet. The policy is
stale-while-revalidate on a single `asf-shell-vN` cache, with old versions
deleted on activate.

### Verified in a browser, not inferred

A manifest that parses and a worker file that returns 200 prove nothing about
whether the worker registers or what it stores. Driven over CDP against the
deployed app:

- registration is `activated`, scope `http://localhost:8080/computerstore/`
- the page is `controlled` after a reload
- the cache holds 22 shell entries
- the only `.html` in any cache is `offline.html`
- `/realtime` appears in no cache
- with the network cut, a request for a per-user page **fails** rather than
  returning session content

One caveat, because it would be easy to over-claim: cutting the network with
`Network.emulateNetworkConditions` does not stop Chrome answering a navigation
from its own memory cache, so the offline *page* was never observed rendering.
Two earlier "leaks" in that harness were this artifact — including a
same-URL navigation that left the previous DOM in place untouched. What is
verified is the part the worker controls: it stores no per-user document, and
serving one to an offline request fails.

## The context path

The app is deployed at `/computerstore`, and this bit people up. **A manifest
resolves its URLs against the manifest's own URL**, so an absolute
`"start_url": "/products"` resolves to the *server root* and 404s. Every path in
the manifest is relative:

```json
"start_url": "./products",
"scope": "./",
"id": "./products"
```

These resolve to `<context>/products` on this host and would resolve correctly
on a host serving the app at `/` as well, with no change. The same rule applies
to `offline.html` and to `pwa.js`, which reads `window.CTXPATH` (set in
`header.jspf`) to compute the worker's scope.

The worker must be served from the root of its scope, which is why `sw.js` sits
at the app root and not under `/assets`.

## `application/manifest+json`

Tomcat ships no MIME mapping for `.webmanifest`, so the manifest was being served
with **no content type at all** — which browsers tolerate and Lighthouse flags.
`web.xml` now declares it:

```xml
<mime-mapping>
    <extension>webmanifest</extension>
    <mime-type>application/manifest+json</mime-type>
</mime-mapping>
```

## Icons

Generated from `assets/images/apach-pc-store.svg` by a script, not hand-drawn,
so they are reproducible: `icon.svg` and `icon-maskable.svg` in
`assets/images/icons/` are the sources, and re-rendering them at a different size
reproduces the PNGs.

Two details that are easy to get wrong:

- **The background must not be one of the logo's own colours.** The logo
  contains `#7C297D`, so a purple field would make part of it invisible. The
  field is `#0f172a` — the app's `--ink` token, which contrasts with all four
  logo colours and is also what the storefront looks like in dark mode.
- **iOS ignores an SVG `apple-touch-icon`** and falls back to a screenshot of the
  page. `header.jspf` previously pointed that link at the SVG; it now points at
  a real 180×180 PNG.

Maskable icons put the logo at 52% of the canvas, inside the 80% safe circle, so
Android's circle and squircle crops do not clip it.

## iOS notes

`apple-mobile-web-app-capable` and the status-bar style are set. iOS does not use
`manifest.webmanifest` for installation, so the manifest's `display` and icons
are inert there and the `apple-touch-icon` PNG is what the home screen shows.
iOS also delivers no service worker install prompt, so the worker may not run at
all on Safari — the app degrades to an ordinary bookmark, which is fine.
