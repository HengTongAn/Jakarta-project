/*
 * Apach_PC/STORE service worker.
 *
 * SCOPE OF WHAT THIS DELIBERATELY DOES NOT CACHE
 * ----------------------------------------------
 * This app is server-rendered and session-bound. Every page is built per
 * request from the session: the cart, the orders list, the account page and
 * the whole admin area all differ per user. So:
 *
 *   - Navigations are NETWORK ONLY. Caching HTML would serve one customer's
 *     cart to the next person on the device, and would keep serving a page
 *     that is already out of date after a deploy. There is no safe way to
 *     cache it, so it is not cached.
 *   - JSON endpoints (/cart/count, admin dashboards, /metrics) are NETWORK
 *     ONLY for the same reason: they are per-user data, not assets.
 *   - The /realtime SSE stream is passed straight through, untouched. It is a
 *     long-lived response; a cache wrapper would buffer it and break
 *     streaming, and a cached "response" would be meaningless.
 *
 * What is cached is the shell: stylesheets, scripts, images and icons. Those
 * are public, identical for everyone, and already cache-busted by the
 * `?v=<assetsVersion>` query that header.jspf appends, so a deploy changes the
 * URL and this worker refetches instead of serving a stale sheet.
 *
 * Offline behaviour is a small static page (offline.html) rather than a
 * cached copy of the app, because there is no correct cached page to show.
 */

const VERSION = 'v1';
const SHELL_CACHE = `asf-shell-${VERSION}`;
const OFFLINE_URL = './offline.html';
const MANIFEST_URL = './manifest.webmanifest';

/* Public, immutable-per-deploy assets. Anything not listed is network only. */
const CACHEABLE_EXT = new Set([
  '.css', '.js', '.mjs', '.png', '.jpg', '.jpeg', '.svg', '.webp',
  '.gif', '.ico', '.woff', '.woff2', '.ttf', '.otf', '.map',
]);

const PRECACHE = [
  OFFLINE_URL,
  MANIFEST_URL,
  './assets/images/icons/icon-192.png',
  './assets/images/icons/icon-512.png',
  './assets/images/icons/icon-maskable-192.png',
  './assets/images/icons/icon-maskable-512.png',
  './assets/images/icons/apach-pc-store.svg',
];

self.addEventListener('install', (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(SHELL_CACHE);
    // Individually, so one missing file cannot fail the whole install.
    await Promise.all(PRECACHE.map((url) =>
      cache.add(new Request(url, { cache: 'reload' })).catch(() => {})));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(
      keys.filter((k) => k.startsWith('asf-shell-') && k !== SHELL_CACHE)
          .map((k) => caches.delete(k)));
    await self.clients.claim();
  })());
});

function hasCacheableExtension(pathname) {
  const dot = pathname.lastIndexOf('.');
  if (dot === -1) return false;
  return CACHEABLE_EXT.has(pathname.slice(dot).toLowerCase());
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;   // never touch cross-origin

  // The SSE stream must reach the page untouched.
  if (url.pathname.endsWith('/realtime')) return;
  if ((req.headers.get('accept') || '').includes('text/event-stream')) return;

  // Navigations: network only, with a static offline page when there is no
  // network. See the header comment for why the response is never cached.
  if (req.mode === 'navigate') {
    event.respondWith((async () => {
      try {
        return await fetch(req);
      } catch (err) {
        const cache = await caches.open(SHELL_CACHE);
        const fallback = await cache.match(OFFLINE_URL);
        return fallback || new Response(
          '<!doctype html><meta charset="utf-8"><title>Offline</title>' +
          '<p>You are offline and this page was not saved.</p>',
          { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } });
      }
    })());
    return;
  }

  // Only the public shell is cacheable.
  if (!hasCacheableExtension(url.pathname)) return;

  event.respondWith((async () => {
    const cache = await caches.open(SHELL_CACHE);
    const hit = await cache.match(req);

    const fromNetwork = fetch(req).then((res) => {
      // Opaque and error responses are not worth storing.
      if (res && res.status === 200 && res.type === 'basic') {
        cache.put(req, res.clone()).catch(() => {});
      }
      return res;
    }).catch(() => null);

    if (hit) {
      // Stale-while-revalidate: instant paint, refreshed for next time.
      fromNetwork;
      return hit;
    }

    const res = await fromNetwork;
    if (res) return res;
    return new Response('', { status: 504, statusText: 'Offline' });
  })());
});
