package com.hengtongan.computerstore.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hit rate is a ratio, so it needs a denominator. This guards the case where
 * there is no denominator yet.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * Caffeine's {@code CacheStats.hitRate()} returns exactly {@code 1.0} when
 * {@code requestCount()} is zero. {@code CacheManager.addStats} passed that
 * straight through, so a cache that had never served a single request was
 * reported to the admin performance panel as a <b>100.0%</b> hit rate.
 *
 * <p>That is worse than a wrong number, because it is wrong in the one direction
 * that stops anyone looking. On a freshly started app, the caches no ordinary
 * browsing has touched yet ({@code details} and {@code orders} on the panel as
 * observed) sit at 0 hits / 0 misses, and both rendered as 100.0% -- visually
 * indistinguishable from a cache that is working perfectly. An admin reading the
 * panel would draw the opposite conclusion about the caches most worth
 * watching.
 *
 * <p>The fix reports no rate at all until at least one request exists, and both
 * the server-rendered view and the live-update script render an em dash for the
 * gap.
 */
class CacheStatsHitRateTest {

    @Test
    void aCacheWithNoRequestsReportsNoHitRate() {
        Map<String, Map<String, Object>> stats = CacheManager.getCacheStats();

        assertNotNull(stats, "getCacheStats() returned nothing");
        assertEquals(9, stats.size(), "expected the nine caches the panel renders");

        stats.forEach((name, entry) -> {
            long requests = ((Number) entry.get("requests")).longValue();
            if (requests == 0) {
                assertNull(entry.get("hitRate"),
                        () -> "cache '" + name + "' has 0 requests, so it must report no hit rate, "
                                + "not " + entry.get("hitRate") + "% -- Caffeine's hitRate() returns "
                                + "1.0 for an untouched cache and that reads as 'working perfectly'");
            }
        });
    }

    @Test
    void everyCacheStillReportsHitsMissesSizeAndRequests() {
        // The em dash must not cost the admin the rest of the row.
        CacheManager.getCacheStats().forEach((name, entry) -> {
            assertNotNull(entry.get("hits"), () -> name + " lost its hit count");
            assertNotNull(entry.get("misses"), () -> name + " lost its miss count");
            assertNotNull(entry.get("size"), () -> name + " lost its entry count");
            assertNotNull(entry.get("requests"), () -> name + " lost its request count");
        });
    }

    @Test
    void hitRatesAreNeverNegativeOrAboveOneHundred() {
        CacheManager.getCacheStats().forEach((name, entry) -> {
            Object rate = entry.get("hitRate");
            if (rate == null) {
                return;                             // no requests: legitimately absent
            }
            double pct = ((Number) rate).doubleValue();
            assertTrue(pct >= 0.0 && pct <= 100.0,
                    () -> "cache '" + name + "' reported a hit rate of " + pct + "%, outside 0-100");
        });
    }

    @Test
    void caffeineIsTheSourceOfTheOneHundredPercentDefault() {
        // Pins the upstream behaviour this class exists to work around, using
        // only Caffeine's public API: build a real cache, never touch it, read
        // its stats. If a Caffeine upgrade ever changed hitRate() to return 0.0
        // for an empty cache this fails, and the guard in addStats can be
        // reconsidered rather than left in place masking behaviour that is gone.
        //
        // recordStats() matters here too, and not only for realism. Without it
        // requestCount() is 0 because nothing is being recorded, not because the
        // cache is untouched, so this test would still pass if Caffeine fixed the
        // 1.0 default -- it would be asserting that an unrecorded cache reads 1.0,
        // which is a different and much weaker claim.
        Cache<String, String> untouched = Caffeine.newBuilder().recordStats().build();
        CacheStats stats = untouched.stats();

        assertEquals(0L, stats.requestCount(), "the fixture cache was never used, so no requests");
        assertEquals(1.0, stats.hitRate(), 0.0,
                "Caffeine reports a perfect 1.0 hit rate for a cache that was never asked "
                        + "anything. This is exactly why CacheManager must not pass hitRate() "
                        + "through unchecked; if this fails, the dependency changed and the "
                        + "null-when-zero-requests guard should be revisited");
    }

    @Test
    void anUntouchedCacheYieldsNoHitRateInTheStatsEntry() {
        // Drives the real addStats with a cache this test owns. The nine caches in
        // CacheManager are static and shared for the whole test JVM, so asserting
        // through getCacheStats() instead would pass or fail depending on which test
        // classes happened to run first -- and a future test that merely reads a
        // cached product would turn that into a spurious failure here.
        Map<String, Map<String, Object>> target = new LinkedHashMap<>();
        CacheManager.addStats(target, "fixture", Caffeine.newBuilder().recordStats().build());

        Map<String, Object> entry = target.get("fixture");
        assertNotNull(entry, "addStats did not record the cache it was given");
        assertEquals(0L, ((Number) entry.get("requests")).longValue(),
                "the fixture cache was never read, so there is no denominator");
        assertNull(entry.get("hitRate"),
                () -> "an untouched cache must report no hit rate, not " + entry.get("hitRate")
                        + "% -- this is the case the em dash on the panel exists for");
    }

    @Test
    void aUsedCacheStillReportsItsRealHitRate() {
        // The other direction, so the guard above cannot be satisfied by never
        // reporting a rate at all. One miss then one hit, which is a 50% rate.
        //
        // recordStats() is not optional: Caffeine 3.1.8 leaves it off unless asked,
        // and an unrecorded cache reports 0/0 for ever. Every cache in CacheManager
        // calls it, so the fixture has to as well or it would test nothing.
        Cache<String, String> warmed = Caffeine.newBuilder().recordStats().build();
        warmed.getIfPresent("absent");            // miss, nothing cached yet
        warmed.put("k", "v");
        warmed.getIfPresent("k");                 // hit

        Map<String, Map<String, Object>> target = new LinkedHashMap<>();
        CacheManager.addStats(target, "warmed", warmed);

        Map<String, Object> entry = target.get("warmed");
        assertEquals(2L, ((Number) entry.get("requests")).longValue(), "one miss then one hit");
        assertEquals(1L, ((Number) entry.get("hits")).longValue());
        assertEquals(1L, ((Number) entry.get("misses")).longValue());
        assertEquals(50.0, ((Number) entry.get("hitRate")).doubleValue(), 0.001,
                "a cache with one hit in two requests is a 50% hit rate, and it must still be "
                        + "reported -- the null-when-zero guard must not swallow real rates");
    }
}
