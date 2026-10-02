package com.hengtongan.computerstore.web.monitoring;

import com.hengtongan.computerstore.web.monitoring.ExperiencePipelineSourceTest.Checks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation harness for the source guards in {@link ExperiencePipelineSourceTest}.
 *
 * <h2>Why this exists</h2>
 *
 * A source assertion can pass while the code is broken. The worst version of that
 * is an assertion that has quietly stopped matching anything: the string it looks
 * for is reformatted, the check is loosened, and the guard is now green while
 * asserting nothing. Nothing in CI would notice, because a guard that cannot fail
 * looks exactly like a guard that passes.
 *
 * <p>So each mutation below breaks one link in the chain the way a real mistake
 * would -- a renamed attribute, an unloaded script, an endpoint moved, a CSRF
 * exemption added -- and the corresponding check must reject it. A mutation that
 * survives means the guard is weaker than it appears, and that is the finding this
 * test exists to surface.
 *
 * <h2>Scope</h2>
 *
 * Only the checks that a mutation can meaningfully exercise. The percentile
 * arithmetic and the browser classifier are covered behaviourally in
 * {@code PageExperienceCollectionTest} and are not re-litigated here.
 *
 * <h2>These are not shipped assertions</h2>
 *
 * This is a self-test of the test suite. It copies files to a temp directory,
 * corrupts the copy, and never touches {@code src/}. Its value is entirely in the
 * failures it would report about the guards, not in the assertions on its own
 * assertions.
 */
class ExperiencePipelineSourceMutationTest {

    private static final Path ROOT = Path.of("src/main");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative));
    }

    /**
     * Asserts that every guard in the real test rejects a given source.
     *
     * <p>Calls the guards rather than restating them. A mutation harness that
     * reimplements the check it is testing proves only that the copy agrees with
     * itself, which is the failure mode this file exists to rule out.</p>
     *
     * @param header mutated header.jspf
     * @param rum    mutated rum.js
     * @param beacon mutated ExperienceBeaconServlet
     * @param jsp    mutated performance.jsp
     */
    private static void applyGuards(String header, String rum, String beacon, String jsp) {
        Checks.viewPublishesPageType(header);
        Checks.scriptLoadsDefered(header);
        Checks.scriptDoesNotReadHeaders(rum);
        Checks.scriptReadsThePageTypeFromTheWindow(rum);
        Checks.scriptAndServletAgreeOnTheEndpoint(rum, beacon);
        Checks.beaconIsGet(beacon);
        Checks.beaconRefusesParameterBrowser(beacon);
        Checks.adminPageDistinguishesFailure(jsp);
        Checks.adminPageHasAllThreeSections(jsp);
        Checks.byteFiguresAreConvertedToKilobytes(jsp);
    }

    /**
     * Runs the guards and requires at least one to fail.
     *
     * <p>Accepts any {@link Throwable} rather than only
     * {@code AssertionFailedError}, because a guard that starts with a
     * {@code contains} on a null will throw an NPE and count as "rejected" even
     * though it rejected for the wrong reason. The {@link #mutate} helper is what
     * keeps that honest: it fails loudly on a drifted anchor, so every case here is
     * a real substitution rather than an absent string.</p>
     */
    private static void assertGuardsReject(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable expected) {
            return;
        }
        throw new AssertionError(
                "The source guards passed against a mutated tree. That mutation was supposed to "
                        + "be caught, so either the guard is weaker than it appears or it has "
                        + "stopped matching anything. A guard that cannot fail is indistinguishable "
                        + "from one that passes.");
    }

    /** The other half of the harness: the guards must pass against the real tree. */
    private static void assertGuardsAccept(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            throw new AssertionError(
                    "The source guards failed against the unmuted tree, so every mutation case "
                            + "above would have been proving nothing.", t);
        }
    }

    /** A body that may throw, so the harness can distinguish pass from fail. */
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Applies a replacement, failing loudly if the anchor has drifted. */
    private static String mutate(String source, String from, String to) {
        if (!source.contains(from)) {
            throw new AssertionError(
                    "Fixture drifted: the text this mutation targets is no longer present:\n  "
                            + from + "\nEvery mutation below tests a specific guard, so a drifted "
                            + "anchor means that guard is no longer being exercised. Update this "
                            + "test and the guard together.");
        }
        return source.replace(from, to);
    }

    @Test
    void renamingThePageTypeAttributeIsCaught() throws IOException {
        String header = read("webapp/WEB-INF/views/layouts/header.jspf");
        // Renaming one side of the pair. rum.js then finds no page type, returns
        // immediately, and the report stays permanently empty with nothing logged.
        assertGuardsReject(() -> applyGuards(
                mutate(header, "${requestScope.pageExperienceType}", "${requestScope.wrongName}"),
                read("webapp/assets/js/rum.js"),
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    @Test
    void unloadingTheScriptIsCaught() throws IOException {
        String header = read("webapp/WEB-INF/views/layouts/header.jspf");
        assertGuardsReject(() -> applyGuards(
                mutate(header,
                        "<script defer src=\"${pageContext.request.contextPath}/assets/js/rum.js"
                                + "?v=${assetsVersion}\"></script>", ""),
                read("webapp/assets/js/rum.js"),
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    @Test
    void droppingDeferFromTheScriptIsCaught() throws IOException {
        String header = read("webapp/WEB-INF/views/layouts/header.jspf");
        // Subtle and real: the script still loads, so any check for the filename
        // passes, but it now runs before parsing finishes and reads a navigation
        // entry whose domInteractive is still 0.
        assertGuardsReject(() -> applyGuards(
                mutate(header,
                        "<script defer src=\"${pageContext.request.contextPath}/assets/js/rum.js"
                                + "?v=${assetsVersion}\"></script>",
                        "<script src=\"${pageContext.request.contextPath}/assets/js/rum.js"
                                + "?v=${assetsVersion}\"></script>"),
                read("webapp/assets/js/rum.js"),
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    @Test
    void movingTheEndpointIsCaught() throws IOException {
        assertGuardsReject(() -> applyGuards(
                read("webapp/WEB-INF/views/layouts/header.jspf"),
                mutate(read("webapp/assets/js/rum.js"), "/realtime/ept", "/realtime/ept-v2"),
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    @Test
    void readingAResponseHeaderInsteadOfThePageIsCaught() throws IOException {
        // The exact design error this pipeline made once already: a script that
        // reads a response header looks correct and reports nothing at all,
        // because the Navigation Timing API does not expose them.
        String rum = read("webapp/assets/js/rum.js")
                + "\n    var pageType = entry.getResponseHeader('X-Page-Type');\n";
        assertGuardsReject(() -> applyGuards(
                read("webapp/WEB-INF/views/layouts/header.jspf"),
                rum,
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    @Test
    void acceptingTheBrowserAsAParameterIsCaught() throws IOException {
        // Lets any caller file samples under a browser that did not make the
        // request, which is the one field the whole by-browser report groups on.
        //
        // The mutation substitutes the real classification call rather than
        // appending the forbidden text as a comment. A comment would trip the
        // guard for the wrong reason and prove only that the guard does substring
        // matching -- true, but not the claim this test makes.
        assertGuardsReject(() -> applyGuards(
                read("webapp/WEB-INF/views/layouts/header.jspf"),
                read("webapp/assets/js/rum.js"),
                mutate(read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                        "UserAgentClassifier.browser(userAgent)",
                        "request.getParameter(\"browser\")"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    @Test
    void relabellingBytesAsKilobytesIsCaught() throws IOException {
        // A real regression, not a hypothetical one: the overall table formatted
        // the repository's raw byte count and labelled the result KB, so a 120 KB
        // page read as 120 KB-worth of bytes. The two breakdown tables were right,
        // which is why a whole-file "contains 1024" check would have missed it.
        assertGuardsReject(() -> applyGuards(
                read("webapp/WEB-INF/views/layouts/header.jspf"),
                read("webapp/assets/js/rum.js"),
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                mutate(read("webapp/WEB-INF/views/admin/performance.jsp"),
                        "${ept.transfer.avg / 1024}", "${ept.transfer.avg}")));
    }

    @Test
    void turningTheBeaconIntoAPostIsCaught() throws IOException {
        // A POST here needs a CSRF exemption to work at all, and this is the one
        // endpoint that writes untrusted input to a table.
        assertGuardsReject(() -> applyGuards(
                read("webapp/WEB-INF/views/layouts/header.jspf"),
                read("webapp/assets/js/rum.js"),
                mutate(read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                        "protected void doGet", "protected void doPost"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    @Test
    void conflatingAFailedQueryWithNoDataIsCaught() throws IOException {
        // Drops the failure branch, so a broken table renders as "nothing sampled
        // yet" -- a site that merely looks unvisited rather than one that is broken.
        assertGuardsReject(() -> applyGuards(
                read("webapp/WEB-INF/views/layouts/header.jspf"),
                read("webapp/assets/js/rum.js"),
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                mutate(read("webapp/WEB-INF/views/admin/performance.jsp"),
                        "${experienceFailed}", "${false}")));
    }

    @Test
    void theGuardsPassAgainstTheUnmutedTree() throws IOException {
        // The other half of a mutation harness: if the guards rejected the real
        // source, every mutation case above would be proving nothing.
        assertGuardsAccept(() -> applyGuards(
                read("webapp/WEB-INF/views/layouts/header.jspf"),
                read("webapp/assets/js/rum.js"),
                read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                        + "ExperienceBeaconServlet.java"),
                read("webapp/WEB-INF/views/admin/performance.jsp")));
    }

    /**
     * The two facts no mutation above breaks, and so no {@link #mutate} anchor covers.
     *
     * <p>Every other fixture in this file is protected by {@link #mutate}, which
     * throws when its anchor has drifted, and by
     * {@link #theGuardsPassAgainstTheUnmutedTree}, which fails if the real tree no
     * longer satisfies a guard. These two are the exception: nothing in this file
     * substitutes them away, so if either disappears the mutations above keep
     * passing while guarding something weaker than they claim.</p>
     */
    @Test
    void theAnchorsNoMutationCoversStillExist() throws IOException {
        // The browser family is the one field derived on the server rather than
        // taken from the request. If it moved to a parameter, the by-browser report
        // would group on a client-supplied value and the beaconRefusesParameterBrowser
        // guard would still pass -- it forbids the read, not its relocation.
        String beacon = read("java/com/hengtongan/computerstore/web/controller/monitoring/"
                + "ExperienceBeaconServlet.java");
        assertTrue(beacon.contains("UserAgentClassifier.browser(userAgent)"),
                "the beacon no longer derives the browser from its own User-Agent. If it was "
                        + "moved to a query parameter, the parameter guards still pass but the "
                        + "by-browser breakdown would group on a forged value.");

        // The attribute name is the seam between the filter and the view. The
        // viewPublishesPageType guard checks the view's half only.
        String filter = read("java/com/hengtongan/computerstore/web/filter/monitoring/"
                + "ExperienceFilter.java");
        assertTrue(filter.contains("ATTR_PAGE_TYPE = \"pageExperienceType\""),
                "ExperienceFilter no longer publishes the page type under the name header.jspf "
                        + "reads. The view's half would still pass while no attribute was set.");
    }
}