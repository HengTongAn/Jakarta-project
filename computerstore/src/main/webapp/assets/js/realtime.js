/* Real-time cross-user updates. Opens one Server-Sent Events stream to
   /realtime and patches the current page the moment data changes anywhere:
   stock on product cards/detail, order status badges, the navbar cart
   counter, and the admin dashboard (which reloads once per change burst).
   The stream is kept alive by the server heartbeat; the browser reconnects
   automatically (retry: 3000) if the connection drops. */
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
        if (document.querySelector('[data-dashboard]')) {
            reloadSoon(150);
        }
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

    // Subscribe only to what this page can act on: stock is always fine
    // (it is public data), order events only matter on pages that render
    // them, and cart events only apply to a logged-in user. /realtime
    // refuses private topics for anonymous sessions anyway.
    var topics = ['stock'];
    if (document.querySelector('[data-order-status], [data-cancelcard], [data-order-detail], [data-dashboard]')) {
        topics.push('orders');
    }
    if (me) {
        topics.push('cart');
    }
    var es = new EventSource(CTX + '/realtime?topics=' + encodeURIComponent(topics.join(',')));
    es.addEventListener('stock', function (e) {
        var d = parse(e);
        if (d) {
            applyStock(d);
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
})();
