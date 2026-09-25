package com.example.computer_store.infrastructure.realtime;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory push hub that fans out Server-Sent Events to every connected
 * browser. Services publish a tiny payload after their database commit so
 * all live users see the same data (stock, order statuses, ...) without
 * refreshing.
 *
 * <p>The whole hub is a no-op when the JVM system property
 * {@code computerstore.realtime.enabled} is {@code false} (set by the test
 * build), mirroring the notification gate.
 *
 * <p><strong>Clustering Limitation:</strong> This implementation uses in-memory
 * storage and only works for a single JVM instance. For clustered deployments
 * (multiple Tomcat instances), you must implement a distributed EventHub using
 * Redis Pub/Sub, Apache Kafka, or similar messaging infrastructure.
 */
public final class EventHub {

    public static final String GATE = "computerstore.realtime.enabled";
    public static final String TOPICS_KEY = EventHub.class.getName() + ".topics";
    public static final String USER_ID_KEY = EventHub.class.getName() + ".userId";
    public static final String ADMIN_KEY = EventHub.class.getName() + ".admin";

    private static final Logger LOGGER = LoggerFactory.getLogger(EventHub.class);

    private static final Set<AsyncContext> CLIENTS = ConcurrentHashMap.newKeySet();

    /**
     * Hard ceiling on concurrent SSE connections (anti-DoS). When the hub is at
     * capacity new clients are completed and dropped instead of accumulating.
     * Tune with {@code -Dcomputerstore.realtime.maxClients}.
     */
    private static final int MAX_CLIENTS = Integer.getInteger(
            "computerstore.realtime.maxClients", 500);

    private static final ScheduledExecutorService HEARTBEAT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "realtime-heartbeat");
        t.setDaemon(true);
        return t;
    });

    static {
        HEARTBEAT.scheduleAtFixedRate(EventHub::heartbeat, 15, 15, TimeUnit.SECONDS);
    }

    private EventHub() {
    }

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(GATE, "true"));
    }

    /**
     * Attaches a client's async context to the hub. The context is removed
     * automatically when the connection completes, times out or errors.
     */
    public static void register(AsyncContext ac) {
        if (!enabled()) {
            return;
        }
        CLIENTS.add(ac);
        if (CLIENTS.size() > MAX_CLIENTS) {
            // This client pushed the hub over its cap: drop it immediately so
            // the set stays bounded even if a flood of connections arrives.
            CLIENTS.remove(ac);
            try {
                ac.complete();
            } catch (IllegalStateException ignored) {
            }
            LOGGER.warn("SSE client rejected: {}/{} connection slots in use.",
                    CLIENTS.size(), MAX_CLIENTS);
            return;
        }
        ac.addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                CLIENTS.remove(ac);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                CLIENTS.remove(ac);
                if (stopping) {
                    return;
                }
                try {
                    event.getAsyncContext().complete();
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onError(AsyncEvent event) {
                CLIENTS.remove(ac);
                if (stopping) {
                    return;
                }
                // The container fires onError (e.g. "Broken pipe" after a peer
                // disconnect mid-write). If the async context is not completed
                // here the errored request lands in the catch-all exception
                // error-page, so every dropped SSE client becomes a fake 500 in
                // the access log. Complete it exactly like onTimeout does.
                try {
                    event.getAsyncContext().complete();
                } catch (IllegalStateException ignored) {
                }
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }
        });
    }

    /** Pushes a single-quoted JSON payload to every subscriber of the event name. */
    public static void publish(String event, String jsonPayload) {
        publish(event, jsonPayload, null);
    }

    /**
     * Pushes a public event, or a private event only to its owner and admins.
     * Authorization is enforced here on the server, never delegated to browser
     * code which can be modified by any connected user.
     */
    private static void publish(String event, String jsonPayload, Integer ownerUserId) {
        try {
            if (stopping || !enabled() || CLIENTS.isEmpty()) {
                return;
            }
            String frame = "event: " + event + "\ndata: " + jsonPayload + "\n\n";
            List<AsyncContext> dead = new ArrayList<>();
            for (AsyncContext ac : CLIENTS) {
                if (!subscribesTo(ac, event) || !mayReceive(ac, ownerUserId)) {
                    continue;
                }
                try {
                    synchronized (ac) {
                        ac.getResponse().setCharacterEncoding("UTF-8");
                        ac.getResponse().getWriter().write(frame);
                        ac.getResponse().getWriter().flush();
                    }
                } catch (IOException | IllegalStateException e) {
                    dead.add(ac);
                }
            }
            if (!dead.isEmpty()) {
                CLIENTS.removeAll(dead);
            }
        } catch (Throwable t) {
            LOGGER.debug("EventHub publish error (non-fatal): {}", t.getMessage());
        }
    }

    /** Broadcasts a product stock change (stock already persisted). */
    public static void publishStock(int productId, int stockQuantity, String status) {
        publish("stock", "{\"productId\":" + productId
                + ",\"stock\":" + stockQuantity
                + ",\"status\":\"" + status + "\"}");
    }

    /** Broadcasts an order status change (already persisted). */
    public static void publishOrder(int orderId, int userId, String status) {
        publish("orders", "{\"orderId\":" + orderId
                + ",\"status\":\"" + status + "\"}", userId);
    }

    /** Publishes a cart badge update to its owner only. */
    public static void publishCart(int userId, int count) {
        publish("cart", "{\"count\":" + count + "}", userId);
    }

    private static boolean subscribesTo(AsyncContext ac, String event) {
        Object topics = ac.getRequest().getAttribute(TOPICS_KEY);
        if (!(topics instanceof Set)) {
            return true;
        }
        return ((Set<?>) topics).contains("*") || ((Set<?>) topics).contains(event);
    }

    private static boolean mayReceive(AsyncContext ac, Integer ownerUserId) {
        if (ownerUserId == null) {
            return true;
        }
        Object userId = ac.getRequest().getAttribute(USER_ID_KEY);
        return Boolean.TRUE.equals(ac.getRequest().getAttribute(ADMIN_KEY))
                || (userId instanceof Integer && ownerUserId.equals(userId));
    }

    public static void heartbeat() {
        if (stopping) {
            return;
        }
        if (CLIENTS.isEmpty()) {
            return;
        }
        List<AsyncContext> dead = new ArrayList<>();
        for (AsyncContext ac : CLIENTS) {
            try {
                synchronized (ac) {
                    ac.getResponse().getWriter().write(": ping\n\n");
                    ac.getResponse().getWriter().flush();
                }
            } catch (IOException | IllegalStateException e) {
                dead.add(ac);
            }
        }
        if (!dead.isEmpty()) {
            CLIENTS.removeAll(dead);
        }
    }

    /** Set while the webapp is shutting down so no callback touches a dying context. */
    private static volatile boolean stopping = false;

    /**
     * Stops the heartbeat scheduler. Called on application shutdown so the
     * daemon thread group does not outlive the webapp.
     */
    public static void shutdown() {
        stopping = true;
        CLIENTS.clear();
        HEARTBEAT.shutdownNow();
    }
}