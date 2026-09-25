package com.example.computer_store.web.controller.admin;

import com.example.computer_store.infrastructure.cache.CacheManager;
import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.infrastructure.monitoring.QueryMonitor;
import com.example.computer_store.infrastructure.persistence.DBConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance monitoring page at {@code /admin/performance} (admin-only via the
 * AdminAuthorizationFilter url-pattern).
 *
 * <p>Shows what the performance features are doing right now: Caffeine cache
 * hit rates and sizes, the HikariCP connection pool state, and JVM memory,
 * plus a small set of data-driven recommendations. Metrics are read live;
 * there is no sampling or persistence.</p>
 */
@WebServlet("/admin/performance")
public class PerformanceMonitoringServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Map<String, Object> poolStats = DBConnection.getPoolStats();
        Map<String, Map<String, Object>> cacheStats = CacheManager.getCacheStats();

        request.setAttribute("cacheEnabled", CacheManager.isCacheEnabled());
        request.setAttribute("compressionEnabled",
                Boolean.parseBoolean(System.getProperty("computerstore.compression.enabled", "true")));
        request.setAttribute("cacheStats", cacheStats);
        request.setAttribute("poolStats", poolStats);
        request.setAttribute("jvm", jvmStats());
        request.setAttribute("queryStats", topQueryStats());
        request.setAttribute("recommendations", recommendations(cacheStats, poolStats));

        request.getRequestDispatcher("/WEB-INF/views/admin/performance.jsp").forward(request, response);
    }

    private Map<String, Object> jvmStats() {
        Runtime rt = Runtime.getRuntime();
        MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        jvm.put("heapUsedMb", mb(bean.getHeapMemoryUsage().getUsed()));
        jvm.put("heapCommittedMb", mb(bean.getHeapMemoryUsage().getCommitted()));
        jvm.put("heapMaxMb", mb(bean.getHeapMemoryUsage().getMax()));
        jvm.put("freeMb", mb(rt.freeMemory()));
        jvm.put("processors", rt.availableProcessors());
        return jvm;
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    /** Top storefront queries by max execution time, for the query table. */
    private List<QueryMonitor.QueryInfo> topQueryStats() {
        List<QueryMonitor.QueryInfo> stats = QueryMonitor.getAllQueryStats();
        stats.sort((a, b) -> Long.compare(b.stats.getMaxTime(), a.stats.getMaxTime()));
        if (stats.size() > 15) {
            return new ArrayList<>(stats.subList(0, 15));
        }
        return stats;
    }

    private List<String> recommendations(Map<String, Map<String, Object>> cacheStats,
                                         Map<String, Object> poolStats) {
        List<String> tips = new ArrayList<>();
        if (!CacheManager.isCacheEnabled()) {
            tips.add("Caching is disabled (-Dcomputerstore.cache.enabled=false). "
                    + "Leave it enabled for production traffic.");
        }
        if (!Boolean.parseBoolean(System.getProperty("computerstore.compression.enabled", "true"))) {
            tips.add("HTTP compression is disabled (-Dcomputerstore.compression.enabled=false). "
                    + "Re-enable it to cut bandwidth and page-load time.");
        }
        Object waiting = poolStats.get("waiting");
        if (waiting instanceof Number && ((Number) waiting).intValue() > 0) {
            tips.add("Connection pool is busy: " + waiting + " thread(s) were waiting for a "
                    + "connection on the last read. Consider raising DB_POOL_MAX.");
        }
        long slowTotal = 0;
        for (QueryMonitor.QueryInfo info : QueryMonitor.getAllQueryStats()) {
            slowTotal += info.stats.getSlowCount();
        }
        if (slowTotal > 0) {
            tips.add(slowTotal + " slow database query(ies) (>1s) were recorded. The "
                    + "catalogue memo absorbs repeated hits on the same page; see the "
                    + "query table below for the individual statements.");
        }
        Object productHit = cacheStats.get("products");
        if (productHit instanceof Map) {
            Number hits = (Number) ((Map<?, ?>) productHit).get("hits");
            Number misses = (Number) ((Map<?, ?>) productHit).get("misses");
            double total = hits.doubleValue() + misses.doubleValue();
            if (total > 200) {
                double rate = hits.doubleValue() / total;
                if (rate < 0.3) {
                    tips.add("Product cache hit rate is " + Math.round(rate * 100)
                            + "% over " + Math.round(total)
                            + " reads. Check that reads go through ProductRepository.findById "
                            + "(single-flight loader).");
                }
            }
        }
        if (tips.isEmpty()) {
            tips.add("No obvious issues right now. Watch the hit rates and pool "
                    + "waiters on this page during peak traffic.");
        }
        return tips;
    }
}