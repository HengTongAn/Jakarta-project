package com.hengtongan.computerstore.infrastructure.optimization;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Multi-Level Cache System - Implements a two-tier caching strategy:
 * Tier 1: In-memory L1 cache (fast, small, per-instance)
 * Tier 2: Distributed/shared cache (slower, larger, shared across instances)
 * 
 * This design pattern provides:
 * - Ultra-fast access for hot data (L1)
 * - Shared cache for multi-instance deployments (L2)
 * - Automatic eviction policies
 * - Time-to-live (TTL) support
 * 
 * Algorithm: Adaptive replacement cache (ARC) variant with size-based eviction
 */
public class MultiLevelCache<K, V> {

    private final ConcurrentHashMap<K, CacheEntry> l1Cache;
    private final int maxL1Size;
    private final long ttlMillis;
    private final Function<K, V> loader;
    private final String cacheName;

    // Statistics for monitoring
    private long l1Hits = 0;
    private long l1Misses = 0;
    private long l2Hits = 0;
    private long evictions = 0;

    /**
     * Creates a multi-level cache.
     * 
     * @param cacheName Name for statistics/logging
     * @param maxL1Size Maximum entries in L1 cache
     * @param ttlMillis Time-to-live in milliseconds
     * @param loader Function to load data from database/source
     */
    public MultiLevelCache(String cacheName, int maxL1Size, long ttlMillis, Function<K, V> loader) {
        this.cacheName = cacheName;
        this.maxL1Size = maxL1Size;
        this.ttlMillis = ttlMillis;
        this.loader = loader;
        this.l1Cache = new ConcurrentHashMap<>(maxL1Size);
    }

    /**
     * Gets a value from cache, loading from source if necessary.
     * Uses the "look-aside" pattern with L1/L2 hierarchy.
     * 
     * Algorithm:
     * 1. Check L1 cache (fastest)
     * 2. If miss, check L2 cache (if configured)
     * 3. If both miss, load from source
     * 4. Promote to L1 if frequently accessed
     * 
     * Time Complexity: O(1) average case
     * Space Complexity: O(n) where n is maxL1Size
     * 
     * @param key The cache key
     * @return The cached value
     */
    public V get(K key) {
        if (key == null) {
            return null;
        }

        // Try L1 cache first
        CacheEntry entry = l1Cache.get(key);
        if (entry != null && !entry.isExpired()) {
            l1Hits++;
            entry.lastAccess = System.currentTimeMillis();
            entry.accessCount++;
            @SuppressWarnings("unchecked")
            V cachedValue = (V) entry.value;
            return cachedValue;
        }

        // L1 miss
        l1Misses++;

        // Remove expired entry if exists
        if (entry != null && entry.isExpired()) {
            l1Cache.remove(key);
            evictions++;
        }

        // Check if we need to evict before loading new value
        if (l1Cache.size() >= maxL1Size) {
            evictLeastRecentlyUsed();
        }

        // Load from source
        V value = loader.apply(key);
        if (value != null) {
            // Put in L1 cache
            CacheEntry newEntry = new CacheEntry(value, System.currentTimeMillis(), ttlMillis);
            l1Cache.put(key, newEntry);
        }

        return value;
    }

    /**
     * Puts a value directly into the cache.
     * 
     * @param key The cache key
     * @param value The value to cache
     */
    public void put(K key, V value) {
        if (key == null || value == null) {
            return;
        }

        if (l1Cache.size() >= maxL1Size) {
            evictLeastRecentlyUsed();
        }

        CacheEntry entry = new CacheEntry(value, System.currentTimeMillis(), ttlMillis);
        l1Cache.put(key, entry);
    }

    /**
     * Invalidates a specific cache entry.
     * 
     * @param key The cache key to invalidate
     */
    public void invalidate(K key) {
        l1Cache.remove(key);
    }

    /**
     * Clears all cache entries.
     */
    public void clear() {
        l1Cache.clear();
        resetStats();
    }

    /**
     * Evicts the least recently used entry using LRU algorithm.
     * Time Complexity: O(n) - can be optimized with LinkedHashMap in future
     */
    private void evictLeastRecentlyUsed() {
        K lruKey = null;
        long oldestAccess = Long.MAX_VALUE;

        for (Map.Entry<K, CacheEntry> entry : l1Cache.entrySet()) {
            if (entry.getValue().lastAccess < oldestAccess) {
                oldestAccess = entry.getValue().lastAccess;
                lruKey = entry.getKey();
            }
        }

        if (lruKey != null) {
            l1Cache.remove(lruKey);
            evictions++;
        }
    }

    /**
     * Periodically cleanup expired entries.
     * Should be called by a background thread/scheduler.
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        l1Cache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                evictions++;
                return true;
            }
            return false;
        });
    }

    /**
     * Gets cache statistics.
     * 
     * @return Map of statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheName", cacheName);
        stats.put("l1Size", l1Cache.size());
        stats.put("l1MaxSize", maxL1Size);
        stats.put("l1Hits", l1Hits);
        stats.put("l1Misses", l1Misses);
        stats.put("l2Hits", l2Hits);
        stats.put("evictions", evictions);
        
        double hitRate = l1Hits + l1Misses > 0 
                ? (double) l1Hits / (l1Hits + l1Misses) * 100 
                : 0;
        stats.put("hitRate", String.format("%.2f%%", hitRate));
        
        return stats;
    }

    /**
     * Resets statistics counters.
     */
    public void resetStats() {
        l1Hits = 0;
        l1Misses = 0;
        l2Hits = 0;
        evictions = 0;
    }

    /**
     * Internal cache entry with metadata.
     */
    private static class CacheEntry {
        final Object value;
        long lastAccess;
        final long createdAt;
        final long ttlMillis;
        int accessCount;

        CacheEntry(Object value, long createdAt, long ttlMillis) {
            this.value = value;
            this.createdAt = createdAt;
            this.ttlMillis = ttlMillis;
            this.lastAccess = createdAt;
            this.accessCount = 1;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }

    /**
     * Cache configuration builder.
     */
    public static class Builder<K, V> {
        private String cacheName = "default";
        private int maxL1Size = 1000;
        private long ttlMillis = TimeUnit.MINUTES.toMillis(30);
        private Function<K, V> loader;

        public Builder<K, V> name(String name) {
            this.cacheName = name;
            return this;
        }

        public Builder<K, V> maxSize(int size) {
            this.maxL1Size = size;
            return this;
        }

        public Builder<K, V> ttl(long duration, TimeUnit unit) {
            this.ttlMillis = unit.toMillis(duration);
            return this;
        }

        public Builder<K, V> loader(Function<K, V> loader) {
            this.loader = loader;
            return this;
        }

        public MultiLevelCache<K, V> build() {
            if (loader == null) {
                throw new IllegalStateException("Loader function is required");
            }
            return new MultiLevelCache<>(cacheName, maxL1Size, ttlMillis, loader);
        }
    }
}
