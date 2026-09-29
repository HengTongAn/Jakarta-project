/* Reveal the current page's link in the admin nav.
 *
 * The nav is 13 items in a `flex-row flex-nowrap` row inside an `overflow-x:
 * auto` container, and it overflows at EVERY viewport width -- 1453px of links
 * in an 823px port on a 1280px laptop. The `active` class is applied correctly
 * (each of the 13 pages marks exactly one link), but on 6 of those 13 pages the
 * active link begins past the right edge of the port, so the highlight was
 * never actually on screen. Dashboard is item 1, so it is the one link always
 * visible, which made every page look like Dashboard was the selected one.
 *
 * Two things fix it, and both are needed:
 *   1. this script, to scroll the active link into view
 *   2. a visible (if thin) scrollbar in layout.css, so it is discoverable that
 *      the row scrolls at all
 *
 * Sets `scrollLeft` on the container rather than calling `scrollIntoView()`:
 * that walks up the ancestor chain and can scroll the document vertically too,
 * which on a short page jumps the whole layout. This container is
 * horizontal-only, so a direct scrollLeft is both sufficient and inert.
 */
(function () {
  'use strict';

  /* Keep a little slack at the right edge. Scrolling the minimum distance
   * parks the active link flush against the boundary, which leaves it hard
   * against the 6px scrollbar and -- because the boundary is a sub-pixel
   * edge -- reports as clipped even though it is fully on screen. */
  var EDGE_SLACK = 12;

  function revealActive(box) {
    var active = box.querySelector('.nav-link.active');
    if (!active) return;

    var boxRect = box.getBoundingClientRect();
    var activeRect = active.getBoundingClientRect();

    // The active link's position in the container's scroll coordinates.
    var start = activeRect.left - boxRect.left + box.scrollLeft;
    var end = start + activeRect.width;

    // Already fully inside the port -- leave it alone rather than nudging it,
    // so a nav that fits never moves at all.
    if (start >= box.scrollLeft && end <= box.scrollLeft + box.clientWidth) return;

    // scrollLeft clamps on its own, so the last item needs no special case.
    box.scrollLeft = start < box.scrollLeft
      ? start - EDGE_SLACK
      : end - box.clientWidth + EDGE_SLACK;
  }

  function init() {
    var box = document.querySelector('.admin-nav-scroll');
    if (!box) return;

    revealActive(box);

    // A resize changes how much of the row is hidden, so a position that was
    // right at one width is wrong at another. Only react to a real width
    // change: a mobile URL bar collapsing also fires `resize`.
    var lastWidth = window.innerWidth;
    window.addEventListener('resize', function () {
      if (window.innerWidth === lastWidth) return;
      lastWidth = window.innerWidth;
      revealActive(box);
    });
  }

  // Loaded with `defer`, so this normally runs at readyState "interactive" and
  // the listener is not needed. Keep it for any future non-deferred include.
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
