package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.infrastructure.cache.CacheManager;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import com.hengtongan.computerstore.infrastructure.monitoring.QueryMonitor;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
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
 * there is no sampling or persistence. {@code /admin/performance?json=1}
 * returns the same data as JSON for {@code assets/js/performance-live.js}.</p>
 */
@WebServlet("/admin/performance")
public class PerformanceMonitoringServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Map<String, Object> poolStats = DBConnection.getPoolStats();
        Map<String, Map<String, Object>> cacheStats = CacheManager.getCacheStats();

        if ("1".equals(request.getParameter("json"))) {
            writePerformanceJson(response, poolStats, cacheStats);
            return;
        }

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

    /**
     * Live metrics for {@code assets/js/performance-live.js}. Same data as the
     * JSP render, serialized as JSON (cache hit rates/sizes, pool state, JVM
     * memory, top queries and recommendations) so the page can patch itself in
     * place instead of reloading every 30s. This page is never cached.
     */
    private void writePerformanceJson(HttpServletResponse response,
                                      Map<String, Object> poolStats,
                                      Map<String, Map<String, Object>> cacheStats) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        PrintWriter out = response.getWriter();
        out.write("{");
        out.write("\"cacheEnabled\":" + CacheManager.isCacheEnabled() + ",");
        out.write("\"compressionEnabled\":"
                + Boolean.parseBoolean(System.getProperty("computerstore.compression.enabled", "true")) + ",");

        out.write("\"cacheStats\":{");
        boolean first = true;
        for (Map.Entry<String, Map<String, Object>> entry : cacheStats.entrySet()) {
            if (!first) {
                out.write(",");
            }
            first = false;
            Map<String, Object> stats = entry.getValue();
            out.write("\"" + esc(entry.getKey()) + "\":{");
            out.write("\"hits\":" + toJsonNumber(stats.get("hits")) + ",");
            out.write("\"misses\":" + toJsonNumber(stats.get("misses")) + ",");
            out.write("\"hitRate\":" + toJsonNumber(stats.get("hitRate")) + ",");
            out.write("\"size\":" + toJsonNumber(stats.get("size")));
            out.write("}");
        }
        out.write("},");

        out.write("\"poolStats\":{");
        out.write("\"total\":" + toJsonNumber(poolStats.get("total")) + ",");
        out.write("\"active\":" + toJsonNumber(poolStats.get("active")) + ",");
        out.write("\"idle\":" + toJsonNumber(poolStats.get("idle")) + ",");
        out.write("\"waiting\":" + toJsonNumber(poolStats.get("waiting")) + ",");
        out.write("\"max\":" + toJsonNumber(poolStats.get("max")) + ",");
        out.write("\"min\":" + toJsonNumber(poolStats.get("min")));
        out.write("},");

        out.write("\"jvm\":{");
        Map<String, Object> jvm = jvmStats();
        out.write("\"uptimeSeconds\":" + toJsonNumber(jvm.get("uptimeSeconds")) + ",");
        out.write("\"heapUsedMb\":" + toJsonNumber(jvm.get("heapUsedMb")) + ",");
        out.write("\"heapCommittedMb\":" + toJsonNumber(jvm.get("heapCommittedMb")) + ",");
        out.write("\"heapMaxMb\":" + toJsonNumber(jvm.get("heapMaxMb")) + ",");
        out.write("\"freeMb\":" + toJsonNumber(jvm.get("freeMb")) + ",");
        out.write("\"processors\":" + toJsonNumber(jvm.get("processors")));
        out.write("},");

        out.write("\"queryStats\":[");
        List<QueryMonitor.QueryInfo> queries = topQueryStats();
        for (int i = 0; i < queries.size(); i++) {
            if (i > 0) {
                out.write(",");
            }
            QueryMonitor.QueryInfo q = queries.get(i);
            out.write("{\"type\":\"" + esc(q.getType()) + "\",");
            out.write("\"signature\":\"" + esc(q.getSignature()) + "\",");
            out.write("\"count\":" + q.getStats().getCount() + ",");
            out.write("\"avgTime\":" + toJsonNumber(q.getStats().getAvgTime()) + ",");
            out.write("\"maxTime\":" + q.getStats().getMaxTime() + ",");
            out.write("\"slowCount\":" + q.getStats().getSlowCount() + "}");
        }
        out.write("],");

        out.write("\"recommendations\":[");
        List<String> tips = recommendations(cacheStats, poolStats);
        for (int i = 0; i < tips.size(); i++) {
            if (i > 0) {
                out.write(",");
            }
            out.write("\"" + esc(tips.get(i)) + "\"");
        }
        out.write("]");
        out.write("}");
    }

    /** JSON number; {@code null} for absent values, non-finite doubles become 0. */
    private static String toJsonNumber(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double d) {
            return Double.isFinite(d) ? d.toString() : "0";
        }
        return String.valueOf(value);
    }

    /** Minimal JSON string escaper (same policy as the other JSON endpoints). */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}