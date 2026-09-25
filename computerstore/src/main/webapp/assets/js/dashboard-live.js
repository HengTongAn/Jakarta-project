/* Admin dashboard live updates.
 *
 * Works together with assets/js/realtime.js: when a stock, order or review
 * event arrives on /realtime, realtime.js calls window.DashboardLive.refresh()
 * and this script silently re-fetches the CURRENT dashboard stats from
 * /admin?json=1 (always computed fresh server-side) and patches the KPI
 * cards, the two recent-activity tables and both Chart.js charts in place —
 * no page reload.
 *
 * A 60s timer re-polls as a backstop for changes that never emit an event
 * (e.g. another admin moderating reviews just before this tab connected).
 */
(function () {
    'use strict';

    var CTX = window.CTXPATH || '';
    var POLL_MS = 60000;
    var timer = null;
    var pendingDebounce = null;
    var polling = false;

    var STATUS_BADGES = {
        PENDING: 'bg-warning text-dark', PROCESSING: 'bg-info',
        SHIPPED: 'bg-primary', COMPLETED: 'bg-success',
        CANCELLED: 'bg-secondary', REFUNDED: 'bg-danger'
    };

    var STATUS_COLORS = {
        PENDING: '#f59e0b', PROCESSING: '#0ea5e9',
        SHIPPED: '#2563eb', COMPLETED: '#16a34a'
    };

    function money(v) {
        return '$' + Number(v || 0).toLocaleString('en-US', {
            minimumFractionDigits: 2, maximumFractionDigits: 2
        });
    }

    function dateLabel(ms) {
        return new Date(ms).toLocaleString(undefined, {
            day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'
        });
    }

    /* Build a table cell from a text string or a DOM node. Using
       textContent (never innerHTML) keeps user-supplied names XSS-safe. */
    function cell(textOrNode, cls) {
        var td = document.createElement('td');
        if (cls) {
            td.className = cls;
        }
        if (typeof textOrNode === 'string') {
            td.textContent = textOrNode;
        } else if (textOrNode) {
            td.appendChild(textOrNode);
        }
        return td;
    }

    function badge(text, cls) {
        var b = document.createElement('span');
        b.className = 'badge ' + cls + ' badge-status';
        b.textContent = text;
        return b;
    }

    function emptyCell(text, colspan, cls) {
        var td = cell(text, cls);
        td.colSpan = colspan;
        return td;
    }

    function patchCounters(c) {
        var revenue = document.querySelector('[data-live-stat="revenue"]');
        if (revenue) {
            revenue.textContent = money(c.totalRevenue);
        }
        ['pendingOrders', 'lowStockCount', 'pendingReviews', 'totalProducts'].forEach(function (key) {
            var el = document.querySelector('[data-live-stat="' + key + '"]');
            if (el) {
                el.textContent = c[key];
            }
        });
    }

    function patchRecentOrders(orders) {
        var tbody = document.querySelector('[data-recent-orders]');
        if (!tbody) {
            return;
        }
        tbody.innerHTML = '';
        if (!orders || orders.length === 0) {
            var tr = document.createElement('tr');
            tr.appendChild(emptyCell('No orders placed yet.', 5, 'text-center text-muted py-4'));
            tbody.appendChild(tr);
            return;
        }
        orders.forEach(function (o) {
            var link = document.createElement('a');
            link.href = CTX + '/admin/orders?id=' + o.orderId;
            link.textContent = '#' + o.orderId;

            var statusCell = cell(o.status, 'text-center');
            statusCell.appendChild(badge(o.status, STATUS_BADGES[o.status] || 'bg-secondary'));

            var tr = document.createElement('tr');
            tr.appendChild(cell(link));
            tr.appendChild(cell(o.customerName));
            tr.appendChild(cell(dateLabel(o.orderDate)));
            tr.appendChild(cell(money(o.totalAmount), 'text-end money'));
            tr.appendChild(statusCell);
            tbody.appendChild(tr);
        });
    }

    function patchRecentLogs(logs) {
        var tbody = document.querySelector('[data-recent-logs]');
        if (!tbody) {
            return;
        }
        tbody.innerHTML = '';
        if (!logs || logs.length === 0) {
            var tr = document.createElement('tr');
            tr.appendChild(emptyCell('No inventory activity yet.', 3, 'text-center text-muted py-4'));
            tbody.appendChild(tr);
            return;
        }
        logs.forEach(function (log) {
            var actionClass = log.action === 'ORDER_CREATED' ? 'bg-info'
                : log.action === 'ORDER_CANCELLED' ? 'bg-secondary' : 'bg-success';
            var actionLabel = log.action === 'ORDER_CREATED' ? 'Order'
                : log.action === 'ORDER_CANCELLED' ? 'Cancelled' : 'Adjust';

            var actionCell = cell('', 'text-center');
            actionCell.appendChild(badge(actionLabel, actionClass));

            var tr = document.createElement('tr');
            tr.appendChild(cell(log.productName, 'small'));
            tr.appendChild(cell(log.oldQuantity + ' \u2192 ' + log.newQuantity, 'text-end small'));
            tr.appendChild(actionCell);
            tbody.appendChild(tr);
        });
    }

    function patchCharts(c, orders) {
        var charts = window.dashCharts;
        if (!charts) {
            return;
        }
        if (charts.sales) {
            // Mirror the initial render: cancelled/refunded orders are not
            // revenue, so they never appear here (the Revenue card's SQL
            // already excludes them).
            var visible = (orders || []).filter(function (o) {
                return o.status !== 'CANCELLED' && o.status !== 'REFUNDED';
            });
            var canvas = document.getElementById('salesChart');
            var empty = document.getElementById('salesEmpty');
            if (canvas) {
                canvas.classList.toggle('d-none', visible.length === 0);
            }
            if (empty) {
                empty.classList.toggle('d-none', visible.length > 0);
            }
            charts.sales.data.labels = visible.map(function (o) { return 'Order #' + o.orderId; });
            var dataset = charts.sales.data.datasets[0];
            dataset.data = visible.map(function (o) { return Number(o.totalAmount); });
            dataset.backgroundColor = visible.map(function (o) {
                return STATUS_COLORS[o.status] || 'rgba(37,99,235,.82)';
            });
            if (visible.length > 0) {
                charts.sales.update();
            }
        }
        if (charts.stock) {
            var inStock = (c.totalProducts - c.lowStockCount - c.outOfStockCount);
            charts.stock.data.datasets[0].data = [inStock, c.lowStockCount, c.outOfStockCount];
            charts.stock.update();
        }
    }

    function refresh() {
        if (!window.dashCharts) {
            return; // charts not created yet (initial page load)
        }
        if (polling) {
            return;
        }
        polling = true;
        fetch(CTX + '/admin?json=1', { headers: { 'Accept': 'application/json' } })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status);
                }
                return response.json();
            })
            .then(function (data) {
                patchCounters(data.counters);
                patchRecentOrders(data.recentOrders);
                patchRecentLogs(data.recentLogs);
                patchCharts(data.counters, data.recentOrders);
            })
            .catch(function () {
                // Leave current values; the next event or the backstop timer retries.
            })
            .finally(function () {
                polling = false;
            });
    }

    /* SSE bursts (e.g. one order emits several stock events) are folded into
       a single fetch by trailing-edge debouncing. */
    function refreshSoon() {
        if (pendingDebounce) {
            clearTimeout(pendingDebounce);
        }
        pendingDebounce = setTimeout(function () {
            pendingDebounce = null;
            refresh();
        }, 350);
    }

    window.DashboardLive = { refresh: refreshSoon };

    // Backstop poll for changes no SSE event covers.
    window.setInterval(refresh, POLL_MS);
})();