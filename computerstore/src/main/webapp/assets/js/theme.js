/* Light/dark theme toggle.
   The scheme lives in localStorage ("computerstore.theme"). The FOUC-safe
   inline boot script in header.jspf applies it before first paint; theme.js
   binds the toggle button, syncs its icon/aria-label, and follows the OS
   preference until the user picks an explicit scheme. Applied BEFORE the
   deferred body/DOM scripts so there is no flash of the wrong theme. */
(function () {
    'use strict';

    var KEY = 'computerstore.theme';
    var root = document.documentElement;
    var mq = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

    function current() {
        return root.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light';
    }
    function iconFor(theme) { return theme === 'dark' ? 'bi-sun' : 'bi-moon-stars'; }
    function labelFor(theme) { return theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'; }
    function apply(theme, save) {
        root.setAttribute('data-bs-theme', theme);
        root.style.colorScheme = theme;
        if (save) {
            try { localStorage.setItem(KEY, theme); } catch (e) {}
        }
        document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
            var icon = btn.querySelector('i');
            if (icon) { icon.className = 'bi ' + iconFor(theme); }
            btn.setAttribute('aria-label', labelFor(theme));
            btn.setAttribute('title', labelFor(theme));
        });
    }
    function toggle() {
        apply(current() === 'dark' ? 'light' : 'dark', true);
    }

    /* Wire the toggle button(s). */
    document.querySelectorAll('[data-theme-toggle]').forEach(function (btn) {
        btn.addEventListener('click', toggle);
    });

    /* Sync icons to whatever the boot script already applied. */
    apply(current(), false);

    /* Only respond to OS changes while the user has not made an explicit
       choice (no stored value). */
    if (mq) {
        mq.addEventListener('change', function (e) {
            var stored = null;
            try { stored = localStorage.getItem(KEY); } catch (err) {}
            if (!stored) { apply(e.matches ? 'dark' : 'light', false); }
        });
    }

    /* Export a tiny API for other scripts. */
    window.Theme = {
        current: current,
        toggle: toggle,
        apply: function (t) { apply(t, true); }
    };
})();
