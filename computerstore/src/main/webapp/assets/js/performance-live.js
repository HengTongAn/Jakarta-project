/* Admin performance monitor: live in-place refresh.
   Replaces the old 30s full-page reload: every POLL_MS this script silently
   re-fetches /admin/performance?json=1 and patches the KPI cards, the cache /
   pool / JVM panels, the query table and the recommendations without touching
   the page chrome (no scroll reset, no flicker).

   Unlike dashboard-live.js / reports-live.js this deliberately does NOT hook
   the SSE stream: the numbers here are aggregate read metrics (cache hit
   rates, pool state, query timings) that move with traffic rather than with
   one specific domain event, so a short silent poll is the honest mechanism.
   realtime.js already avoids hard-reloading this page (it only reloads the
   dashboard/reports markers), so nothing interrupts the cadence.
   */
(function () {
    'use strict';

    var root = document.querySelector('[data-perf]');
    if (!root) {
        return; // not the performance page
    }

    var CTX = window.CTXPATH || '';
    var POLL_MS = 15000;
    var UPDATED = document.querySelector('[data-live-updated]');

    function numberOrDash(v) {
        if (v === null || v === undefined || v === '') {
            return '\u2014';
        }
        var n = Number(v);
        return isFinite(n) ? n : '\u2014';
    }

    /* Group thousands like the server does (<fmt:formatNumber>). */
    function grouped(v) {
        var n = numberOrDash(v);
        return n === '\u2014' ? n : String(n.toLocaleString());
    }

    function rate(v) {
        var n = Number(v);
        return isFinite(n) ? n.toFixed(1) + '%' : '\u2014';
    }

    function setText(selector, text) {
        var el = document.querySelector(selector);
        if (el) {
            el.textContent = text;
        }
    }

    function td(text, className) {
        var cell = document.createElement('td');
        if (className) {
            cell.className = className;
        }
        cell.textContent = text;
        return cell;
    }

    function emptyRow(message, colSpan) {
        var tr = document.createElement('tr');
        var cell = document.createElement('td');
        cell.colSpan = colSpan;
        cell.className = 'text-muted small';
        cell.textContent = message;
        tr.appendChild(cell);
        return tr;
    }

    function patchNumbers(pool, jvm) {
        if (pool) {
            setText('[data-live-perf="poolActive"]', numberOrDash(pool.active));
            setText('[data-live-perf="poolMax"]', numberOrDash(pool.max));
            setText('[data-live-perf="poolWaiting"]', numberOrDash(pool.waiting));
            setText('[data-live-pool="total"]', numberOrDash(pool.total));
            setText('[data-live-pool="active"]', numberOrDash(pool.active));
            setText('[data-live-pool="idle"]', numberOrDash(pool.idle));
            setText('[data-live-pool="waiting"]', numberOrDash(pool.waiting));
            setText('[data-live-pool="max"]', numberOrDash(pool.max));
            setText('[data-live-pool="min"]', numberOrDash(pool.min));
        }
        if (jvm) {
            setText('[data-live-perf="heapUsed"]', numberOrDash(jvm.heapUsedMb));
            setText('[data-live-perf="heapMax"]', numberOrDash(jvm.heapMaxMb));
            setText('[data-live-perf="uptimeH"]', (Number(jvm.uptimeSeconds) / 3600).toFixed(1));
            setText('[data-live-jvm="heapUsed"]', grouped(jvm.heapUsedMb));
            setText('[data-live-jvm="heapCommitted"]', grouped(jvm.heapCommittedMb));
            setText('[data-live-jvm="heapMax"]', grouped(jvm.heapMaxMb));
            setText('[data-live-jvm="free"]', grouped(jvm.freeMb));
            setText('[data-live-jvm="processors"]', numberOrDash(jvm.processors));
        }
    }

    function patchState(cacheEnabled, compressionEnabled) {
        var badge = document.querySelector('[data-live-cache-badge]');
        if (badge) {
            badge.textContent = cacheEnabled ? 'enabled' : 'disabled';
            badge.className = 'badge align-middle ' + (cacheEnabled ? 'bg-success' : 'bg-secondary');
        }
        setText('[data-live-jvm="compression"]', compressionEnabled ? 'enabled' : 'disabled');
    }

    function patchCacheTable(stats) {
        var tbody = document.querySelector('[data-live-cache-table]');
        if (!tbody) {
            return;
        }
        tbody.textContent = '';
        var names = Object.keys(stats || {});
        if (!names.length) {
            tbody.appendChild(emptyRow('No cache data available yet.', 5));
            return;
        }
        names.forEach(function (name) {
            var s = stats[name] || {};
            var tr = document.createElement('tr');
            tr.appendChild(td(name));
            tr.appendChild(td(grouped(s.size), 'text-end'));
            tr.appendChild(td(grouped(s.hits), 'text-end'));
            tr.appendChild(td(grouped(s.misses), 'text-end'));
            tr.appendChild(td(rate(s.hitRate), 'text-end'));
            tbody.appendChild(tr);
        });
    }

    function patchQueries(list) {
        var tbody = document.querySelector('[data-live-queries]');
        if (!tbody) {
            return;
        }
        tbody.textContent = '';
        if (!list || !list.length) {
            tbody.appendChild(emptyRow('No queries recorded yet. Load the storefront '
                + '(/products) and this table will show its real query profile.', 5));
            return;
        }
        list.forEach(function (q) {
            var slow = Number(q.slowCount) || 0;
            var tr = document.createElement('tr');
            tr.appendChild(td(grouped(q.count), 'text-end'));
            tr.appendChild(td((Number(q.avgTime) || 0).toFixed(1), 'text-end'));
            tr.appendChild(td(grouped(q.maxTime), 'text-end'));
            tr.appendChild(td(slow, 'text-end' + (slow > 0 ? ' text-danger fw-bold' : '')));
            var sig = document.createElement('td');
            sig.className = 'small text-break';
            var code = document.createElement('code');
            code.className = 'text-muted';
            code.textContent = q.signature;
            sig.appendChild(code);
            tr.appendChild(sig);
            tbody.appendChild(tr);
        });
    }

    function patchRecommendations(list) {
        var ul = document.querySelector('[data-live-recommendations]');
        if (!ul) {
            return;
        }
        ul.textContent = '';
        (list || []).forEach(function (tip) {
            var li = document.createElement('li');
            li.className = 'list-group-item';
            var icon = document.createElement('i');
            icon.className = 'bi bi-arrow-right-circle me-2 text-primary';
            icon.setAttribute('aria-hidden', 'true');
            li.appendChild(icon);
            li.appendChild(document.createTextNode(tip));
            ul.appendChild(li);
        });
    }

    function refresh() {
        fetch(CTX + '/admin/performance?json=1', {
            headers: { 'Accept': 'application/json' },
            cache: 'no-store'
        }).then(function (r) {
            return r.ok ? r.json() : null;
        }).then(function (data) {
            if (!data) {
                return;
            }
            patchNumbers(data.poolStats, data.jvm);
            patchState(data.cacheEnabled, data.compressionEnabled);
            patchCacheTable(data.cacheStats);
            patchQueries(data.queryStats);
            patchRecommendations(data.recommendations);
            if (UPDATED) {
                UPDATED.textContent = new Date().toLocaleTimeString();
            }
        }).catch(function () {
            // Network hiccup - keep the last good numbers on screen.
        });
    }

    // Quick first sync (the pool settles within the first seconds after
    // startup), then a steady cadence.
    window.setTimeout(refresh, 1500);
    window.setInterval(refresh, POLL_MS);
})();