/* Admin reports live updates.
 *
 * Works together with assets/js/realtime.js: when an order or stock event
 * arrives on /realtime, realtime.js calls window.ReportsLive.refresh() and
 * this script silently re-fetches the CURRENT period's report data from
 * /admin/reports?range=X&json=1 (always computed fresh server-side) and
 * patches the KPI cards, the order funnel, the stock-valuation table, the
 * top-sellers table and the revenue trend chart in place — no page reload.
 *
 * A 60s backstop timer re-polls for changes that never emit an event (for
 * example a change that happened before this tab connected to the stream).
 */
(function () {
    'use strict';

    var CTX = window.CTXPATH || '';
    var root = document.querySelector('[data-reports]');
    if (!root) {
        return;
    }
    var RANGE = root.getAttribute('data-reports-range') || '7d';
    var POLL_MS = 60000;
    var pendingDebounce = null;
    var polling = false;
    var trendChart = null;

    function money(v) {
        return '$' + Number(v || 0).toLocaleString('en-US', {
            minimumFractionDigits: 2, maximumFractionDigits: 2
        });
    }

    function setText(selector, value) {
        var el = document.querySelector(selector);
        if (el) {
            el.textContent = value;
        }
    }

    function patchSummary(s) {
        setText('[data-live-report="totalRevenue"]', money(s.totalRevenue));
        setText('[data-live-report="itemsSold"]', s.itemsSold);
        setText('[data-live-report="totalOrders"]', s.totalOrders);
        setText('[data-live-report="avgOrderValue"]', money(s.avgOrderValue));
        ['pending', 'processing', 'shipped', 'completed', 'cancelled', 'refunded'].forEach(function (key) {
            setText('[data-live-funnel="' + key + '"]', s[key]);
        });
    }

    function patchStockValues(v) {
        setText('[data-live-stock="totalStockValue"]', money(v.totalStockValue));
        setText('[data-live-stock="lowStockValue"]', money(v.lowStockValue));
    }

    function patchTopSellers(sellers) {
        var tbody = document.querySelector('[data-top-sellers]');
        if (!tbody) {
            return;
        }
        tbody.innerHTML = '';
        if (!sellers || sellers.length === 0) {
            var tr = document.createElement('tr');
            var empty = document.createElement('td');
            empty.colSpan = 4;
            empty.className = 'text-center text-muted py-4';
            empty.textContent = 'No sales in this period yet.';
            tr.appendChild(empty);
            tbody.appendChild(tr);
            return;
        }
        sellers.forEach(function (seller, i) {
            var tr = document.createElement('tr');

            var rank = document.createElement('td');
            rank.className = 'text-muted';
            rank.textContent = String(i + 1);
            tr.appendChild(rank);

            var name = document.createElement('td');
            name.className = 'small';
            name.textContent = seller.name;
            tr.appendChild(name);

            var qty = document.createElement('td');
            qty.className = 'text-center fw-semibold';
            qty.textContent = seller.quantity;
            tr.appendChild(qty);

            var revenue = document.createElement('td');
            revenue.className = 'text-end money';
            revenue.textContent = money(seller.revenue);
            tr.appendChild(revenue);

            tbody.appendChild(tr);
        });
    }

    function renderTrend(series) {
        var canvas = document.getElementById('revenueTrendChart');
        if (!canvas) {
            return;
        }
        var empty = document.getElementById('revenueTrendEmpty');
        var points = series || [];
        var hasData = points.some(function (p) { return Number(p.value) > 0; });
        canvas.classList.toggle('d-none', !hasData);
        if (empty) {
            empty.classList.toggle('d-none', hasData);
        }
        if (!hasData) {
            return;
        }
        if (trendChart) {
            trendChart.data.labels = points.map(function (p) { return p.label; });
            trendChart.data.datasets[0].data = points.map(function (p) { return Number(p.value); });
            trendChart.update();
        } else {
            trendChart = new Chart(canvas, {
                type: 'line',
                data: {
                    labels: points.map(function (p) { return p.label; }),
                    datasets: [{
                        label: 'Revenue',
                        data: points.map(function (p) { return Number(p.value); }),
                        borderColor: '#2563eb',
                        backgroundColor: 'rgba(37,99,235,.12)',
                        fill: true,
                        tension: 0.35,
                        pointRadius: 3,
                        pointBackgroundColor: '#2563eb',
                        borderWidth: 2
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            callbacks: { label: function (c) { return money(c.parsed.y); } }
                        }
                    },
                    scales: {
                        x: { grid: { display: false } },
                        y: {
                            beginAtZero: true,
                            ticks: { callback: function (value) { return money(value); } },
                            grid: { color: 'rgba(148,163,184,.18)' }
                        }
                    }
                }
            });
        }
    }

    function patchAll(data) {
        patchSummary(data.summary);
        patchStockValues(data.stockValues);
        patchTopSellers(data.topSellers);
        renderTrend(data.series);
    }

    function refresh() {
        if (polling) {
            return;
        }
        polling = true;
        fetch(CTX + '/admin/reports?range=' + encodeURIComponent(RANGE) + '&json=1', {
            headers: { 'Accept': 'application/json' }
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.json();
            })
            .then(patchAll)
            .catch(function () {
                // Leave current values; the next event or the backstop retries.
            })
            .finally(function () {
                polling = false;
            });
    }

    /* SSE bursts (one order emits a stock event per item) are folded into a
       single fetch by trailing-edge debouncing. */
    function refreshSoon() {
        if (pendingDebounce) {
            clearTimeout(pendingDebounce);
        }
        pendingDebounce = setTimeout(function () {
            pendingDebounce = null;
            refresh();
        }, 350);
    }

    // Initial render from the server-provided series; chart creation lives
    // here so a later refresh can create the chart when data first appears.
    renderTrend(window.__reportSeries || []);

    window.ReportsLive = { refresh: refreshSoon };

    // Backstop poll for changes no SSE event covers.
    window.setInterval(refresh, POLL_MS);
})();