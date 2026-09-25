/* Real-time cross-user updates. Opens one Server-Sent Events stream to
   /realtime and patches the current page the moment data changes anywhere:
   stock on product cards/detail, order status badges, the navbar cart
   counter, and the admin dashboard (which refreshes its stats in place via
   assets/js/dashboard-live.js — or reloads once per change burst if that
   script has not loaded yet). The stream is kept alive by the server
   heartbeat; the browser reconnects automatically (retry: 3000) if the
   connection drops. */
(function () {
    'use strict';

    var CTX = window.CTXPATH || '';
    var me = document.body ? document.body.getAttribute('data-userid') : null;
    if (!('EventSource' in window)) {
        return;
    }

    var BADGE_CLASS = {
        PENDING: 'bg-warning text-dark', PROCESSING: 'bg-info',
        SHIPPED: 'bg-primary', COMPLETED: 'bg-success',
        CANCELLED: 'bg-secondary', REFUNDED: 'bg-danger'
    };

    function parse(e) {
        try {
            return JSON.parse(e.data);
        } catch (err) {
            return null;
        }
    }

    function statusLabel(status) {
        if (status === 'OUT_OF_STOCK') return 'Out of stock';
        if (status === 'LOW_STOCK') return 'Only LEFT_STOCK_LEFT left';
        return status === 'DISCONTINUED' ? 'Discontinued' : 'In stock';
    }

    function stockClass(status) {
        return status === 'OUT_OF_STOCK' ? 'out'
            : status === 'LOW_STOCK' ? 'low'
            : status === 'DISCONTINUED' ? 'out' : 'in';
    }

    function applyStock(d) {
        var id = d.productId;
        document.querySelectorAll('[data-stock-for="' + id + '"]').forEach(function (el) {
            var label = statusLabel(d.status).replace('LEFT_STOCK_LEFT', d.stock);
            var dot = el.querySelector('.dot');
            while (el.firstChild) {
                el.removeChild(el.firstChild);
            }
            if (dot) {
                el.appendChild(dot);
            }
            el.appendChild(document.createTextNode(label));
            el.className = el.className.replace(/p-stock-(out|low|in)/, 'p-stock-' + stockClass(d.status));
            var card = el.closest('.product-card');
            if (card) {
                // Only buttons INSIDE a quick-add form are toggled: those are
                // the ones that can actually submit. A bare disabled button
                // rendered server-side must not be enabled into a dead click.
                var btn = card.querySelector('form[data-quickadd] button[type="submit"]');
                if (btn) {
                    btn.disabled = d.status === 'OUT_OF_STOCK' || d.stock <= 0 || d.status === 'DISCONTINUED';
                }
            }
        });

        var avail = document.querySelector('[data-avail-for="' + id + '"]');
        if (avail) {
            avail.textContent = d.stock + ' unit(s)';
        }
        var badge = document.querySelector('[data-stock-badge-for="' + id + '"]');
        if (badge) {
            var cls = d.status === 'DISCONTINUED' ? 'bg-dark'
                : d.status === 'OUT_OF_STOCK' ? 'bg-danger'
                : d.status === 'LOW_STOCK' ? 'bg-warning text-dark' : 'bg-success';
            badge.className = 'badge ' + cls + ' badge-status';
            badge.textContent = d.status === 'DISCONTINUED' ? 'Discontinued'
                : d.status === 'OUT_OF_STOCK' ? 'Out of stock'
                : d.status === 'LOW_STOCK' ? 'Low stock' : 'In stock';
        }
        var qtyInput = document.querySelector('[data-qtymax="' + id + '"]');
        if (qtyInput) {
            qtyInput.max = String(Math.max(d.stock, 1));
        }
        var addForm = document.querySelector('[data-addform="' + id + '"]');
        if (addForm) {
            addForm.hidden = d.status === 'OUT_OF_STOCK' || d.stock <= 0 || d.status === 'DISCONTINUED';
        }
        var lowNote = document.querySelector('[data-plow-for="' + id + '"]');
        if (lowNote) {
            lowNote.hidden = d.stock > 10;
            lowNote.textContent = 'Only ' + d.stock + ' left in stock';
        }
    }

    function applyOrder(d) {
        var id = d.orderId;
        document.querySelectorAll('[data-order-status="' + id + '"]').forEach(function (wrapper) {
            wrapper.querySelectorAll('.badge-status').forEach(function (badge) {
                badge.className = 'badge ' + (BADGE_CLASS[d.status] || '') + ' badge-status';
                badge.textContent = d.status;
            });
        });
        var cancelCard = document.querySelector('[data-cancelcard="' + id + '"]');
        if (cancelCard) {
            cancelCard.hidden = d.status !== 'PENDING';
        }
        var detailPage = document.querySelector('[data-order-detail="' + id + '"]');
        if (detailPage && detailPage.getAttribute('data-current-status') !== d.status) {
            reloadSoon(200);
        }
        notifyDashboard();
        notifyReports();
    }

    function applyCart(d) {
        var badge = document.getElementById('cartCountBadge');
        if (badge) {
            badge.textContent = String(d.count);
            badge.classList.toggle('d-none', d.count <= 0);
            badge.setAttribute('aria-label', d.count + ' items in cart');
        }
    }

    var pendingReload = null;
    function reloadSoon(ms) {
        if (pendingReload) {
            return;
        }
        pendingReload = setTimeout(function () {
            pendingReload = null;
            window.location.reload();
        }, ms);
    }

    /* The admin dashboard refreshes its KPI cards, tables and charts in place
       (dashboard-live.js). Fall back to a reload if that script has not
       finished loading yet. */
    function notifyDashboard() {
        if (!document.querySelector('[data-dashboard]')) {
            return;
        }
        if (window.DashboardLive && typeof window.DashboardLive.refresh === 'function') {
            window.DashboardLive.refresh();
        } else {
            reloadSoon(150);
        }
    }

    /* The admin reports page is fed by the same stream: order or stock
       changes re-fetch the current period's analytics in place
       (reports-live.js), falling back to a reload if it has not loaded. */
    function notifyReports() {
        if (!document.querySelector('[data-reports]')) {
            return;
        }
        if (window.ReportsLive && typeof window.ReportsLive.refresh === 'function') {
            window.ReportsLive.refresh();
        } else {
            reloadSoon(150);
        }
    }

    // Subscribe only to what this page can act on: stock is always fine
    // (it is public data), order events only matter on pages that render
    // them, and cart events only apply to a logged-in user. /realtime
    // refuses private topics for anonymous sessions anyway.
    var topics = ['stock'];
    if (document.querySelector('[data-order-status], [data-cancelcard], [data-order-detail], [data-dashboard], [data-reports]')) {
        topics.push('orders');
    }
    if (document.querySelector('[data-dashboard]') || document.querySelector('[data-live-review-count]')) {
        topics.push('reviews'); // pending-review count is admin-only data
    }
    if (me) {
        topics.push('cart');
    }
    var es = new EventSource(CTX + '/realtime?topics=' + encodeURIComponent(topics.join(',')));

    /* The dashboard's "Live" badge reflects the true stream state: green
       when connected, amber while the browser is reconnecting. */
    function setLiveIndicator(connected) {
        var els = document.querySelectorAll('[data-live-indicator]');
        if (!els.length) {
            return;
        }
        els.forEach(function (el) {
            var alreadyOn = el.classList.contains('text-bg-primary');
            if (alreadyOn === connected) {
                return;
            }
            el.classList.toggle('text-bg-primary', connected);
            el.classList.toggle('text-bg-warning', !connected);
            var icon = el.querySelector('i');
            if (icon) {
                icon.className = connected ? 'bi bi-broadcast me-1' : 'bi bi-arrow-repeat me-1';
            }
            var label = el.querySelector('[data-live-indicator-label]');
            if (label) {
                label.textContent = connected ? 'Live' : 'Reconnecting';
            }
        });
    }

    es.addEventListener('open', function () {
        setLiveIndicator(true);
    });
    es.addEventListener('error', function () {
        setLiveIndicator(false);
    });

    function patchReviewBadges(pending) {
        if (typeof pending !== 'number') {
            return;
        }
        document.querySelectorAll('[data-live-review-count]').forEach(function (b) {
            if (b.textContent !== String(pending)) {
                b.textContent = pending;
            }
            b.classList.toggle('d-none', pending <= 0);
        });
    }

    es.addEventListener('stock', function (e) {
        var d = parse(e);
        if (d) {
            applyStock(d);
            notifyDashboard();
            notifyReports();
        }
    });
    es.addEventListener('orders', function (e) {
        var d = parse(e);
        if (d) {
            applyOrder(d);
        }
    });
    es.addEventListener('cart', function (e) {
        var d = parse(e);
        if (d) {
            applyCart(d);
        }
    });
    es.addEventListener('reviews', function (e) {
        var d = parse(e);
        if (d) {
            patchReviewBadges(d.pending);
        }
        notifyDashboard();
    });
})();
