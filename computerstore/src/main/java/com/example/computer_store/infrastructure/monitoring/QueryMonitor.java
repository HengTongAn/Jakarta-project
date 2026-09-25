package com.example.computer_store.infrastructure.monitoring;

import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Database query monitoring utility for performance optimization.
 * Tracks query execution times, counts, and identifies slow queries.
 */
public final class QueryMonitor {
    
    private static final Logger LOGGER = Logger.getLogger(QueryMonitor.class.getName());
    
    // Query execution time threshold (in milliseconds) for "slow" queries
    private static final long SLOW_QUERY_THRESHOLD_MS = 1000;
    
    // Store query statistics
    private static final ConcurrentHashMap<String, QueryStats> queryStats = new ConcurrentHashMap<>();
    
    // Global counters
    private static final AtomicLong totalQueries = new AtomicLong(0);
    private static final AtomicLong totalQueryTime = new AtomicLong(0);
    private static final AtomicLong slowQueries = new AtomicLong(0);
    
    public static class QueryStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicLong maxTime = new AtomicLong(0);
        private final AtomicLong slowCount = new AtomicLong(0);
        
        void record(long executionTime) {
            count.incrementAndGet();
            totalTime.addAndGet(executionTime);
            
            // Update max time
            long currentMax;
            do {
                currentMax = maxTime.get();
                if (executionTime <= currentMax) break;
            } while (!maxTime.compareAndSet(currentMax, executionTime));
            
            // Track slow queries
            if (executionTime > SLOW_QUERY_THRESHOLD_MS) {
                slowCount.incrementAndGet();
            }
        }
        
        public long getCount() { return count.get(); }
        public long getTotalTime() { return totalTime.get(); }
        public long getMaxTime() { return maxTime.get(); }
        public long getSlowCount() { return slowCount.get(); }
        public double getAvgTime() { 
            long count = getCount();
            return count > 0 ? (double) getTotalTime() / count : 0;
        }
    }
    
    private QueryMonitor() {
    }
    
    /**
     * Monitors a query execution and records statistics.
     */
    public static void monitorQuery(String queryType, String querySignature, long executionTime) {
        if (!isMonitoringEnabled()) {
            return;
        }
        
        totalQueries.incrementAndGet();
        totalQueryTime.addAndGet(executionTime);
        
        // Track slow queries
        if (executionTime > SLOW_QUERY_THRESHOLD_MS) {
            slowQueries.incrementAndGet();
            LOGGER.log(Level.WARNING, "SLOW QUERY DETECTED: {0} took {1}ms - {2}", 
                    new Object[]{queryType, executionTime, querySignature});
        }
        
        // Track per-query statistics
        String key = queryType + ":" + querySignature;
        QueryStats stats = queryStats.computeIfAbsent(key, k -> new QueryStats());
        stats.record(executionTime);
    }
    
    /**
     * Gets query statistics for a specific query type.
     */
    public static QueryStats getQueryStats(String queryType, String querySignature) {
        String key = queryType + ":" + querySignature;
        return queryStats.get(key);
    }
    
    /**
     * Gets all query statistics.
     */
    public static List<QueryInfo> getAllQueryStats() {
        List<QueryInfo> allStats = new ArrayList<>();
        for (String key : queryStats.keySet()) {
            String[] parts = key.split(":", 2);
            if (parts.length == 2) {
                QueryStats stats = queryStats.get(key);
                allStats.add(new QueryInfo(parts[0], parts[1], stats));
            }
        }
        return allStats;
    }
    
    /**
     * Logs overall query performance statistics.
     */
    public static void logStatistics() {
        if (!isMonitoringEnabled()) {
            return;
        }
        
        LOGGER.log(Level.INFO, "=== Database Query Statistics ===");
        LOGGER.log(Level.INFO, "Total Queries: {0}", totalQueries.get());
        LOGGER.log(Level.INFO, "Total Query Time: {0}ms", totalQueryTime.get());
        LOGGER.log(Level.INFO, "Average Query Time: {0}ms", 
                totalQueries.get() > 0 ? (double) totalQueryTime.get() / totalQueries.get() : 0);
        LOGGER.log(Level.INFO, "Slow Queries (> {0}ms): {1}", 
                new Object[]{SLOW_QUERY_THRESHOLD_MS, slowQueries.get()});
        
        // Log top slow queries
        List<QueryInfo> allStats = getAllQueryStats();
        allStats.sort((a, b) -> Long.compare(b.stats.getMaxTime(), a.stats.getMaxTime()));
        
        LOGGER.log(Level.INFO, "=== Top 5 Slowest Queries ===");
        int count = Math.min(5, allStats.size());
        for (int i = 0; i < count; i++) {
            QueryInfo info = allStats.get(i);
            LOGGER.log(Level.INFO, "{0}. {1} - Max: {2}ms, Avg: {3}ms, Count: {4}, Slow: {5}", 
                    new Object[]{i + 1, info.signature, info.stats.getMaxTime(), 
                            String.format("%.2f", info.stats.getAvgTime()), 
                            info.stats.getCount(), info.stats.getSlowCount()});
        }
    }
    
    /**
     * Clears all query statistics.
     */
    public static void clearStatistics() {
        queryStats.clear();
        totalQueries.set(0);
        totalQueryTime.set(0);
        slowQueries.set(0);
        LOGGER.info("Query statistics cleared");
    }
    
    /**
     * Checks if query monitoring is enabled via system property.
     */
    public static boolean isMonitoringEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("computerstore.query.monitoring.enabled", "true"));
    }
    
    /**
     * Extracts a query signature (simplified SQL) for tracking.
     */
    public static String extractSignature(String sql) {
        if (sql == null) {
            return "unknown";
        }
        // Remove parameter values and whitespace for signature
        return sql.replaceAll("\\?", "?")
                .replaceAll("\\s+", " ")
                .trim()
                .substring(0, Math.min(100, sql.length()));
    }
    
    /**
     * Query information holder.
     */
    public static class QueryInfo {
        public final String type;
        public final String signature;
        public final QueryStats stats;

        public QueryInfo(String type, String signature, QueryStats stats) {
            this.type = type;
            this.signature = signature;
            this.stats = stats;
        }

        // JavaBean getters: Jakarta EL (Tomcat) only resolves readable
        // properties via getters - public fields alone raise
        // PropertyNotFoundException in EL expressions.
        public String getType() { return type; }
        public String getSignature() { return signature; }
        public QueryStats getStats() { return stats; }
    }
    
    /**
     * Simple query timing helper (no wrapper needed for basic monitoring).
     */
    public static long timeQuery(Runnable queryOperation, String queryType, String querySignature) {
        long start = System.currentTimeMillis();
        try {
            queryOperation.run();
            long duration = System.currentTimeMillis() - start;
            monitorQuery(queryType, querySignature, duration);
            return duration;
        } catch (RuntimeException e) {
            long duration = System.currentTimeMillis() - start;
            monitorQuery(queryType, querySignature + " (FAILED)", duration);
            throw e;
        }
    }
}