package com.hengtongan.computerstore.web.monitoring;

import com.hengtongan.computerstore.core.domain.entity.PageExperienceSample;
import com.hengtongan.computerstore.core.repository.PageExperienceRepository;
import com.hengtongan.computerstore.core.repository.PageExperienceRepository.ExperienceReport;
import com.hengtongan.computerstore.core.repository.PageExperienceRepository.Percentiles;
import com.hengtongan.computerstore.infrastructure.monitoring.PageType;
import com.hengtongan.computerstore.infrastructure.monitoring.UserAgentClassifier;
import com.hengtongan.computerstore.web.controller.monitoring.ExperienceBeaconServlet;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the Experienced Page Time collection pipeline.
 *
 * <h2>Why these are worth guarding</h2>
 *
 * EPT is a number an operator will act on -- "Safari is four times slower than
 * Chrome, fix it" -- so a wrong number here causes real work to be done in the
 * wrong place. The failure modes worth protecting against are:
 *
 * <ul>
 *   <li><b>Unbounded page types.</b> Keying on the raw URL would turn "which page
 *       is slowest" into a list of single visits, and let a crawler invent a new
 *       type per hit. {@link PageType} is the only thing preventing that.</li>
 *   <li><b>Unbounded browsers.</b> Storing the raw User-Agent is both a
 *       fingerprint and an unbounded key. {@link UserAgentClassifier} is what keeps
 *       the column to a fixed set of families.</li>
 *   <li><b>Mixing what the server saw with what the browser saw.</b> A request
 *       whose script never ran has no EPT; letting its server time into the report
 *       would show a fast page nobody experienced.</li>
 *   <li><b>A percent of zero meaning "fastest".</b> A browser that reported
 *       nothing must not sort above a genuinely quick page.</li>
 * </ul>
 *
 * <h2>What these tests do not establish</h2>
 *
 * They read source and call pure functions. Nothing here performs a network
 * request, executes {@code rum.js}, or queries MySQL. The one claim that really
 * matters -- that the browser's numbers arrive intact and land in the right
 * columns -- is verified against the live database separately, because a mocked
 * servlet cannot show it.
 */
class PageExperienceCollectionTest {

    /** Builds a request whose method, URI and id parameter are all controllable. */
    private static HttpServletRequest get(String uri, String id) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/computerstore" + uri);
        when(request.getContextPath()).thenReturn("/computerstore");
        when(request.getParameter("id")).thenReturn(id);
        return request;
    }

    // ------------------------------------------------------------------
    // PageType: bounded labels, and the detail suffix.
    // ------------------------------------------------------------------

    @Test
    void aDetailViewIsLabelledSeparatelyFromItsList() {
        // This is the distinction that makes "which record pages are slowest"
        // answerable. A list renders 24 cards; a detail renders one product plus
        // its reviews. Averaging them hides the number the operator wants.
        assertEquals("/products", PageType.of(get("/products", null)));
        assertEquals("/products:detail", PageType.of(get("/products", "41")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/assets/css/app.css",
            "/assets/js/rum.js",
            "/WEB-INF/web.xml",
            "/favicon.ico",
            "/robots.txt",
            "/manifest.webmanifest",
            "/realtime",
            "/health",
            "/logo.png"})
    void infrastructureAndFilesAreNotPageViews(String uri) {
        assertNull(PageType.of(get(uri, null)),
                uri + " is not a page a person interacts with, so it must not become a page type. "
                        + "Static assets in particular would dominate the volume if they counted.");
    }

    @Test
    void aPathOnlyResemblingAnExcludedPrefixIsStillAPage() {
        // The exclusion must be boundary-checked. "/assetsomething" is a real page,
        // and a naive startsWith("/assets") would drop it while also failing to
        // match "/favicon.ico" exactly.
        assertEquals("/assetsomething", PageType.of(get("/assetsomething", null)));
    }

    @Test
    void nonGetRequestsAreNotPageViews() {
        HttpServletRequest post = mock(HttpServletRequest.class);
        when(post.getMethod()).thenReturn("POST");
        assertNull(PageType.of(post),
                "A POST is a submission, not a navigation. Mixing form posts into page-load "
                        + "statistics would make writes look like slow pages.");
    }

    @Test
    void aLabelNeverCarriesAQueryStringOrFragment() {
        // A route template is a kind of page. A URL with a query string is not, and
        // storing one would leak whatever was in the parameters into the database.
        HttpServletRequest request = get("/products?token=abc123", null);
        when(request.getRequestURI()).thenReturn("/computerstore/products?token=abc123");
        assertEquals("/products", PageType.of(request));
    }

    @Test
    void labelsAreLengthCapped() {
        StringBuilder deep = new StringBuilder("/");
        deep.append("a".repeat(500));
        String label = PageType.of(get(deep.toString(), null));
        assertNotNull(label);
        assertTrue(label.length() <= 64,
                () -> "A " + label.length() + "-character label is not a bounded page type: "
                        + label);
    }

    @Test
    void aTrailingSlashDoesNotCreateASecondPageType() {
        // /products and /products/ are the same page. Two labels would split the
        // samples between them and halve the apparent sample size of each.
        assertEquals(PageType.of(get("/products", null)),
                PageType.of(get("/products/", null)));
    }

    @Test
    void anEmptyLabelNeverCollapsesToABlankKey() {
        HttpServletRequest request = get("/", null);
        assertNotNull(PageType.of(request), "the home page is a page");
    }

    // ------------------------------------------------------------------
    // UserAgentClassifier: a fixed set of families, never the raw string.
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{1}")
    @CsvSource({
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36, Chrome",
            "Mozilla/5.0 (Macintosh) AppleWebKit/605.1 Safari/605.1 Version/17.0 Safari/605.1, Safari",
            "Mozilla/5.0 (X11; Linux) Gecko/20100101 Firefox/121.0, Firefox",
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Edg/120.0, Edge",
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/119.0 Safari/537.36 OPR/105.0, Opera",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36, Chrome",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) CriOS/120.0 Mobile/15E148 Safari/604.1, Chrome",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) FxiOS/121.0 Mobile/15E148 Safari/605.1, Firefox",
            "Mozilla/5.0 (Linux; Android 13) SamsungBrowser/23.0 Chrome/115.0 Mobile Safari/537.36, Samsung",
            "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 6.2; Trident/6.0), IE",
            "curl/8.4.0, Other",
            "some-unknown-client/1.0, Other"})
    void browsersAreReducedToAFixedSetOfFamilies(String userAgent, String expected) {
        assertEquals(expected, UserAgentClassifier.browser(userAgent));
    }

    @Test
    void edgeAndOperaAreNotMistakenForChromeOrSafari() {
        // Every Chromium UA also says "Safari/537.36", and Edge also says
        // "Chrome/120.0". Ordering the checks most-specific-first is the only
        // reason these come out right, and the symptom of getting it wrong is a
        // report that blames Safari for Edge's problems.
        String edge = "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Edg/120.0";
        assertEquals("Edge", UserAgentClassifier.browser(edge));
        assertFalse("Safari".equals(UserAgentClassifier.browser(edge)),
                "an Edge request must not be filed under Safari just because its UA mentions Safari");
    }

    @Test
    void theRawUserAgentIsNeverReturned() {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; rv:121.0) Gecko/20100101 Firefox/121.0";
        String browser = UserAgentClassifier.browser(userAgent);
        assertFalse(browser.contains("Mozilla") || browser.contains("Gecko"),
                () -> browser + " looks like it carries the raw User-Agent, which is a fingerprint "
                        + "and an unbounded grouping key");
    }

    @ParameterizedTest
    @CsvSource({
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) AppleWebKit/605.1 Mobile/15E148 Safari/604.1, mobile",
            "Mozilla/5.0 (iPad; CPU OS 17_0) AppleWebKit/605.1 Safari/605.1, tablet",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36, mobile",
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120.0 Safari/537.36, desktop"})
    void devicesAreReducedToAClass(String userAgent, String expected) {
        assertEquals(expected, UserAgentClassifier.device(userAgent));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; bingbot/2.0)",
            "curl/8.4.0",
            "wget/1.21",
            "python-requests/2.31.0",
            "Java/17.0.9",
            "okhttp/4.12.0",
            "HeadlessChrome/120.0"})
    void botsAreRecognisedSoTheyDoNotSkewTheReport(String userAgent) {
        assertTrue(UserAgentClassifier.isAutomatedClient(userAgent),
                userAgent + " is an automated client. It runs no timing script, so it would "
                        + "contribute only a fast server number and nothing to the client figures.");
    }

    @Test
    void aMissingUserAgentCountsAsAutomated() {
        // Overwhelmingly a script or a health check rather than a person, and it
        // certainly did not run the timing script.
        assertTrue(UserAgentClassifier.isAutomatedClient(null));
        assertTrue(UserAgentClassifier.isAutomatedClient("  "));
    }

    @Test
    void aRealBrowserIsNotMistakenForABot() {
        assertFalse(UserAgentClassifier.isAutomatedClient(
                "Mozilla/5.0 (Windows NT 10.0; rv:121.0) Gecko/20100101 Firefox/121.0"),
                "excluding real browsers would empty the report");
    }

    // ------------------------------------------------------------------
    // Percentiles: the arithmetic behind the numbers.
    // ------------------------------------------------------------------

    private static List<int[]> values(int... ms) {
        return java.util.Arrays.stream(ms).mapToObj(v -> new int[] {v}).toList();
    }

    @Test
    void percentilesUseNearestRankSoEveryFigureIsARealLatency() {
        // A percentile should be a latency somebody actually experienced, not a
        // synthetic value interpolated between two of them.
        Percentiles p = Percentiles.of(values(10, 20, 30, 40, 50, 60, 70, 80, 90, 100));
        assertEquals(50, p.getP50());
        assertEquals(80, p.getP75());
        assertEquals(100, p.getP95());
        assertEquals(100, p.getMax());
        assertEquals(55.0, p.getAverage(), 0.01);
    }

    @Test
    void aSingleSampleIsEveryPercentile() {
        Percentiles p = Percentiles.of(values(420));
        assertEquals(420, p.getP50());
        assertEquals(420, p.getP95());
        assertEquals(1, p.getCount());
    }

    @Test
    void noSamplesIsZeroRatherThanAbsent() {
        // The report has to render something when a page has no browser timings.
        // Returning a null here would turn "nothing sampled" into an exception on
        // the admin page, which is worse than showing a zero.
        Percentiles p = Percentiles.of(List.of());
        assertEquals(0, p.getCount());
        assertEquals(0, p.getP50());
        assertEquals(0.0, p.getAverage(), 0.001);
    }

    @Test
    void orderOfInputDoesNotAffectTheResult() {
        Percentiles sorted = Percentiles.of(values(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        Percentiles shuffled = Percentiles.of(values(10, 3, 7, 1, 9, 2, 8, 4, 6, 5));
        assertEquals(sorted.getP50(), shuffled.getP50());
        assertEquals(sorted.getP95(), shuffled.getP95());
        assertEquals(sorted.getAverage(), shuffled.getAverage(), 0.001);
    }

    @Test
    void theSlowTailIsNotHiddenByTheMean() {
        // Five fast loads and one very slow one. The mean lands between the two
        // clusters and describes no actual customer: p50 is 63ms, p95 is 5000ms.
        // A mean-only report would call this page fine, and p95 is the number that
        // corresponds to the person who complained.
        Percentiles p = Percentiles.of(values(50, 60, 55, 70, 65, 5000));
        assertEquals(5000, p.getP95());
        assertEquals(60, p.getP50());
        assertTrue(p.getAverage() > p.getP50() && p.getAverage() < p.getP95(),
                () -> "the mean (" + p.getAverage() + ") should sit between the median and the "
                        + "tail, which is exactly why it describes nobody in particular");
    }

    // ------------------------------------------------------------------
    // Beacon validation: every field is client-supplied.
    // ------------------------------------------------------------------

    @Test
    void theSampleRateIsNeverBelowOne() {
        // A rate of 0 would make nextInt(0) throw, and a negative rate would too.
        // The property is that sampleRate() is always usable as a modulus.
        assertTrue(ExperienceBeaconServlet.sampleRate() >= 1,
                () -> "sampleRate() returned " + ExperienceBeaconServlet.sampleRate()
                        + ", which cannot be used as a modulus");
    }

    @Test
    void theRetentionWindowIsShortEnoughToBoundTheTable() {
        // Sampling alone does not bound row count over time; the purge does. Seven
        // days is the agreed figure and is asserted here because a change to it
        // would quietly change what the admin page claims it is reporting over.
        assertEquals(7, PageExperienceRepository.DEFAULT_RETENTION_DAYS);
    }

    @Test
    void aSampleWithNoBrowserTimingIsRecognisedAsSuch() {
        PageExperienceSample sample = new PageExperienceSample("/products", "Chrome", "desktop");
        assertFalse(sample.hasClientTiming(),
                "a freshly built sample defaults to 0, which is both a plausible latency and "
                        + "indistinguishable from a reported one. It must read as unreported.");
        assertEquals(PageExperienceSample.UNREPORTED, sample.getInteractiveMs(),
                "the default must be the explicit sentinel, not a bare int zero");
        sample.setInteractiveMs(1200);
        assertTrue(sample.hasClientTiming());
    }

    @Test
    void aReportedZeroMillisecondIsDistinguishableFromAnUnreportedOne() {
        // The sentinel has to be negative for exactly this reason. A browser that
        // genuinely measured 0 ms is not the same fact as one that measured nothing,
        // and collapsing them would either invent instant pages or discard real ones.
        PageExperienceSample sample = new PageExperienceSample("/products", "Chrome", "desktop");
        sample.setInteractiveMs(0);
        assertTrue(sample.hasClientTiming(),
                "a genuine 0 ms is a measurement and must be stored, not treated as missing");
        assertEquals(0, sample.getInteractiveMs());
    }

    @Test
    void theBucketSortPutsMeasuredPagesBeforeUnmeasuredOnes() {
        // Read from the repository source because the comparator is a private detail
        // and Bucket's constructor is package-private. What is being guarded is the
        // property the comparator exists to provide: a page with no browser timings
        // has a client p95 of zero, and sorting naively on p95 would file it at the
        // TOP of a "fastest pages" table. The assertion is on the comparator being
        // present and ordered so clientCount is checked before the percentiles.
        String source = readRepositorySource();
        String sortBlock = between(source, "out.sort(", "return out;");
        assertFalse(sortBlock.isEmpty(), "could not find the bucket comparator in the repository");

        assertTrue(sortBlock.contains("getClientCount()"),
                "the comparator must test clientCount before the percentiles, otherwise a page "
                        + "with no browser timings sorts as the fastest on the site");
        int countCheck = sortBlock.indexOf("getClientCount()");
        int p95 = sortBlock.indexOf("Bucket::getClientP95");
        assertTrue(countCheck >= 0 && p95 > countCheck,
                "getClientCount() must be compared before the client p95 so unmeasured pages sort last");
        assertTrue(sortBlock.contains("thenComparing"),
                "the sort must be a chained comparator rather than a single-key sort, or a tie on "
                        + "one field makes the ordering arbitrary");
    }

    private static String readRepositorySource() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/java/com/hengtongan/computerstore/core/repository/"
                            + "PageExperienceRepository.java"));
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not read PageExperienceRepository.java", e);
        }
    }

    /** The text between two markers, or an empty string if either is absent. */
    private static String between(String source, String from, String to) {
        int start = source.indexOf(from);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf(to, start);
        return end < 0 ? "" : source.substring(start, end);
    }

    @Test
    void theReportShapeCarriesBothBreakdownsAndTheDenominator() {
        // Reflection rather than construction: the constructor is package-private
        // and takes a long positional list, so building one by hand in a test would
        // assert against whatever the signature happens to be today rather than the
        // shape the servlet and JSP depend on.
        for (String getter : List.of("getRowCount", "getClientReported", "getInteractive",
                "getDomReady", "getLoad", "getServer", "getTtfb", "getTransfer",
                "getByBrowser", "getByPageType")) {
            boolean found = java.util.Arrays.stream(ExperienceReport.class.getMethods())
                    .anyMatch(m -> m.getName().equals(getter) && m.getParameterCount() == 0);
            assertTrue(found, () -> "ExperienceReport lost " + getter + "(). The servlet and the "
                    + "JSP both read it, so removing it breaks the admin page at runtime, not at "
                    + "compile time.");
        }
    }
}