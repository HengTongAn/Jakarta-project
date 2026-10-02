package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.PageExperienceSample;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores and aggregates Experienced Page Time samples.
 *
 * <h2>One row per sampled page view</h2>
 *
 * A row is written only when a browser reported a timing, and it carries both what
 * the browser saw and what this server saw. The two are captured separately:
 * {@code ExperienceFilter} times the request and keeps the elapsed milliseconds in
 * a {@code MetricsCollector} histogram, while {@code rum.js} reads the Navigation
 * Timing API and beacons one row describing the same view. They are labelled
 * distinctly in the report and never merged -- the server's own figure is not known
 * until the response has been written, so it cannot reach the browser at all, and
 * the browser's {@code responseStart - requestStart} is a different measurement
 * that happens to share a name.
 *
 * <p>The alternative, writing a server row for every request and a client row for
 * every beacon, would put unpaired server numbers in the same table as paired ones.
 * A request whose script never ran (a bot, a blocked asset, a customer with JS
 * disabled) has no Experienced Page Time at all, and its server figure would sit in
 * the report as though it were a fast page. One row, or nothing.</p>
 *
 * <h2>Why one scan and bucket in Java</h2>
 *
 * Percentiles are the point of this report -- a mean hides the slow tail people
 * actually complain about. This reads the window's rows once and buckets them in
 * memory, so the overall figure and both breakdowns cost a single pass, and the
 * percentiles are exact rather than estimated from a pre-aggregated histogram.
 * That is affordable because the client-side sampling rate and
 * {@link #purgeOlderThan} together bound the row count; it would not be, on
 * unsampled data over a long window.
 */
public class PageExperienceRepository {

    /** Rows older than this are deleted by {@link #purgeOlderThan}. */
    public static final int DEFAULT_RETENTION_DAYS = 7;

    private Connection conn() throws SQLException {
        return com.hengtongan.computerstore.infrastructure.persistence.DBConnection.getConnection();
    }

    /** Persists one sampled page view. */
    public void insert(PageExperienceSample s) throws SQLException {
        String sql = "INSERT INTO page_experience_samples "
                + "(page_type, browser, device, server_ms, ttfb_ms, "
                + " interactive_ms, dom_ready_ms, load_ms, transfer_bytes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getPageType());
            ps.setString(2, s.getBrowser());
            ps.setString(3, s.getDevice());
            /* server_ms and ttfb_ms are clamped rather than nulled, unlike the
             * three client columns. A -1 reaching either of them means the browser
             * reported no responseStart at all, and a row in that state also has
             * ttfb unreported -- so the beacon's own guard normally rejects it
             * before this point. Clamping is the honest fallback for the residue:
             * NULL there would mean "unknown", and the column is declared NOT NULL
             * because 0 is at least visibly implausible next to any real figure,
             * rather than silently looking like an instant answer. */
            ps.setInt(4, Math.max(0, s.getServerMs()));
            ps.setInt(5, Math.max(0, s.getTtfbMs()));
            nullableInt(ps, 6, s.getInteractiveMs());
            nullableInt(ps, 7, s.getDomReadyMs());
            nullableInt(ps, 8, s.getLoadMs());
            ps.setInt(9, Math.max(0, s.getTransferBytes()));
            ps.executeUpdate();
        }
    }

    private static void nullableInt(PreparedStatement ps, int index, int value) throws SQLException {
        if (value < 0) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    /**
     * Reads a nullable timing column, restoring the unreported sentinel.
     *
     * <p>{@code ResultSet.getInt} answers {@code 0} for a SQL NULL rather than
     * failing, so the {@link PageExperienceSample#UNREPORTED} sentinel that
     * {@link #nullableInt} wrote out as NULL comes back indistinguishable from a
     * measured zero. Left alone that quietly corrupts the report twice: the
     * percentile list gains a {@code 0}, dragging every figure it touches toward
     * zero, and the row counts as one that reported a timing when it reported none.
     * A page that never finished rendering would show as the fastest page on the
     * site.</p>
     *
     * <p>{@link ResultSet#wasNull()} is the only way to tell the two apart, and it
     * has to be read before the column is touched again -- hence not folding this
     * into a shared helper with the non-null columns.</p>
     *
     * <p>Package-private only so {@code PageExperienceReportTest} can drive it with a
     * stubbed {@code ResultSet}. {@code DBConnection.getConnection} is static and this
     * project runs Mockito's subclass mock maker, so the query as a whole cannot be
     * stubbed without either a static mock or a live database; this is the one step
     * in it whose behaviour is not obvious, and it is where the defect was.</p>
     */
    static int reportedOrUnreported(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? PageExperienceSample.UNREPORTED : value;
    }

    /** Deletes samples older than the retention window. Returns rows removed. */
    public int purgeOlderThan(int days) throws SQLException {
        String sql = "DELETE FROM page_experience_samples WHERE sampled_at < ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, since(days));
            return ps.executeUpdate();
        }
    }

    private static Timestamp since(int days) {
        return Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(Math.max(1, days)));
    }

    /** Builds the report over the default retention window. */
    public ExperienceReport report() throws SQLException {
        return report(DEFAULT_RETENTION_DAYS);
    }

    /**
     * Builds the full report over the last {@code days} days.
     *
     * <p>A row always has at least one client timing by construction, so the client
     * and server denominators differ only where a browser reported no timings at
     * all, which is why both counts are published rather than assumed equal.</p>
     */
    public ExperienceReport report(int days) throws SQLException {
        String sql = "SELECT page_type, browser, server_ms, ttfb_ms, "
                + "interactive_ms, dom_ready_ms, load_ms, transfer_bytes "
                + "FROM page_experience_samples WHERE sampled_at >= ?";

        List<int[]> interactive = new ArrayList<>();
        List<int[]> domReady = new ArrayList<>();
        List<int[]> load = new ArrayList<>();
        List<int[]> server = new ArrayList<>();
        List<int[]> ttfb = new ArrayList<>();
        List<int[]> transfer = new ArrayList<>();

        Map<String, List<int[]>> byBrowser = new LinkedHashMap<>();
        Map<String, List<int[]>> byPageType = new LinkedHashMap<>();
        Map<String, long[]> browserBytes = new LinkedHashMap<>();
        Map<String, long[]> pageBytes = new LinkedHashMap<>();

        int rowCount = 0;
        int clientReported = 0;

        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, since(days));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                    String pageType = rs.getString("page_type");
                    String browser = rs.getString("browser");
                    int srv = rs.getInt("server_ms");
                    int tfb = rs.getInt("ttfb_ms");
                    // Nullable columns: NULL means the browser reported no timing for
                    // that phase, which is not the same as a fast one. See
                    // reportedOrUnreported for why the sentinel has to be restored here
                    // rather than inferred from a zero.
                    int inter = reportedOrUnreported(rs, "interactive_ms");
                    int dom = reportedOrUnreported(rs, "dom_ready_ms");
                    int ld = reportedOrUnreported(rs, "load_ms");
                    int bytes = rs.getInt("transfer_bytes");

                    server.add(new int[] {srv});
                    ttfb.add(new int[] {tfb});
                    transfer.add(new int[] {bytes});

                    if (inter >= 0 || dom >= 0 || ld >= 0) {
                        clientReported++;
                        interactive.add(new int[] {Math.max(0, inter)});
                        domReady.add(new int[] {Math.max(0, dom)});
                        load.add(new int[] {Math.max(0, ld)});
                        browserBytes.merge(browser, new long[] {bytes}, PageExperienceRepository::sum);
                        pageBytes.merge(pageType, new long[] {bytes}, PageExperienceRepository::sum);
                    }

                    byBrowser.computeIfAbsent(browser, k -> new ArrayList<>())
                            .add(new int[] {inter, srv, tfb, bytes});
                    byPageType.computeIfAbsent(pageType, k -> new ArrayList<>())
                            .add(new int[] {inter, srv, tfb, bytes});
                }
            }
        }

        return new ExperienceReport(
                rowCount, clientReported,
                Percentiles.of(interactive), Percentiles.of(domReady), Percentiles.of(load),
                Percentiles.of(server), Percentiles.of(ttfb), Percentiles.of(transfer),
                buckets(byBrowser, browserBytes),
                buckets(byPageType, pageBytes));
    }

    private static long[] sum(long[] a, long[] b) {
        return new long[] {a[0] + b[0]};
    }

    /**
     * Turns per-key rows into report buckets, slowest first.
     *
     * <p>Rows are {@code [interactive, server, ttfb, bytes]}; an interactive of -1
     * means the browser never reported, and that row contributes to the server
     * figures but not the client ones.</p>
     */
    private static List<Bucket> buckets(Map<String, List<int[]>> rows, Map<String, long[]> bytes) {
        List<Bucket> out = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> e : rows.entrySet()) {
            List<int[]> inter = new ArrayList<>();
            List<int[]> srv = new ArrayList<>();
            List<int[]> tfb = new ArrayList<>();
            for (int[] r : e.getValue()) {
                if (r[0] >= 0) {
                    inter.add(new int[] {r[0]});
                }
                srv.add(new int[] {r[1]});
                tfb.add(new int[] {r[2]});
            }
            long[] b = bytes.get(e.getKey());
            int totalBytes = b == null ? 0 : (int) b[0];
            out.add(new Bucket(e.getKey(), e.getValue().size(), inter.size(),
                    Percentiles.of(inter), Percentiles.of(srv), Percentiles.of(tfb),
                    inter.isEmpty() ? 0 : totalBytes / inter.size()));
        }
        // Buckets with no browser timing sort last instead of first. Ordering them
        // by a client p95 of zero would put the pages we know nothing about at the
        // top of a "fastest pages" reading.
        out.sort(Comparator
                .comparingInt((Bucket x) -> x.getClientCount() > 0 ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(Bucket::getClientP95).reversed())
                .thenComparing(Comparator.comparingDouble(Bucket::getServerP95).reversed())
                .thenComparing(Bucket::getKey));
        return out;
    }

    /** Exact percentiles over a sample of non-negative millisecond values. */
    public static final class Percentiles {
        private final int count;
        private final double average;
        private final int p50;
        private final int p75;
        private final int p95;
        private final int max;

        private Percentiles(int count, double average, int p50, int p75, int p95, int max) {
            this.count = count;
            this.average = average;
            this.p50 = p50;
            this.p75 = p75;
            this.p95 = p95;
            this.max = max;
        }

        public static Percentiles of(List<int[]> values) {
            if (values == null || values.isEmpty()) {
                return new Percentiles(0, 0, 0, 0, 0, 0);
            }
            int[] v = new int[values.size()];
            long total = 0;
            for (int i = 0; i < values.size(); i++) {
                v[i] = values.get(i)[0];
                total += v[i];
            }
            Arrays.sort(v);
            return new Percentiles(v.length, (double) total / v.length,
                    pick(v, 0.50), pick(v, 0.75), pick(v, 0.95), v[v.length - 1]);
        }

        /**
         * Nearest-rank percentile: the smallest value at or above the given rank.
         *
         * <p>Chosen over interpolation because a latency percentile should be a
         * latency somebody actually experienced, not a synthetic value between two
         * of them.</p>
         */
        private static int pick(int[] sorted, double q) {
            int rank = (int) Math.ceil(q * sorted.length);
            return sorted[Math.min(sorted.length - 1, Math.max(0, rank - 1))];
        }

        public int getCount() {
            return count;
        }

        public double getAverage() {
            return Math.round(average * 10) / 10.0;
        }

        public int getP50() {
            return p50;
        }

        public int getP75() {
            return p75;
        }

        public int getP95() {
            return p95;
        }

        public int getMax() {
            return max;
        }
    }

    /** One row of the by-browser or by-page-type breakdown. */
    public static final class Bucket {
        private final String key;
        private final int sampleCount;
        private final int clientCount;
        private final Percentiles client;
        private final Percentiles server;
        private final Percentiles ttfb;
        private final int averageBytes;

        Bucket(String key, int sampleCount, int clientCount, Percentiles client,
               Percentiles server, Percentiles ttfb, int averageBytes) {
            this.key = key;
            this.sampleCount = sampleCount;
            this.clientCount = clientCount;
            this.client = client;
            this.server = server;
            this.ttfb = ttfb;
            this.averageBytes = averageBytes;
        }

        public String getKey() {
            return key;
        }

        public int getSampleCount() {
            return sampleCount;
        }

        public int getClientCount() {
            return clientCount;
        }

        public Percentiles getClient() {
            return client;
        }

        public Percentiles getServer() {
            return server;
        }

        public Percentiles getTtfb() {
            return ttfb;
        }

        public int getClientP95() {
            return client.getP95();
        }

        public int getServerP95() {
            return server.getP95();
        }

        public int getAverageBytes() {
            return averageBytes;
        }
    }

    /** The report: denominators, then overall figures, then both breakdowns. */
    public static final class ExperienceReport {
        private final int rowCount;
        private final int clientReported;
        private final Percentiles interactive;
        private final Percentiles domReady;
        private final Percentiles load;
        private final Percentiles server;
        private final Percentiles ttfb;
        private final Percentiles transfer;
        private final List<Bucket> byBrowser;
        private final List<Bucket> byPageType;

        ExperienceReport(int rowCount, int clientReported, Percentiles interactive,
                         Percentiles domReady, Percentiles load, Percentiles server,
                         Percentiles ttfb, Percentiles transfer,
                         List<Bucket> byBrowser, List<Bucket> byPageType) {
            this.rowCount = rowCount;
            this.clientReported = clientReported;
            this.interactive = interactive;
            this.domReady = domReady;
            this.load = load;
            this.server = server;
            this.ttfb = ttfb;
            this.transfer = transfer;
            this.byBrowser = byBrowser;
            this.byPageType = byPageType;
        }

        public int getRowCount() {
            return rowCount;
        }

        public int getClientReported() {
            return clientReported;
        }

        public Percentiles getInteractive() {
            return interactive;
        }

        public Percentiles getDomReady() {
            return domReady;
        }

        public Percentiles getLoad() {
            return load;
        }

        public Percentiles getServer() {
            return server;
        }

        public Percentiles getTtfb() {
            return ttfb;
        }

        public Percentiles getTransfer() {
            return transfer;
        }

        public List<Bucket> getByBrowser() {
            return byBrowser;
        }

        public List<Bucket> getByPageType() {
            return byPageType;
        }
    }
}
