/*
 * Service worker registration.
 *
 * Kept in its own file rather than inline in header.jspf so it is cached with
 * the rest of the shell and so the layout does not grow another script block.
 *
 * Registration is deferred until after 'load' so it never competes with the
 * page's own render-blocking CSS on a phone, and it is skipped entirely where
 * it cannot work: a service worker needs a secure context, so plain-http
 * origins other than localhost never get one.
 *
 * There is no "update ready" notification, because sw.js calls skipWaiting() and
 * clients.claim(): a new worker takes over as soon as it installs, so there is no
 * window in which the user is holding a stale page and could be told to reload.
 */
(function () {
  'use strict';

  if (!('serviceWorker' in navigator)) return;
  if (!window.isSecureContext) return;

  window.addEventListener('load', function () {
    // sw.js is served from the app root so its scope covers the context path.
    // window.CTXPATH is set by header.jspf.
    var base = window.CTXPATH || '';
    var url = base + '/sw.js';

    navigator.serviceWorker.register(url, { scope: base + '/' }).catch(function (err) {
      // Registration failing must never break the page: the app works fine
      // without it, just not offline.
      console.warn('[pwa] service worker registration failed:', err && err.message);
    });
  });
})();
