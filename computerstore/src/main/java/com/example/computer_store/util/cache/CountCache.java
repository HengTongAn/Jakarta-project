package com.example.computer_store.util.cache;

import jakarta.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;

/**
 * Tiny session-scoped cache used by the header count filters. The unread-mail
 * and cart counters sit in the navigation on every page, but there is no need
 * to re-run their SQL on each of the ten CSS/JS/image requests that a single
 * page view causes. Values are cached for a few seconds; the real-time SSE
 * events keep them fresh in the browser in between.
 *
 * <p>Values are keyed by the session id in a plain {@link ConcurrentHashMap}
 * instead of being stored as {@code HttpSession} attributes. Tomcat
 * synchronizes attribute access on the session object, so every tab of the
 * same browser contended on that lock just to read a counter; the map removes
 * session-lock traffic from the request path entirely. Entries are pruned on a
 * TTL basis (rollover of the 4 s window), so a session that stops visiting
 * cleans itself up on the next {@link #get} sweep.</p>
 */
public final class CountCache {

    private static final long TTL_MS = 4000L;

    /** Every {sessionId -> {key -> entry}} pair. Bounded by active sessions. */
    private static final ConcurrentHashMap<String, Map<String, Entry>> SESSIONS = new ConcurrentHashMap<>();

    /** Sweep the map for dead sessions every N reads instead of every read. */
    private static final long SWEEP_INTERVAL = 512;
    private static final AtomicLong READS = new AtomicLong();

    private CountCache() {
    }

    public static int get(HttpSession session, String key, IntSupplier loader) {
        long now = System.currentTimeMillis();
        Map<String, Entry> bag = SESSIONS.computeIfAbsent(session.getId(), k -> new ConcurrentHashMap<>());
        Entry entry = bag.get(key);
        if (entry != null && now - entry.storedAt < TTL_MS) {
            return entry.value;
        }
        int value = loader.getAsInt();
        bag.put(key, new Entry(value, now));
        maybeSweep();
        return value;
    }

    /**
     * Periodically drops entries whose TTL has rolled over and removes the
     * session bucket once it becomes empty, so the map can never grow without
     * bound even if sessions are never invalidated.
     */
    private static void maybeSweep() {
        if (READS.incrementAndGet() % SWEEP_INTERVAL != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(session -> {
            Map<String, Entry> bag = session.getValue();
            bag.entrySet().removeIf(e -> now - e.getValue().storedAt >= TTL_MS);
            return bag.isEmpty();
        });
    }

    private static final class Entry {
        private final int value;
        private final long storedAt;

        private Entry(int value, long storedAt) {
            this.value = value;
            this.storedAt = storedAt;
        }
    }
}