package com.example.computer_store.infrastructure.monitoring;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple metrics collector that exports metrics in Prometheus format.
 * Tracks application performance, business metrics, and system health.
 */
public final class MetricsCollector {
    
    private static final Logger LOGGER = Logger.getLogger(MetricsCollector.class.getName());
    
    // Counter metrics (only increase)
    private static final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    /**
     * Upper bound on distinct metric names. Per-entity breakdowns
     * ({@code product_<id>_views}, {@code failed_login_<username>_...}) derive
     * from data an attacker can influence (e.g. arbitrary usernames typed at
     * the login form), so the map must never be allowed to grow without bound.
     */
    private static final int MAX_METRIC_NAMES = 4096;
    
    // Gauge metrics (can go up and down)
    private static final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    
    // Histogram metrics (distributions)
    private static final Map<String, Histogram> histograms = new ConcurrentHashMap<>();
    
    static class Histogram {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong sum = new AtomicLong(0);
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(0);
        
        synchronized void observe(long value) {
            count.incrementAndGet();
            sum.addAndGet(value);
            
            // Update min
            long currentMin;
            do {
                currentMin = min.get();
                if (value >= currentMin) break;
            } while (!min.compareAndSet(currentMin, value));
            
            // Update max
            long currentMax;
            do {
                currentMax = max.get();
                if (value <= currentMax) break;
            } while (!max.compareAndSet(currentMax, value));
        }
        
        long getCount() { return count.get(); }
        long getSum() { return sum.get(); }
        long getMin() { return min.get(); }
        long getMax() { return max.get(); }
        double getAvg() { 
            long c = getCount();
            return c > 0 ? (double) getSum() / c : 0;
        }
    }
    
    private MetricsCollector() {
    }
    
    /**
     * Increments a counter metric.
     */
    public static void incrementCounter(String name) {
        incrementCounter(name, 1);
    }
    
    /**
     * Increments a counter metric by a specific amount.
     */
    public static void incrementCounter(String name, long value) {
        // Cardinality guard: once the map is at capacity, never create NEW
        // names (aggregate metrics always exist, so they keep recording).
        if (counters.size() >= MAX_METRIC_NAMES && !counters.containsKey(name)) {
            return;
        }
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(value);
    }
    
    /**
     * Sets a gauge metric value.
     */
    public static void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong(0)).set(value);
    }
    
    /**
     * Records a value in a histogram metric.
     */
    public static void recordHistogram(String name, long value) {
        histograms.computeIfAbsent(name, k -> new Histogram()).observe(value);
    }
    
    /**
     * Exports all metrics in Prometheus format.
     */
    public static String exportPrometheusFormat() {
        StringBuilder sb = new StringBuilder();
        
        // Export counters
        for (Map.Entry<String, AtomicLong> entry : counters.entrySet()) {
            String metricName = sanitizeMetricName(entry.getKey());
            sb.append("# TYPE ").append(metricName).append(" counter\n");
            sb.append(metricName).append(" ").append(entry.getValue().get()).append("\n");
        }
        
        // Export gauges
        for (Map.Entry<String, AtomicLong> entry : gauges.entrySet()) {
            String metricName = sanitizeMetricName(entry.getKey());
            sb.append("# TYPE ").append(metricName).append(" gauge\n");
            sb.append(metricName).append(" ").append(entry.getValue().get()).append("\n");
        }
        
        // Export histograms
        for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
            String metricName = sanitizeMetricName(entry.getKey());
            Histogram hist = entry.getValue();
            
            sb.append("# TYPE ").append(metricName).append(" summary\n");
            sb.append(metricName).append("_count ").append(hist.getCount()).append("\n");
            sb.append(metricName).append("_sum ").append(hist.getSum()).append("\n");
            if (hist.getMin() != Long.MAX_VALUE) {
                sb.append(metricName).append("_min ").append(hist.getMin()).append("\n");
            }
            sb.append(metricName).append("_max ").append(hist.getMax()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Sanitizes metric names for Prometheus format.
     */
    private static String sanitizeMetricName(String name) {
        return name.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace(".", "_")
                .replaceAll("[^a-z0-9_]", "");
    }
    
    /**
     * Resets all metrics.
     */
    public static void reset() {
        counters.clear();
        gauges.clear();
        histograms.clear();
        LOGGER.info("All metrics reset");
    }
    
    /**
     * Business metrics helpers
     */
    public static void recordProductView(int productId) {
        incrementCounter("product_views_total");
        incrementCounter("product_" + productId + "_views");
    }
    
    public static void recordCartAdd(int productId) {
        incrementCounter("cart_add_total");
        incrementCounter("product_" + productId + "_cart_adds");
    }
    
    public static void recordOrderCreated(double amount) {
        incrementCounter("orders_created_total");
        incrementCounter("revenue_total", (long) (amount * 100)); // Store in cents
        recordHistogram("order_amount", (long) (amount * 100));
    }
    
    public static void recordUserLogin(String username) {
        incrementCounter("user_logins_total");
        incrementCounter("user_" + sanitizeMetricName(username) + "_logins");
    }
    
    public static void recordFailedLogin(String username) {
        incrementCounter("failed_logins_total");
    }
    
    public static void updateActiveUsers(int count) {
        setGauge("active_users", count);
    }
    
    public static void updateCartCount(int count) {
        setGauge("cart_items_total", count);
    }
    
    public static void updateProductCount(int count) {
        setGauge("products_total", count);
    }
    
    public static void updateOrderCount(int count) {
        setGauge("orders_total", count);
    }
    
    public static void recordDatabaseQuery(long durationMs) {
        incrementCounter("database_queries_total");
        recordHistogram("database_query_duration_ms", durationMs);
    }
    
    public static void recordCacheHit() {
        incrementCounter("cache_hits_total");
    }
    
    public static void recordCacheMiss() {
        incrementCounter("cache_misses_total");
    }
    
}