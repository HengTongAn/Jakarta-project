/* Inline admin help (dashboard / reports / performance).
   Every card or panel marked with data-help gets a small "?" button in its
   header. Clicking it — or double-clicking a NON-link card — opens a short,
   plain-language popup with two sections: "what it is" and "why it matters".
   The text lives in data-help / data-help-title / data-help-why attributes,
   so adding help to any future panel is pure markup — no JS changes.

   Real-world interaction: the "?" is the discoverable path (standard for
   help), and double-click is the fast path for admins already used to the
   page. Link cards (e.g. the dashboard KPI cards that navigate on click)
   intentionally open help only via their "?" — double-click would fight the
   navigation. */
(function () {
    'use strict';

    if (!document.querySelector('[data-help]')) {
        return; // no helpable panels on this page
    }

    var ui = null;   // lazily built popup nodes
    var toggleFor = null; // the opening toggle (for focus restore)

    function buildPopup() {
        var backdrop = document.createElement('div');
        backdrop.className = 'admin-help-backdrop';

        var panel = document.createElement('div');
        panel.className = 'admin-help-popup';
        panel.setAttribute('role', 'dialog');
        panel.setAttribute('aria-modal', 'true');
        panel.setAttribute('aria-labelledby', 'adminHelpTitle');

        var head = document.createElement('div');
        head.className = 'admin-help-head';
        var title = document.createElement('h3');
        title.id = 'adminHelpTitle';
        title.className = 'admin-help-title';
        var closeBtn = document.createElement('button');
        closeBtn.type = 'button';
        closeBtn.className = 'btn-close admin-help-close';
        closeBtn.setAttribute('aria-label', 'Close help');
        head.appendChild(title);
        head.appendChild(closeBtn);

        var body = document.createElement('div');
        body.className = 'admin-help-body';

        function labeled(labelText, textClass) {
            var wrap = document.createElement('div');
            wrap.className = textClass || '';
            var label = document.createElement('p');
            label.className = 'admin-help-label';
            label.textContent = labelText;
            var text = document.createElement('p');
            text.textContent = '';
            wrap.appendChild(label);
            wrap.appendChild(text);
            body.appendChild(wrap);
            return text;
        }

        var what = labeled('What it is');
        var why = labeled('Why it matters', 'admin-help-why');

        var foot = document.createElement('div');
        foot.className = 'admin-help-foot';
        var gotIt = document.createElement('button');
        gotIt.type = 'button';
        gotIt.className = 'btn btn-primary btn-sm admin-help-gotit';
        gotIt.textContent = 'Got it';
        foot.appendChild(gotIt);

        panel.appendChild(head);
        panel.appendChild(body);
        panel.appendChild(foot);
        backdrop.appendChild(panel);
        document.body.appendChild(backdrop);

        return { backdrop: backdrop, panel: panel, title: title, what: what, why: why, closeBtn: closeBtn, gotIt: gotIt };
    }

    function openFor(source, toggle) {
        if (!source) {
            return;
        }
        if (!ui) {
            ui = buildPopup();
        }
        ui.title.textContent = source.getAttribute('data-help-title') || 'About this';
        ui.what.textContent = source.getAttribute('data-help') || '';
        ui.why.textContent = source.getAttribute('data-help-why')
            || 'Keep an eye on this card as you run the store — it exists to make that easier.';
        toggleFor = toggle || null;
        ui.backdrop.classList.add('is-open');
        window.setTimeout(function () {
            ui.gotIt.focus();
        }, 10);
    }

    function close() {
        if (!ui) {
            return;
        }
        ui.backdrop.classList.remove('is-open');
        if (toggleFor) {
            try {
                toggleFor.focus();
            } catch (e) { /* element may no longer exist */ }
        }
        toggleFor = null;
    }

    function isOpen() {
        return !!ui && ui.backdrop.classList.contains('is-open');
    }

    function helpSource(toggle) {
        return toggle.closest('[data-help]') || toggle;
    }

    document.addEventListener('click', function (e) {
        var toggle = e.target.closest('[data-help-toggle]');
        if (toggle) {
            e.preventDefault();
            e.stopPropagation();
            openFor(helpSource(toggle), toggle);
            return;
        }
        if (isOpen() && (e.target === ui.backdrop
                || e.target.closest('.admin-help-close')
                || e.target.closest('.admin-help-gotit'))) {
            close();
        }
    });

    /* Double-click on a non-link card is the fast path to the same popup.
       Interactive descendants (links, buttons, inputs, the "?" itself) are
       left alone so their normal behaviour is never disturbed. */
    document.addEventListener('dblclick', function (e) {
        var card = e.target.closest('[data-help]');
        if (!card || card.closest('a[href]')) {
            return;
        }
        if (e.target.closest('a, button, input, select, textarea, [data-help-toggle]')) {
            return;
        }
        e.preventDefault();
        openFor(card, null);
    });

    /* Keyboard: Esc closes (always); Enter/Space activates a role=button
       toggle placed inside a link card (native <button> handles itself). */
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            close();
            return;
        }
        var toggle = e.target.closest('[data-help-toggle]');
        if (toggle && !e.target.closest('button')
                && (e.key === 'Enter' || e.key === ' ')) {
            e.preventDefault();
            openFor(helpSource(toggle), toggle);
        }
    });
})();