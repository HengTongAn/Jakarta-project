/* Live mailbox updates: polls /mail/json (every POLL_MS) and
   1) refreshes the unread-count badges in the navbar, and
   2) re-renders the inbox list when new mail arrives (no page refresh needed). */
(function () {
    'use strict';

    var POLL_MS = 8000;

    var badges = document.querySelectorAll('.mail-live-count');
    var inboxEl = document.querySelector('[data-mail-inbox]');
    if (badges.length === 0 && !inboxEl) {
        return;
    }

    var viewBase = inboxEl ? inboxEl.getAttribute('data-mail-inbox') : null;
    var filter = inboxEl ? (inboxEl.getAttribute('data-mail-filter') || 'all') : 'all';
    var knownNewest = null;

    var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
        'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    function fmt(ms) {
        var d = new Date(ms);
        var mm = String(d.getMinutes()).padStart(2, '0');
        var hh = String(d.getHours()).padStart(2, '0');
        var day = String(d.getDate()).padStart(2, '0');
        return day + ' ' + MONTHS[d.getMonth()] + ', ' + hh + ':' + mm;
    }

    function setBadges(n) {
        badges.forEach(function (b) {
            if (b.textContent !== String(n)) {
                b.textContent = n;
            }
            b.classList.toggle('d-none', n === 0);
        });
        document.querySelectorAll('.mail-nav-count').forEach(function (b) {
            if (b.textContent !== String(n)) {
                b.textContent = n;
            }
            b.classList.toggle('d-none', n === 0);
        });
    }

    function row(m) {
        var row = document.createElement('a');
        row.className = 'mail-row' + (m.read ? '' : ' unread');
        row.href = viewBase + m.id;

        var av = document.createElement('span');
        av.className = 'mail-avatar-sm';
        av.textContent = (m.senderName || '?').charAt(0);

        var mid = document.createElement('div');
        mid.className = 'mail-mid';

        var top = document.createElement('div');
        top.className = 'mail-top';
        var name = document.createElement('span');
        name.className = 'text-truncate';
        name.textContent = m.senderName || '';
        var time = document.createElement('span');
        time.className = 'mail-time';
        time.textContent = fmt(m.time);
        top.appendChild(name);
        top.appendChild(time);

        var line = document.createElement('div');
        line.className = 'text-truncate';
        var subject = document.createElement('span');
        subject.className = 'fw-semibold';
        subject.textContent = m.subject || '';
        var snippet = document.createElement('span');
        snippet.className = 'mail-snippet';
        snippet.textContent = ' - ' + (m.snippet || '');
        line.appendChild(subject);
        line.appendChild(snippet);

        mid.appendChild(top);
        mid.appendChild(line);

        row.appendChild(av);
        row.appendChild(mid);

        if (!m.read) {
            var dot = document.createElement('span');
            dot.className = 'mail-dot';
            row.appendChild(dot);
        }
        return row;
    }

    function render(messages) {
        inboxEl.innerHTML = '';
        if (!messages.length) {
            var empty = document.createElement('div');
            empty.className = 'mail-empty';
            empty.textContent = 'Your inbox is empty.';
            inboxEl.appendChild(empty);
            return;
        }
        messages.forEach(function (m) {
            inboxEl.appendChild(row(m));
        });
    }

    function refresh() {
        fetch(window.CTXPATH + '/mail/json?filter=' + encodeURIComponent(filter))
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('mail json unavailable');
                }
                return res.json();
            })
            .then(function (data) {
                setBadges(data.unread || 0);
                if (!inboxEl || !data.messages || !data.messages.length) {
                    return;
                }
                var newest = data.messages[0].id;
                if (knownNewest === null) {
                    knownNewest = newest;
                    return;
                }
                if (newest !== knownNewest) {
                    knownNewest = newest;
                    render(data.messages);
                }
            })
            .catch(function () {
                /* session/polling unavailable — stop silently */
            });
    }

    refresh();
    setInterval(refresh, POLL_MS);
})();