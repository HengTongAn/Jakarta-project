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

   The Experienced Page Time tables are patched too, for a different reason.
   Those figures come from sampled browser reports accumulated over the
   retention window, so they move by a sample or two per poll rather than by
   the second. Refreshing them is not about freshness: a report whose numbers
   silently froze while every other panel ticked would read as "nothing has
   been sampled", which is a claim the page cannot actually make. And if the
   query starts failing, the tables are replaced with the failure notice
   rather than left showing the last good figures as if they were current. */
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

    /* A null hit rate means "no requests recorded yet", not zero percent.
     * Number(null) is 0 and Number('') is 0, so the guard has to come before the
     * numeric coercion -- otherwise an untouched cache renders as 0.0% here
     * while the server-rendered first paint shows an em dash, and the same row
     * changes meaning once the 15s refresh lands. */
    function rate(v) {
        if (v === null || v === undefined || v === '') {
            return '\u2014';
        }
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

    /* ------------------------------------------------------------------
       Experienced Page Time.
       ------------------------------------------------------------------ */

    /* The three tables share a shape: key, sample count, client p50, client
     * p95, server p95, average weight. Only the first cell differs -- a browser
     * name in one, a route template in the other -- so they render through one
     * function rather than three near-identical ones. */
    function patchEptTable(selector, buckets, emptyMessage, renderKey) {
        var tbody = document.querySelector(selector);
        if (!tbody) {
            return;
        }
        tbody.textContent = '';
        if (!buckets || !buckets.length) {
            tbody.appendChild(emptyRow(emptyMessage, 6));
            return;
        }
        buckets.forEach(function (b) {
            var tr = document.createElement('tr');
            tr.appendChild(renderKey(b.key));
            tr.appendChild(td(grouped(b.clientCount), 'text-end'));

            /* clientCount, not sampleCount, is the column's real denominator. A
             * bucket can hold rows where the browser reported no timing at all,
             * and showing the wider count beside a percentile computed from the
             * narrower one would overstate the evidence behind that number. */
            tr.appendChild(td(ms(b.client && b.client.p50), 'text-end'));
            tr.appendChild(td(ms(b.client && b.client.p95), 'text-end'
                + ((Number(b.client && b.client.p95) || 0) > 2000 ? ' text-danger fw-bold' : '')));
            tr.appendChild(td(ms(b.server && b.server.p95), 'text-end text-muted'));
            tr.appendChild(td(kb(b.averageBytes), 'text-end text-muted'));
            tbody.appendChild(tr);
        });
    }

    function ms(v) {
        var n = numberOrDash(v);
        return n === '\u2014' ? n : n + ' ms';
    }

    function kb(v) {
        var n = Number(v);
        return (isFinite(n) ? (n / 1024).toFixed(1) : '\u2014') + ' KB';
    }

    function plainKey(text) {
        var cell = document.createElement('td');
        cell.textContent = text;
        return cell;
    }

    function routeKey(text) {
        var cell = document.createElement('td');
        var code = document.createElement('code');
        code.className = 'text-muted';
        code.textContent = text;
        cell.appendChild(code);
        return cell;
    }

    function percentileRow(label, suffix, p, emphasise) {
        var tr = document.createElement('tr');
        var name = document.createElement('td');
        if (emphasise) {
            var strong = document.createElement('strong');
            strong.textContent = label;
            name.appendChild(strong);
        } else {
            name.textContent = label;
        }
        var note = document.createElement('span');
        note.className = 'text-muted small';
        note.textContent = ' \u2014 ' + suffix;
        name.appendChild(note);
        tr.appendChild(name);
        tr.appendChild(td(ms(p.p50), 'text-end' + (emphasise ? ' fw-bold' : '')));
        tr.appendChild(td(ms(p.p75), 'text-end'));
        tr.appendChild(td(ms(p.p95), 'text-end'));
        tr.appendChild(td(avg(p.avg), 'text-end text-muted'));
        tr.appendChild(td(ms(p.max), 'text-end text-muted'));
        return tr;
    }

    function avg(v) {
        var n = Number(v);
        return (isFinite(n) ? n.toFixed(1) : '\u2014') + ' ms';
    }

    function patchEpt(report) {
        /* A null report means the query failed. Leaving the previous contents in
         * place would show stale figures as though they were current, and clearing
         * them to the ordinary "nothing sampled yet" copy would read as a site
         * nobody visits rather than an instrumentation that has stopped working.
         * Only one of those two is true, so every table says which. */
        if (!report) {
            ['[data-live-ept-overall]', '[data-live-ept-browser]', '[data-live-ept-pages]']
                .forEach(function (selector) {
                    var tbody = document.querySelector(selector);
                    if (tbody) {
                        tbody.textContent = '';
                        tbody.appendChild(emptyRow(
                            'Could not read page timing data \u2014 this is not an empty result.', 6));
                    }
                });
            var basis = document.querySelector('[data-live-ept-basis]');
            if (basis) {
                basis.textContent = 'The page timing query is failing, so the figures below are '
                    + 'stale. Reload once the database is reachable.';
            }
            return;
        }

        var tbody = document.querySelector('[data-live-ept-overall]');
        if (tbody) {
            tbody.textContent = '';
            if (!report.rowCount) {
                tbody.appendChild(emptyRow('Nothing sampled yet.', 6));
            } else {
                tbody.appendChild(percentileRow('Interactive', 'clickable',
                    report.interactive, true));
                tbody.appendChild(percentileRow('DOM ready', 'content ready', report.domReady, false));
                tbody.appendChild(percentileRow('Fully loaded', 'images, fonts', report.load, false));
                tbody.appendChild(percentileRow('First byte', 'includes connection setup',
                    report.ttfb, false));
                tbody.appendChild(percentileRow('Server think time', 'your app\'s share',
                    report.server, true));
                tbody.appendChild(weightRow(report.transfer));
            }
        }

        patchEptTable('[data-live-ept-browser]', report.byBrowser,
            'No browser samples yet.', plainKey);
        patchEptTable('[data-live-ept-pages]', report.byPageType,
            'No page samples yet.', routeKey);

        /* Both basis paragraphs are always in the DOM; exactly one is shown. A card can
         * go from unvisited to sampled while it is open, and "nothing sampled
         * yet" is the first wording that has to change when it does. */
        show('[data-live-ept-basis]', !!report.rowCount);
        show('[data-live-ept-empty]', !report.rowCount);
        setText('[data-live-ept-count]', grouped(report.rowCount));
        setText('[data-live-ept-days]', report.retentionDays);
        setText('[data-live-ept-rate]', report.sampleRate);
    }

    function weightRow(transfer) {
        var tr = document.createElement('tr');
        var label = document.createElement('td');
        label.textContent = 'Page weight';
        var note = document.createElement('span');
        note.className = 'text-muted small';
        note.textContent = ' \u2014 transferred';
        label.appendChild(note);
        tr.appendChild(label);

        var value = document.createElement('td');
        value.className = 'text-end';
        var avgBytes = Number(transfer && transfer.avg);
        var maxBytes = Number(transfer && transfer.max);
        value.textContent = (isFinite(avgBytes) ? (avgBytes / 1024).toFixed(1) : '\u2014')
            + ' KB average, '
            + (isFinite(maxBytes) ? Math.round(maxBytes / 1024) : '\u2014') + ' KB worst';
        tr.appendChild(value);

        var filler = document.createElement('td');
        filler.colSpan = 5;
        tr.appendChild(filler);
        return tr;
    }

    function show(selector, visible) {
        var el = document.querySelector(selector);
        if (el) {
            el.classList.toggle('d-none', !visible);
        }
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
            patchEpt(data.experience);
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