/* Hover-to-open for every dropdown in the app.
 *
 * Bootstrap dropdowns are click-driven out of the box. This makes the pointer
 * path hover-driven instead: rest the cursor on a trigger and the menu opens;
 * move off the trigger or the menu and it closes. A click still toggles, and so
 * does the keyboard -- see the note on the click listener below, which explains
 * why the click cannot be taken over.
 *
 * It applies to the admin sub-nav (Catalog / Sales / Customers / Insights) and to
 * the profile menu in the site topbar, which is the one dropdown customers get
 * and admins get too. Those are opted in with the HOVERABLE class rather than
 * being found by a blanket selector, so a dropdown that genuinely wants a click
 * (anything inside a collapsed navbar, or a control where hover would be wrong)
 * is left alone by adding nothing.
 *
 * The two delays are the whole design. A dropdown bar wants different things
 * from a delay on the way in and on the way out:
 *
 *   OPEN_DELAY  stops a menu from flashing open every time the cursor sweeps
 *               across the bar on its way somewhere else. Without it, moving
 *               from Customers to the page content drags four menus open on the
 *               way past, and the last one to close reads as a glitch.
 *   CLOSE_DELAY is what makes the menu usable at all. A menu hangs below its
 *               trigger, so the cursor has to leave the trigger and travel
 *               diagonally down into it; closing on the trigger's own mouseleave
 *               would shut the menu every time the pointer moved toward it.
 *
 * Both are short enough that neither is perceptible as a wait -- they read as
 * "instant", which is the point. Tune them here and nowhere else.
 */
(function () {
  'use strict';

  var OPEN_DELAY = 120;
  var CLOSE_DELAY = 200;

  /* The opt-in class, on the .dropdown wrapper. Kept as a constant because it is
   * also what the stylesheet keys the fade off (.dropdown-hover-menu on the menu
   * itself), and the two have to move together. */
  var HOVERABLE = 'dropdown-hover';

  function attach(dropdown) {
    var toggle = dropdown.querySelector('[data-bs-toggle="dropdown"]');
    if (!toggle) return;

    var openTimer = null;
    var closeTimer = null;
    /* Set when a click, rather than the hover, decided the menu's state. Held
     * until the pointer has genuinely left, so that a click-closed menu does
     * not spring open again under a cursor that never moved, and so a click
     * during the close delay still wins. Cleared by the close timer finishing
     * and by any click outside. */
    var pinned = false;

    function instance() {
      return bootstrap.Dropdown.getOrCreateInstance(toggle);
    }

    /* Read from aria-expanded rather than Bootstrap's `_isShown()`: the
     * underscore means private, and show()/hide() maintain the attribute
     * whether they were called by the data-api, by us, or by Escape. */
    function isOpen() {
      return toggle.getAttribute('aria-expanded') === 'true';
    }

    function clearTimers() {
      if (openTimer) { clearTimeout(openTimer); openTimer = null; }
      if (closeTimer) { clearTimeout(closeTimer); closeTimer = null; }
    }

    dropdown.addEventListener('mouseenter', function () {
      clearTimers();
      if (pinned) return;
      openTimer = setTimeout(function () {
        openTimer = null;
        instance().show();
      }, OPEN_DELAY);
    });

    // The menu is a child of .dropdown, so leaving either one counts as leaving
    // -- and the delay is what covers the gap between them.
    dropdown.addEventListener('mouseleave', function () {
      clearTimers();
      closeTimer = setTimeout(function () {
        closeTimer = null;
        pinned = false;
        instance().hide();
      }, CLOSE_DELAY);
    });

    /* A click still toggles the menu, and this listener deliberately does not
     * try to change that. The obvious approach -- intercept the click and call
     * show()/hide() yourself -- cannot work here, and it is worth recording why
     * so nobody tries it again. Bootstrap registers its data-api with
     *
     *   element.addEventListener(event, handler, delegationSelector)
     *
     * and that third argument is the *selector*, which is truthy, so the
     * listener is bound in the CAPTURE phase on `document`. Capture on document
     * runs before the event ever reaches the target, so by the time a listener
     * on this trigger can see the click, Bootstrap has already toggled. No
     * stopPropagation, preventDefault or ordering trick available from inside
     * the page can run before it.
     *
     * So the click is read rather than replaced. By the time this runs the menu
     * is already in its new state, and the only thing to decide is whether the
     * hover path is allowed to have an opinion:
     *
     *   click closed it  -> pin. The pointer is still resting on the trigger, so
     *                       without a pin the menu would be reopened by the very
     *                       next mouseenter and the click would look like it did
     *                       nothing. It stays closed until the pointer leaves.
     *   click opened it  -> do not pin, so leaving the trigger closes it again,
     *                       exactly as if the hover had opened it.
     *
     * Keyboard clicks are skipped entirely: they carry no pointer, so there is
     * no hover state to protect, and `detail` is what tells the two apart (0 for
     * a synthesised click, >= 1 for a real one). */
    toggle.addEventListener('click', function (event) {
      if (event.detail === 0) return;
      clearTimers();
      pinned = !isOpen();
    });

    /* A click somewhere else dismisses the menu by Bootstrap's own hand, and
     * it knows nothing about `pinned`. A menu pinned shut by a click would
     * otherwise stay pinned, so the pointer could rest on the trigger for the
     * rest of the page's life and hover would never work again. */
    document.addEventListener('click', function (event) {
      if (!dropdown.contains(event.target)) pinned = false;
    });
  }

  function init() {
    /* Only where there is a real pointer to hover with. On a touch screen
     * mouseenter fires on tap and the synthetic hover is never "left", so a
     * dismissed menu would reopen under a finger that has already moved on.
     * Restricting this to (hover: hover) and (pointer: fine) leaves touch users
     * on plain click-to-open, which is what they need. */
    if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) return;

    document.querySelectorAll('.' + HOVERABLE).forEach(attach);
  }

  /* Loaded with `defer`, so this normally runs at readyState "interactive".
   * Kept for a future non-deferred include, matching the other app scripts. */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
