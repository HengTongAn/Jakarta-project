package com.hengtongan.computerstore.web.monitoring;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-level guards on the EPT pipeline's wiring.
 *
 * <h2>Why this reads files instead of calling code</h2>
 *
 * The claims here are all about connections between pieces that only meet in a
 * servlet container: that a filter sets the attribute the view renders, that the
 * view renders the variable the script reads, that the script calls the endpoint
 * the servlet serves. None of it is reachable from a unit test -- there is no
 * filter chain, no JSP compilation and no browser here. The project deliberately
 * keeps {@code mvn test} free of a container dependency, so reading the source is
 * the only automated check available.
 *
 * <p>These are weaker than behavioural tests and are labelled as such. A text
 * assertion can be satisfied by a matching string in a comment, and it cannot
 * prove the two halves agree at runtime. What actually establishes the chain is
 * a deployed browser hitting the beacon and a row appearing in the table, which
 * is verified separately against the live database.</p>
 *
 * <h2>What a break here looks like</h2>
 *
 * Every check corresponds to a silent failure. If the view stops rendering the
 * page type, {@code rum.js} finds no {@code window.__EPT_PT} and returns
 * immediately: no error, no beacon, no samples, and the admin page reports
 * "nothing sampled yet" forever.
 */
class ExperiencePipelineSourceTest {

    private static final Path HEADER = Path.of(
            "src/main/webapp/WEB-INF/views/layouts/header.jspf");
    private static final Path RUM_JS = Path.of("src/main/webapp/assets/js/rum.js");
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path FILTER = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/filter/monitoring/ExperienceFilter.java");
    private static final Path BEACON = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/controller/monitoring/"
                    + "ExperienceBeaconServlet.java");
    private static final Path PERF_JSP = Path.of(
            "src/main/webapp/WEB-INF/views/admin/performance.jsp");

    private static String read(Path path) throws IOException {
        return Files.readString(path);
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

    /**
     * The guard predicates, as plain methods.
     *
     * <p>Factored out so {@code ExperiencePipelineSourceMutationTest} can call the
     * real checks rather than restating them. A mutation harness that reimplements
     * the check it is testing proves only that its copy agrees with itself, which
     * is the exact failure this pairing is meant to rule out.</p>
     */
    static final class Checks {
        private Checks() {
        }

        static void viewPublishesPageType(String header) {
            assertTrue(header.contains(
                            "<script>window.__EPT_PT = '${requestScope.pageExperienceType}';</script>"),
                    "header.jspf must render window.__EPT_PT from the request attribute. If this "
                            + "is missing, rum.js finds no page type and returns immediately: no "
                            + "beacon, no samples, and the admin page reads 'nothing sampled yet' "
                            + "forever.");
        }

        static void scriptLoadsDefered(String header) {
            /* Anchored on the script tag, not on the bare filename. header.jspf
             * mentions rum.js twice: once in the appAssets array that feeds the
             * cache-busting version, and once in the <script> element that loads
             * it. A first-occurrence search finds the array entry and would check
             * that line for defer, which is nonsense -- and, when the array was
             * added, reported a passing script as broken. */
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<script([^>]*)src=\"[^\"]*/assets/js/rum\\.js").matcher(header);
            assertTrue(m.find(),
                    "rum.js is never loaded by a <script> element, so no page timing is "
                            + "ever reported. If the tag was removed the report stays empty "
                            + "forever with nothing logged.");
            assertTrue(m.group(1).contains("defer"),
                    () -> "rum.js is loaded without defer: <script" + m.group(1) + " ...>. Run "
                            + "synchronously it would read the navigation entry before parsing "
                            + "finished, when domInteractive is still 0, and silently record a "
                            + "different measurement.");
        }

        static void scriptReadsThePageTypeFromTheWindow(String rum) {
            assertTrue(rum.contains("window.__EPT_PT"),
                    "rum.js must read the page type from the page, since response headers are "
                            + "not readable from the Navigation Timing API");
        }

        static void scriptDoesNotReadHeaders(String rum) {
            assertFalse(rum.contains("getResponseHeader"),
                    "rum.js must not try to read a response header. It cannot: there is no "
                            + "synchronous read of the document response available to a deferred "
                            + "script, so this would silently do nothing.");
        }

        static void scriptAndServletAgreeOnTheEndpoint(String rum, String beacon) {
            // Both halves name the same path. Renaming one alone is the most likely
            // real break here -- the script keeps loading and the servlet keeps
            // serving, and every sample is silently discarded with nothing logged.
            //
            // Quoted in the pattern on purpose: rum.js mentions the path in prose in
            // its header comment, and matching that would make the guard pass on the
            // comment while the two call sites disagree with the servlet.
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("'(/[^'?]+)").matcher(rum);
            boolean found = false;
            while (m.find()) {
                found = true;
                String path = m.group(1);
                assertTrue(beacon.contains("@WebServlet(urlPatterns = \"" + path + "\")"),
                        () -> "rum.js sends to " + path + " but the servlet is not mapped there. "
                                + "Every sample would be discarded silently.");
            }
            assertTrue(found,
                    "could not find a quoted request path in rum.js, so there is nothing to "
                            + "compare the servlet's mapping against");
        }

        static void beaconIsGet(String beacon) {
            assertTrue(beacon.contains("protected void doGet"),
                    "the beacon must be a GET endpoint. A POST would need a CSRF exemption to "
                            + "work, and this is the one endpoint that writes untrusted input to "
                            + "a table.");
        }

        static void beaconRefusesParameterBrowser(String beacon) {
            assertFalse(beacon.contains("getParameter(\"browser\")"),
                    "the browser must not be read from a parameter, or the field that groups "
                            + "the whole report can be forged by naming a browser that did not "
                            + "make the request");
            assertFalse(beacon.contains("getParameter(\"ua\")"),
                    "the User-Agent must not be accepted as a parameter");
        }

        static void adminPageDistinguishesFailure(String jsp) {
            assertTrue(jsp.contains("${experienceFailed}"),
                    "the JSP must handle the failed-query case distinctly from the empty case");
            assertTrue(jsp.contains("Could not read page timing data"),
                    "a failed query must say so rather than rendering an empty table");
            assertTrue(jsp.contains("Nothing sampled yet"),
                    "an empty result must say so rather than implying success");
        }

        static void adminPageHasAllThreeSections(String jsp) {
            assertTrue(jsp.contains("EPT overall"), "the overall EPT section is missing");
            assertTrue(jsp.contains("Performance by browser"), "the by-browser section is missing");
            assertTrue(jsp.contains("Performance by page type"),
                    "the by-page-type section is missing");
            assertTrue(jsp.contains("${ept.byBrowser}"),
                    "the by-browser table is not bound to data");
            assertTrue(jsp.contains("${ept.byPageType}"),
                    "the by-page-type table is not bound to data");
        }

        /**
         * Every byte figure on the page is divided before it is labelled KB.
         *
         * <p>This is the one guard here that exists because a real bug got through.
         * The repository stores and returns {@code transfer_bytes} verbatim, and the
         * overall table was formatting that number and labelling the result "KB" --
         * so a 120 KB page was displayed as 120 <em>bytes</em>-worth of KB, roughly a
         * thousandfold understatement. No behavioural test could have caught it: the
         * repository is correct, the JSP is the thing that is wrong, and nothing runs
         * the JSP.</p>
         *
         * <p>The check is per-expression rather than a whole-file {@code contains} for
         * the same reason the other guards here are narrow: the page also contains
         * correctly converted figures, so "does the file mention 1024" would pass on
         * the good ones while the broken one stayed broken.</p>
         */
        static void byteFiguresAreConvertedToKilobytes(String jsp) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\$\\{((?:ept\\.)?(?:transfer\\.(?:avg|max)|b\\.averageBytes))([^}]*)\\}")
                    .matcher(jsp);
            int checked = 0;
            while (m.find()) {
                checked++;
                String tail = m.group(2);
                assertTrue(tail.contains("/ 1024"),
                        () -> m.group(1) + " is rendered as " + m.group(0)
                                + " and labelled KB, but the repository holds raw bytes. "
                                + "Divide by 1024 here, or the page reports every page as "
                                + "roughly a thousandth of its real weight.");
            }
            assertTrue(checked > 0,
                    "no transfer-byte expression was found in performance.jsp, so this "
                            + "guard is checking nothing. If the page weight rows were "
                            + "renamed or removed, update this with them.");
        }
    }

    // ------------------------------------------------------------------
    // The chain: filter -> view -> script -> endpoint -> repository.
    // ------------------------------------------------------------------

    @Test
    void theViewPublishesThePageTypeTheFilterSets() throws IOException {
        // The attribute name must appear on both sides. If either is renamed alone,
        // rum.js gets no page type and returns without sending anything -- silently.
        String filter = read(FILTER);
        assertTrue(filter.contains("ATTR_PAGE_TYPE = \"pageExperienceType\""),
                "ExperienceFilter must publish the page type under the name header.jspf reads");
        Checks.viewPublishesPageType(read(HEADER));
    }

    @Test
    void theFilterIsMappedForEveryRequest() throws IOException {
        // Declared but unmapped is the same as absent: the attribute is never set,
        // and the failure is invisible because nothing reports an error.
        String webXml = read(WEB_XML);
        assertTrue(webXml.contains("<filter-name>ExperienceFilter</filter-name>"),
                "ExperienceFilter is not declared in web.xml, so it never runs");
        assertTrue(webXml.contains("ExperienceFilter</filter-class>"),
                "the filter class must be named in web.xml");
        assertTrue(webXml.contains("<url-pattern>/*</url-pattern>"),
                "the filter must be mapped to /* or it never runs. A page-type label only has to "
                        + "be right for the pages that report timings, but missing them entirely "
                        + "silently empties the table.");
    }

    @Test
    void theFilterRunsAfterTheStaticResourceCacheSoItSeesRealPages() throws IOException {
        // Ordering is not load-bearing for correctness here -- the filter inspects the
        // request, not the response -- but it is asserted so that moving the filter
        // to the very front of the chain, ahead of encoding, is a deliberate choice
        // rather than an accident in a diff.
        String webXml = read(WEB_XML);
        int filter = webXml.indexOf("<filter-name>ExperienceFilter</filter-name>");
        int staticCache = webXml.indexOf("<filter-name>StaticResourceCacheFilter</filter-name>");
        assertTrue(filter > 0 && staticCache > 0,
                "both filters must be present in web.xml for the ordering to mean anything");
        assertTrue(filter > staticCache,
                "ExperienceFilter should be declared after StaticResourceCacheFilter");
    }

    @Test
    void theScriptLoadsOnEveryPageThatReportsTimings() throws IOException {
        Checks.scriptLoadsDefered(read(HEADER));
    }

    @Test
    void theScriptSendsToTheEndpointTheServletServes() throws IOException {
        String rum = read(RUM_JS);
        assertTrue(rum.contains("/realtime/ept"),
                "rum.js posts to /realtime/ept. If the path changed alone, samples are silently "
                        + "discarded.");
        Checks.scriptAndServletAgreeOnTheEndpoint(rum, read(BEACON));
    }

    @Test
    void theScriptReadsThePageTypeFromTheWindowNotFromAHeader() throws IOException {
        // The design reason this matters: the Navigation Timing API does not expose
        // response headers. A version that tried to read one would appear correct,
        // ship, and report nothing at all.
        String rum = read(RUM_JS);
        Checks.scriptReadsThePageTypeFromTheWindow(rum);
        Checks.scriptDoesNotReadHeaders(rum);
    }

    @Test
    void theScriptHonoursDoNotTrack() throws IOException {
        String rum = read(RUM_JS);
        assertTrue(rum.contains("doNotTrack"),
                "rum.js must check Do Not Track. It stores no IP, user id or full URL, but a "
                        + "person who asks not to be tracked should not have to know that argument "
                        + "to be believed.");
    }

    @Test
    void theBeaconIsNotExemptFromCsrfProtection() throws IOException {
        // The endpoint takes untrusted input into a table. A CSRF hole there is a
        // forged-metrics hole, so it must be a GET (which CSRFProtectionFilter does
        // not validate) rather than a POST needing an exemption.
        String webXml = read(WEB_XML);
        int csrf = webXml.indexOf("<filter-name>CSRFProtectionFilter</filter-name>");
        assertTrue(csrf > 0, "CSRFProtectionFilter must be mapped");

        String beacon = read(BEACON);
        Checks.beaconIsGet(beacon);

        // The reason a GET is the right choice: the filter only validates POSTs, so
        // a GET beacon needs no exemption and therefore no hole. Asserting that
        // directly is stronger than asserting the beacon is a GET -- it also catches
        // someone later widening the CSRF filter to cover GETs, which would break
        // every real beacon in the browser and might tempt an exemption.
        String csrfSource = read(Path.of("src/main/java/com/hengtongan/computerstore/web/"
                + "filter/security/CSRFProtectionFilter.java"));
        assertTrue(csrfSource.contains("!\"POST\".equalsIgnoreCase(request.getMethod())"),
                "CSRFProtectionFilter no longer skips non-POST requests. The beacon depends on "
                        + "that: it is a GET precisely so it needs no exemption, and extending "
                        + "validation to GETs would break every browser beacon in production "
                        + "while creating pressure to add one.");

        // The exemption list must stay empty. This is the assertion that carries the
        // security argument: the beacon is safe because nothing is excluded and GETs
        // are never checked, so there is no hole to widen. A future /realtime/ept entry
        // would look identical to a harmless one and weaken a table write.
        String excluded = between(csrfSource, "EXCLUDED_PATHS =", ";");
        assertTrue(excluded.contains("new HashSet<>()"),
                () -> "CSRFProtectionFilter's EXCLUDED_PATHS is no longer empty: " + excluded
                        + ". The beacon was designed to need no exemption, so an entry here is "
                        + "either unnecessary or a CSRF hole on an unauthenticated table write.");
        assertFalse(csrfSource.contains("/realtime/ept"),
                "the beacon path must not appear in CSRFProtectionFilter at all");
    }

    @Test
    void theBeaconNeverStoresWhatItCannotValidate() throws IOException {
        // Every column is client-supplied except the browser family, which is derived
        // from the request's own User-Agent rather than taken from a parameter.
        String beacon = read(BEACON);
        assertTrue(beacon.contains("UserAgentClassifier.browser(userAgent)"),
                "the browser must be classified from the request's own User-Agent, never taken "
                        + "from a query parameter -- otherwise the field that groups the whole "
                        + "report can be forged by naming a browser that did not make the request");
        Checks.beaconRefusesParameterBrowser(beacon);
    }

    @Test
    void theScriptSendsOnlyBoundedNumericFields() throws IOException {
        // No free text crosses into a SQL parameter from the beacon. The only string
        // is the page type, which is pattern-validated server-side.
        String rum = read(RUM_JS);
        assertFalse(rum.contains("document.cookie"),
                "rum.js must not send cookies or session identifiers");
        assertFalse(rum.contains("localStorage"),
                "rum.js must not read storage");
        assertFalse(rum.contains("sessionStorage"),
                "rum.js must not read session storage");
    }

    @Test
    void theSampleRateIsServerControlled() throws IOException {
        // Sampling in the browser would make the reported volume depend on client
        // behaviour. The rate is a deployment property so the count on the admin page
        // means what it says.
        String beacon = read(BEACON);
        assertTrue(beacon.contains("computerstore.rum.sampleRate"),
                "the sample rate must be a server-side system property");
    }

    // ------------------------------------------------------------------
    // The admin page: what it claims about itself.
    // ------------------------------------------------------------------

    @Test
    void theAdminPageStatesThatItsCountsAreASampleNotTotalTraffic() throws IOException {
        // The number is an estimate of volume. An admin reading "12,400 page loads"
        // as a traffic figure would draw wrong conclusions, so the caveat is on the
        // page rather than only in the code.
        String jsp = read(PERF_JSP);
        assertTrue(jsp.contains("sampled page loads"),
                "the EPT section must say the figures come from sampled page loads");
        assertTrue(jsp.contains("sampleRate"),
                "the page must show the configured sample rate");
    }

    @Test
    void theAdminPageDistinguishesAFailedQueryFromNoData() throws IOException {
        // Both render as an empty card if conflated. Only one of them is healthy, so
        // a broken table must not look like a site nobody visits.
        Checks.adminPageDistinguishesFailure(read(PERF_JSP));
    }

    @Test
    void theAdminPageShowsAllThreeRequestedBreakdowns() throws IOException {
        // EPT overall, by browser, and by page type. Named so a failure says which.
        Checks.adminPageHasAllThreeSections(read(PERF_JSP));
    }

    @Test
    void theAdminPageDoesNotRelabelBytesAsKilobytes() throws IOException {
        Checks.byteFiguresAreConvertedToKilobytes(read(PERF_JSP));
    }

    @Test
    void theAdminPageExplainsTheServerVersusClientSplit() throws IOException {
        // Without this the two timing rows are just numbers, and the reader cannot
        // act on them. The split is the whole diagnostic value.
        String jsp = read(PERF_JSP);
        assertTrue(jsp.contains("Server think time"),
                "the page must label the server's share distinctly from the total");
        assertTrue(jsp.contains("Your app&#39;s share") || jsp.contains("your app's share"),
                "the page must say which side of the split belongs to the application");
    }

    }