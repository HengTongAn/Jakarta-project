package com.hengtongan.computerstore.infrastructure.cache;

import com.hengtongan.computerstore.infrastructure.monitoring.MetricsCollector;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Centralized cache manager using Caffeine for high-performance in-memory caching.
 * Provides caching for frequently accessed data like products, categories, and brands.
 * <p>
 * Note: Cached objects should be treated as immutable. If the underlying data changes,
 * the cache must be explicitly invalidated.
 *
 * <p><strong>Clustering Limitation:</strong> these caches live in JVM memory and are
 * per-instance. On a multi-instance deployment disable them with
 * {@code -Dcomputerstore.cache.enabled=false} (or accept momentary staleness /
 * run behind sticky sessions); there is no distributed cache.
 */
public final class CacheManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheManager.class);

    // Performance-optimized cache configuration for admin and user traffic
    private static final long CACHE_EXPIRE_MINUTES = 30;
    private static final long CACHE_MAX_SIZE = 1000;

    // Product cache - much larger for admin and user traffic
    private static final Cache<String, Object> PRODUCT_CACHE = Caffeine.newBuilder()
            .maximumSize(5000)              // Increased from 1000
            .expireAfterWrite(2, TimeUnit.HOURS)  // Increased from 30 minutes
            .recordStats()
            .build();

    // Category cache - longer expiry for stable data
    private static final Cache<String, Object> CATEGORY_CACHE = Caffeine.newBuilder()
            .maximumSize(500)               // Increased from 100
            .expireAfterWrite(4, TimeUnit.HOURS)  // Increased from 1 hour
            .recordStats()
            .build();

    // Brand cache - longer expiry for stable data
    private static final Cache<String, Object> BRAND_CACHE = Caffeine.newBuilder()
            .maximumSize(500)               // Increased from 100
            .expireAfterWrite(4, TimeUnit.HOURS)  // Increased from 1 hour
            .recordStats()
            .build();

    // User cache - admin needs user data frequently
    private static final Cache<String, Object> USER_CACHE = Caffeine.newBuilder()
            .maximumSize(2000)              // Increased from 500
            .expireAfterWrite(1, TimeUnit.HOURS)  // Increased from 15 minutes
            .recordStats()
            .build();

    // Cart cache - cart counts and small cart payloads (single-flight)
    private static final Cache<String, Object> CART_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    // Product-detail page memo (specs + approved reviews + rating) — 60s TTL
    private static final Cache<String, Object> DETAIL_CACHE = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .recordStats()
            .build();

    // Order cache - for admin dashboard and user order history
    private static final Cache<String, Object> ORDER_CACHE = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .recordStats()
            .build();

    // Admin dashboard cache - cache computed metrics
    private static final Cache<String, Object> DASHBOARD_CACHE = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private CacheManager() {
    }

    /**
     * Gets a product from cache or returns null if not cached.
     */
    public static Object getProduct(String key) {
        Object cached = PRODUCT_CACHE.getIfPresent(key);
        if (cached != null) {
            MetricsCollector.recordCacheHit();
        } else {
            MetricsCollector.recordCacheMiss();
        }
        return cached;
    }

    /**
     * Atomically returns the cached value for {@code key}, computing it via
     * {@code loader} on a miss. Caffeine's {@code get(key, fn)} is single-flight
     * per key: concurrent callers with the same key wait for one shared load
     * instead of stampeding the database, then all read the same computed
     * value. Exceptions from the loader propagate and nothing is cached; a
     * {@code null} result is not cached either (so a genuinely missing object
     * is re-probed next time).
     */
    public static Object getOrLoadProduct(String key, java.util.function.Function<String, Object> loader) {
        // The loader invocation is exactly one cache miss by definition.
        return PRODUCT_CACHE.get(key, k -> {
            MetricsCollector.recordCacheMiss();
            return loader.apply(k);
        });
    }

    /**
     * Puts a product in cache.
     * Note: The product object should not be modified after caching.
     */
    public static void putProduct(String key, Object product) {
        PRODUCT_CACHE.put(key, product);
    }

    /**
     * Invalidates a specific product from cache.
     */
    public static void invalidateProduct(String key) {
        PRODUCT_CACHE.invalidate(key);
    }

    /**
     * Invalidates all products from cache.
     */
    public static void invalidateAllProducts() {
        PRODUCT_CACHE.invalidateAll();
        LOGGER.info("All products invalidated from cache");
    }

    /*
     * ---------------------------------------------------------------------
     * CATALOG READ MEMO (the 1000-concurrent-read tier)
     * ---------------------------------------------------------------------
     * Memoizes the CATALOG/LIST page reads (search rows + countSearch count +
     * new-arrivals + trending) through the SAME single-flight Caffeine pattern
     * used everywhere else in this class. Goals, deliberately:
     *
     *  - 60s TTL, NOT 2h: catalogue staleness after an admin write must self-heal
     *    in under a minute, not in 2 hours. 60s is the honesty backstop.
     *  - bounded size (5000 slots) so the memo can't grow without limit under a
     *    1000-concurrent distinct-filter burst.
     *  - a GLOBAL bust (invalidateAllCatalog) hops the same write train as the
     *    per-id product invalidations (ProductRepository writes), and is ALSO called
     *    from brand/category save/delete sites, so a brand-rename or category
     *    merge instantly surfaces in list pages -- no 60s-stale window.
     *  - getOrLoadCatalog mirrors getOrLoadProduct exactly: single-flight on a
     *    miss, the loader is invoked exactly once, null/exception results are
     *    NOT cached, and the product cache stats/metrics pipeline is reused.
     */
    private static final Cache<String, Object> CATALOG_CACHE = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(60, TimeUnit.SECONDS)  // 60s catalog memo -- NOT 2h
            .recordStats()
            .build();

    /**
     * Atomically returns the catalog memo for {@code key}, computing it via
     * {@code loader} on a miss. Single-flight per key (Caffeine), exactly one
     * loader invocation per miss; exceptions and nulls are not cached, so a
     * genuinely missing/errored catalog is re-probed next time rather than
     * poisoned for 60s.
     */
    public static Object getOrLoadCatalog(String key, java.util.function.Function<String, Object> loader) {
        return CATALOG_CACHE.get(key, k -> {
            MetricsCollector.recordCacheMiss();
            return loader.apply(k);
        });
    }

    /**
     * Invalidates ALL catalog memo entries. Called on any admin write that can
     * change what a list page shows (product save/delete AND brand/category
     * save/delete), so edits are visible immediately -- the 60s TTL is only the
     * backstop, not the primary bust.
     */
    public static void invalidateAllCatalog() {
        CATALOG_CACHE.invalidateAll();
        LOGGER.info("All catalog memo entries invalidated (list pages will re-read)");
    }

    /**
     * Gets a category from cache or returns null if not cached.
     */
    public static Object getCategory(String key) {
        Object cached = CATEGORY_CACHE.getIfPresent(key);
        if (cached != null) {
            MetricsCollector.recordCacheHit();
        } else {
            MetricsCollector.recordCacheMiss();
        }
        return cached;
    }

    /**
     * Puts a category in cache.
     */
    public static void putCategory(String key, Object category) {
        CATEGORY_CACHE.put(key, category);
    }

    /**
     * Invalidates all categories from cache.
     */
    public static void invalidateAllCategories() {
        CATEGORY_CACHE.invalidateAll();
        LOGGER.info("All categories invalidated from cache");
    }

    /**
     * Gets a brand from cache or returns null if not cached.
     */
    public static Object getBrand(String key) {
        Object cached = BRAND_CACHE.getIfPresent(key);
        if (cached != null) {
            MetricsCollector.recordCacheHit();
        } else {
            MetricsCollector.recordCacheMiss();
        }
        return cached;
    }

    /**
     * Puts a brand in cache.
     */
    public static void putBrand(String key, Object brand) {
        BRAND_CACHE.put(key, brand);
    }

    /**
     * Invalidates all brands from cache.
     */
    public static void invalidateAllBrands() {
        BRAND_CACHE.invalidateAll();
        LOGGER.info("All brands invalidated from cache");
    }

    /**
     * Gets a user from cache or returns null if not cached.
     */
    public static Object getUser(String key) {
        Object cached = USER_CACHE.getIfPresent(key);
        if (cached != null) {
            MetricsCollector.recordCacheHit();
        } else {
            MetricsCollector.recordCacheMiss();
        }
        return cached;
    }

    /**
     * Puts a user in cache.
     */
    public static void putUser(String key, Object user) {
        USER_CACHE.put(key, user);
    }

    /**
     * Invalidates a specific user from cache.
     */
    public static void invalidateUser(String key) {
        USER_CACHE.invalidate(key);
    }

    /**
     * Invalidates all users from cache.
     */
    public static void invalidateAllUsers() {
        USER_CACHE.invalidateAll();
        LOGGER.info("All users invalidated from cache");
    }

    /**
     * Gets cart data from cache or returns null if not cached.
     */
    public static Object getCart(String key) {
        Object cached = CART_CACHE.getIfPresent(key);
        if (cached != null) {
            MetricsCollector.recordCacheHit();
        } else {
            MetricsCollector.recordCacheMiss();
        }
        return cached;
    }

    /**
     * Single-flight cart load (used for per-user cart counts so badge filters
     * never stampede the DB under a click burst).
     */
    public static Object getOrLoadCart(String key, java.util.function.Function<String, Object> loader) {
        return CART_CACHE.get(key, k -> {
            MetricsCollector.recordCacheMiss();
            return loader.apply(k);
        });
    }

    /**
     * Puts cart data in cache.
     */
    public static void putCart(String key, Object cart) {
        CART_CACHE.put(key, cart);
    }

    /**
     * Invalidates specific cart from cache.
     */
    public static void invalidateCart(String key) {
        CART_CACHE.invalidate(key);
    }

    /** Convenience: drop the memoized cart-count for one user. */
    public static void invalidateCartCount(int userId) {
        CART_CACHE.invalidate(cartCountKey(userId));
    }

    public static String cartCountKey(int userId) {
        return "cart_count_" + userId;
    }

    /**
     * Invalidates all carts from cache.
     */
    public static void invalidateAllCarts() {
        CART_CACHE.invalidateAll();
        LOGGER.info("All carts invalidated from cache");
    }

    /**
     * Single-flight product-detail page memo (specs + approved reviews + rating).
     * User-specific fields (myReview) must stay outside this cache.
     */
    public static Object getOrLoadDetail(String key, java.util.function.Function<String, Object> loader) {
        return DETAIL_CACHE.get(key, k -> {
            MetricsCollector.recordCacheMiss();
            return loader.apply(k);
        });
    }

    public static void invalidateProductDetail(int productId) {
        DETAIL_CACHE.invalidate("detail_" + productId);
    }

    public static void invalidateAllProductDetails() {
        DETAIL_CACHE.invalidateAll();
        LOGGER.info("All product-detail memos invalidated");
    }

    /** Admin product list memo key (full inventory for admin pages). */
    public static final String PRODUCTS_ALL_KEY = "products_all";

    public static void invalidateProductList() {
        PRODUCT_CACHE.invalidate(PRODUCTS_ALL_KEY);
    }

    /**
     * Gets order data from cache or returns null if not cached.
     */
    public static Object getOrder(String key) {
        Object cached = ORDER_CACHE.getIfPresent(key);
        if (cached != null) {
            MetricsCollector.recordCacheHit();
        } else {
            MetricsCollector.recordCacheMiss();
        }
        return cached;
    }

    /**
     * Puts order data in cache.
     */
    public static void putOrder(String key, Object order) {
        ORDER_CACHE.put(key, order);
    }

    /**
     * Invalidates specific order from cache.
     */
    public static void invalidateOrder(String key) {
        ORDER_CACHE.invalidate(key);
    }

    /**
     * Invalidates all orders from cache.
     */
    public static void invalidateAllOrders() {
        ORDER_CACHE.invalidateAll();
        LOGGER.info("All orders invalidated from cache");
    }

    /**
     * Gets dashboard data from cache or returns null if not cached.
     */
    public static Object getDashboard(String key) {
        Object cached = DASHBOARD_CACHE.getIfPresent(key);
        if (cached != null) {
            MetricsCollector.recordCacheHit();
        } else {
            MetricsCollector.recordCacheMiss();
        }
        return cached;
    }

    /**
     * Puts dashboard data in cache.
     */
    public static void putDashboard(String key, Object dashboard) {
        DASHBOARD_CACHE.put(key, dashboard);
    }

    /**
     * Invalidates specific dashboard data from cache.
     */
    public static void invalidateDashboard(String key) {
        DASHBOARD_CACHE.invalidate(key);
    }

    /**
     * Invalidates all dashboard data from cache.
     */
    public static void invalidateAllDashboard() {
        DASHBOARD_CACHE.invalidateAll();
        LOGGER.info("All dashboard data invalidated from cache");
    }

    /**
     * Clears all caches.
     */
    public static void clearAll() {
        PRODUCT_CACHE.invalidateAll();
        CATALOG_CACHE.invalidateAll();
        DETAIL_CACHE.invalidateAll();
        CATEGORY_CACHE.invalidateAll();
        BRAND_CACHE.invalidateAll();
        USER_CACHE.invalidateAll();
        CART_CACHE.invalidateAll();
        ORDER_CACHE.invalidateAll();
        DASHBOARD_CACHE.invalidateAll();
        LOGGER.info("All caches cleared");
    }

    /**
     * Returns per-cache statistics (hits, misses, hit rate percentage and
     * approximate entry count) for the /admin/performance page. The returned
     * map is a snapshot; callers must not keep a long-lived reference to it.
     */
    public static java.util.LinkedHashMap<String, Map<String, Object>> getCacheStats() {
        java.util.LinkedHashMap<String, Map<String, Object>> all = new java.util.LinkedHashMap<>();
        addStats(all, "products", PRODUCT_CACHE);
        addStats(all, "catalog", CATALOG_CACHE);
        addStats(all, "details", DETAIL_CACHE);
        addStats(all, "categories", CATEGORY_CACHE);
        addStats(all, "brands", BRAND_CACHE);
        addStats(all, "users", USER_CACHE);
        addStats(all, "carts", CART_CACHE);
        addStats(all, "orders", ORDER_CACHE);
        addStats(all, "dashboard", DASHBOARD_CACHE);
        return all;
    }

    private static void addStats(Map<String, Map<String, Object>> target, String name, Cache<?, ?> cache) {
        CacheStats stats = cache.stats();
        Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("hits", stats.hitCount());
        entry.put("misses", stats.missCount());
        entry.put("hitRate", Math.round(stats.hitRate() * 1000) / 10.0);
        entry.put("size", cache.estimatedSize());
        target.put(name, entry);
    }

    /**
     * Logs cache statistics for monitoring.
     */
    public static void logStats() {
        CacheStats productStats = PRODUCT_CACHE.stats();
        CacheStats catalogStats = CATALOG_CACHE.stats();
        CacheStats detailStats = DETAIL_CACHE.stats();
        CacheStats categoryStats = CATEGORY_CACHE.stats();
        CacheStats brandStats = BRAND_CACHE.stats();
        CacheStats userStats = USER_CACHE.stats();
        CacheStats cartStats = CART_CACHE.stats();
        CacheStats orderStats = ORDER_CACHE.stats();
        CacheStats dashboardStats = DASHBOARD_CACHE.stats();

        LOGGER.info("=== Cache Statistics ===");
        LOGGER.info("Products - Hit Rate: {}%, Hits: {}, Misses: {}",
                productStats.hitRate() * 100, productStats.hitCount(), productStats.missCount());
        LOGGER.info("Catalog - Hit Rate: {}%, Hits: {}, Misses: {}",
                catalogStats.hitRate() * 100, catalogStats.hitCount(), catalogStats.missCount());
        LOGGER.info("Details - Hit Rate: {}%, Hits: {}, Misses: {}",
                detailStats.hitRate() * 100, detailStats.hitCount(), detailStats.missCount());
        LOGGER.info("Categories - Hit Rate: {}%, Hits: {}, Misses: {}",
                categoryStats.hitRate() * 100, categoryStats.hitCount(), categoryStats.missCount());
        LOGGER.info("Brands - Hit Rate: {}%, Hits: {}, Misses: {}",
                brandStats.hitRate() * 100, brandStats.hitCount(), brandStats.missCount());
        LOGGER.info("Users - Hit Rate: {}%, Hits: {}, Misses: {}",
                userStats.hitRate() * 100, userStats.hitCount(), userStats.missCount());
        LOGGER.info("Carts - Hit Rate: {}%, Hits: {}, Misses: {}",
                cartStats.hitRate() * 100, cartStats.hitCount(), cartStats.missCount());
        LOGGER.info("Orders - Hit Rate: {}%, Hits: {}, Misses: {}",
                orderStats.hitRate() * 100, orderStats.hitCount(), orderStats.missCount());
        LOGGER.info("Dashboard - Hit Rate: {}%, Hits: {}, Misses: {}",
                dashboardStats.hitRate() * 100, dashboardStats.hitCount(), dashboardStats.missCount());
    }

    /**
     * Checks if caching is enabled via system property.
     */
    public static boolean isCacheEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("computerstore.cache.enabled", "true"));
    }
}