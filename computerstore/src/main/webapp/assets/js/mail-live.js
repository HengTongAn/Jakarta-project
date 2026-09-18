/* Messenger live updates: polls /mail/json every POLL_MS and
   1) refreshes the unread-count badges in the navbar / admin navigation,
   2) on the chats (contact list) page re-renders the conversation list,
   3) on the chat page re-renders the thread, auto-marks it read when opened,
      and posts replies without a full page reload. */
(function () {
    'use strict';

    var POLL_MS = 5000;

    var badges = document.querySelectorAll('.mail-live-count');
    var convListEl = document.querySelector('[data-conv-list]');
    var threadEl = document.querySelector('[data-chat-thread]');
    if (!badges.length && !convListEl && !threadEl) {
        return;
    }

    var threadRegion = threadEl;
    var knownCount = threadRegion ? threadRegion.querySelectorAll('.chat-msg').length : -1;
    var sentUrl = threadEl ? threadEl.getAttribute('data-sent') : null;

    var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
        'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    function fmt(ms) {
        if (!ms) {
            return '';
        }
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

    function avatarSpan(name, sizeClass, avatarUrl) {
        var av = document.createElement('span');
        av.className = 'chat-avatar' + (sizeClass ? ' ' + sizeClass : '');
        if (avatarUrl) {
            var image = document.createElement('img');
            image.src = (window.CTXPATH || '') + '/' + avatarUrl.replace(/^\/+/, '');
            image.alt = '';
            av.appendChild(image);
        } else {
            av.textContent = (name || '?').charAt(0).toUpperCase();
        }
        return av;
    }

    function textNode(s) {
        return document.createTextNode(s || '');
    }

    /* ---------- conversation list (page 1) ---------- */

    function convRow(conv) {
        var a = document.createElement('a');
        a.className = 'chat-conv';
        a.href = convListEl.getAttribute('data-view') + conv.lastId;

        var mid = document.createElement('div');
        mid.className = 'chat-conv-mid';

        var top = document.createElement('div');
        top.className = 'chat-conv-top';
        var name = document.createElement('span');
        name.className = 'chat-conv-name';
        name.appendChild(textNode(conv.name));
        var time = document.createElement('span');
        time.className = 'chat-time';
        time.appendChild(textNode(fmt(conv.lastTime)));
        top.appendChild(name);
        top.appendChild(time);

        var preview = document.createElement('span');
        preview.className = 'chat-conv-preview';
        preview.appendChild(textNode(conv.subject + ' - ' + conv.snippet));

        mid.appendChild(top);
        mid.appendChild(preview);

        var end = document.createElement('span');
        end.className = 'chat-conv-end';
        if (conv.unread > 0) {
            var badge = document.createElement('span');
            badge.className = 'chat-badge';
            badge.appendChild(textNode(String(conv.unread)));
            end.appendChild(badge);
        }

        a.appendChild(avatarSpan(conv.name, '', conv.avatarUrl));
        a.appendChild(mid);
        a.appendChild(end);
        return a;
    }

    function loadConvList(data) {
        setBadges(data.unread || 0);
        if (!convListEl) {
            return;
        }
        var byName = {};
        (data.messages || []).forEach(function (m) {
            var c = byName[m.senderName];
            if (!c || m.time >= c.lastTime) {
                byName[m.senderName] = c = {
                    name: m.senderName,
                    lastId: m.id,
                    subject: m.subject,
                    snippet: m.snippet,
                    avatarUrl: m.senderAvatarUrl,
                    lastTime: m.time,
                    unread: 0
                };
            }
            if (!m.read) {
                c.unread++;
            }
        });
        var convs = Object.keys(byName).map(function (k) {
            return byName[k];
        });
        convs.sort(function (a, b) {
            return b.lastTime - a.lastTime;
        });

        convListEl.innerHTML = '';
        if (!convs.length) {
            var empty = document.createElement('div');
            empty.className = 'chat-empty';
            empty.appendChild(textNode('No messages yet.'));
            convListEl.appendChild(empty);
            return;
        }
        convs.forEach(function (c) {
            convListEl.appendChild(convRow(c));
        });
    }

    /* ---------- chat thread (page 2) ---------- */

    function bubble(m, mine) {
        var w = document.createElement('div');
        w.className = 'chat-msg ' + (mine ? 'mine' : 'theirs');

        if (!mine) {
            w.appendChild(avatarSpan(peerName(), 'chat-avatar-xs', m.senderAvatarUrl));
        }

        var col = document.createElement('div');
        var b = document.createElement('div');
        b.className = 'chat-bubble';
        b.appendChild(textNode(m.body || m.snippet || ''));
        var meta = document.createElement('div');
        meta.className = 'chat-meta';
        meta.appendChild(textNode(fmt(m.time)));
        col.appendChild(b);
        col.appendChild(meta);
        w.appendChild(col);
        return w;
    }

    function peerName() {
        return threadEl ? threadEl.getAttribute('data-peer-name') : null;
    }

    function loadThread(data) {
        setBadges(data.unread || 0);
        if (!threadEl || !threadRegion) {
            return;
        }
        var peer = peerName();
        var incoming = (data.messages || [])
            .filter(function (m) {
                return m.senderName === peer;
            })
            .sort(function (a, b) {
                return a.time - b.time;
            });
        var outgoing = cache.sent
            .filter(function (m) {
                return m.peer === peer;
            })
            .sort(function (a, b) {
                return a.time - b.time;
            });

        var merged = [];
        var i = 0;
        var j = 0;
        while (i < incoming.length && j < outgoing.length) {
            if (incoming[i].time <= outgoing[j].time) {
                merged.push({ bubble: incoming[i], mine: false });
                i++;
            } else {
                merged.push({ bubble: outgoing[j], mine: true });
                j++;
            }
        }
        while (i < incoming.length) {
            merged.push({ bubble: incoming[i], mine: false });
            i++;
        }
        while (j < outgoing.length) {
            merged.push({ bubble: outgoing[j], mine: true });
            j++;
        }

        var nearBottom = threadRegion.scrollHeight - threadRegion.scrollTop
            - threadRegion.clientHeight < 120;
        var fresh = knownCount !== merged.length;

        threadRegion.innerHTML = '';
        merged.forEach(function (item) {
            threadRegion.appendChild(bubble(item.bubble, item.mine));
        });
        knownCount = merged.length;

        if (nearBottom || fresh || merged.length > 0) {
            threadRegion.scrollTop = threadRegion.scrollHeight;
        }

        if (threadEl.getAttribute('data-inbound') !== 'true' || incoming.length === 0) {
            return;
        }
        var newest = incoming[incoming.length - 1];
        if (newest && newest.read === false) {
            var toggleBase = threadEl.getAttribute('data-json').replace(/\/json$/, '') + '/toggle';
            var formData = new FormData();
            formData.append('id', String(newest.id));
            var headers = {};
            if (window.CSRFTOKEN) {
                headers['X-CSRF-Token'] = window.CSRFTOKEN;
            }
            fetch(toggleBase, { method: 'POST', body: formData, headers: headers }).catch(function () {});
            setBadges(Math.max(0, (data.unread || 0) - 1));
        }
    }

    /* ---------- sent (own) messages: parsed from the /mail/sent page ---------- */

    var cache = { sent: [] };

    function fetchSent() {
        if (!sentUrl) {
            return Promise.resolve([]);
        }
        return fetch(sentUrl)
            .then(function (res) {
                if (!res.ok) {
                    throw new Error('sent page unavailable');
                }
                return res.text();
            })
            .then(function (html) {
                var doc = new DOMParser().parseFromString(html, 'text/html');
                var rows = Array.prototype.slice.call(doc.querySelectorAll('[data-msg-row]'));
                cache.sent = rows.map(function (row) {
                    return {
                        peer: row.getAttribute('data-peer') || '',
                        id: parseInt(row.getAttribute('data-id') || '0', 10),
                        time: parseInt(row.getAttribute('data-time') || '0', 10),
                        body: row.getAttribute('data-body') || ''
                    };
                });
                return cache.sent;
            })
            .catch(function () {
                return cache.sent;
            });
    }

    /* ---------- refresh ---------- */

    function refresh() {
        var url = threadEl ? threadEl.getAttribute('data-json')
            : convListEl ? convListEl.getAttribute('data-json') : null;
        if (!url) {
            return;
        }
        var job = threadEl
            ? Promise.all([fetch(url).then(function (r) { return r.json(); }), fetchSent()])
            : fetch(url).then(function (r) { return r.json(); });
        job
            .then(function (result) {
                if (threadEl) {
                    loadThread(result[0]);
                } else if (convListEl) {
                    loadConvList(result);
                } else {
                    setBadges(result.unread || 0);
                }
            })
            .catch(function () {
                /* session/polling unavailable — stop silently */
            });
    }

    /* ---------- sending ---------- */

    var sendForm = document.querySelector('[data-chat-send]');
    if (sendForm) {
        var sendText = sendForm.querySelector('[data-chat-text]');

        /* Pressing Enter sends the message; Shift+Enter inserts a new line. */
        if (sendText) {
            sendText.addEventListener('keydown', function (ev) {
                if (ev.key !== 'Enter' || ev.shiftKey || ev.isComposing) {
                    return;
                }
                ev.preventDefault();
                sendForm.requestSubmit ? sendForm.requestSubmit() : sendForm.dispatchEvent(new Event('submit', { cancelable: true }));
            });
        }

        sendForm.addEventListener('submit', function (ev) {
            ev.preventDefault();
            var text = sendText;
            if (!text || !text.value.trim()) {
                return;
            }
            fetch(sendForm.getAttribute('action'), { method: 'POST', body: new FormData(sendForm) })
                .then(function (res) {
                    if (!res.ok) {
                        throw new Error('send failed');
                    }
                    var body = text.value.trim();
                    text.value = '';
                    if (threadEl && threadRegion) {
                        var optimistic = {
                            body: body,
                            time: Date.now()
                        };
                        var puppet = bubble(optimistic, true);
                        threadRegion.appendChild(puppet);
                        threadRegion.scrollTop = threadRegion.scrollHeight;
                    }
                    refresh();
                })
                .catch(function () {
                    /* keep the page usable even if delivery reported an error */
                });
        });
    }

    refresh();
    setInterval(refresh, POLL_MS);
})();
