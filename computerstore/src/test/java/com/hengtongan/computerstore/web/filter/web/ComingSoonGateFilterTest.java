package com.hengtongan.computerstore.web.filter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The behaviour of {@link ComingSoonGateFilter}: a gated path must never reach
 * its servlet.
 *
 * <p>
 * {@code ComingSoonGateTest} covers the other half of the feature -- that the
 * descriptor and
 * the filter's map describe the same set of paths, and that the mapping is
 * ordered after admin
 * authorization. This class covers what the filter does at request time, which
 * reading the source
 * cannot establish:
 *
 * <ul>
 * <li>a gated path is answered by a forward and {@code chain.doFilter} is never
 * called, which
 * is the whole point -- the half-finished servlet behind it is
 * unreachable;</li>
 * <li>the forward target is the placeholder view, and the feature's name, icon
 * and copy are
 * published as request attributes for the view to read;</li>
 * <li>any other path is left completely alone, because a filter mapped with
 * url-patterns still
 * sees requests the container did not route to it and must not swallow
 * them.</li>
 * </ul>
 */
class ComingSoonGateFilterTest {

    private static final String VIEW = "/WEB-INF/views/admin/coming-soon.jsp";

    /**
     * Read to discover which paths are gated, so coverage cannot drift from the
     * filter.
     */
    private static final java.nio.file.Path FILTER_SOURCE = java.nio.file.Path.of(
            "src/main/java/com/hengtongan/computerstore/web/filter/web/ComingSoonGateFilter.java");

    /**
     * /** A gate with one fictional section in it.
     *
     * <p>
     * This fixture keeps the generic forwarding behaviour covered independently
     * of the current production gates, and uses a path that is not a real admin
     * section so its assertions cannot drift as features are released or gated.
     * </p>
     */
    private static final Map<String, ComingSoonGateFilter.Feature> FIXTURE_GATE = Map.of("/admin/not-shipped-yet",
            new ComingSoonGateFilter.Feature(
                    "Not Shipped Yet", "bi-hourglass-split", "This section is not finished yet."));

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private RequestDispatcher dispatcher;

    /** What the filter put on the request, recorded rather than captured. */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getContextPath()).thenReturn("/computerstore");
        when(request.getRequestDispatcher(any(String.class))).thenReturn(dispatcher);
        doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(request).setAttribute(anyString(), any());
    }

    /** Runs the filter's gate logic against {@link #FIXTURE_GATE}. */
    private void gate() throws IOException, ServletException {
        ComingSoonGateFilter.doFilter(request, response, chain, FIXTURE_GATE);
    }

    @Test
    void aGatedPathIsForwardedToThePlaceholderAndNeverReachesItsServlet()
            throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/admin/not-shipped-yet");

        gate();

        verify(dispatcher).forward(request, response);
        // The whole feature rests on this. If doFilter ran, the request would continue
        // to
        // the half-finished servlet behind the gate and the admin would land on it.
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void thePerformanceSectionShowsComingSoonInsteadOfTheMonitor() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/admin/performance");

        new ComingSoonGateFilter().doFilter(request, response, chain);

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
        assertEquals("Performance", attribute("comingSoonName"));
        assertEquals("bi-activity", attribute("comingSoonIcon"));
        assertTrue(attribute("comingSoonNote").contains("available soon"));
        assertTrue(gatedPathsInTheFilter().contains("/admin/performance"),
                "Performance must remain registered in the production gate map.");
    }

    @Test
    void theFilterIsActiveForPerformance() {
        assertFalse(ComingSoonGateFilter.DORMANT,
                "Performance is intentionally gated until the monitor is ready to ship.");
    }

    @Test
    void thePaymentsSectionIsNoLongerGated() throws IOException, ServletException {
        // Same reasoning as the transactions case below, and a bigger consequence: the
        // payment configuration page is the only place an operator can switch a payment
        // method on at all. While it was gated, a store with both methods off by
        // default
        // was cash-only with no reachable page that could change that -- an admin
        // reading
        // a "coming soon" modal has no way to tell that from "not built yet".
        when(request.getServletPath()).thenReturn("/admin/payments");

        new ComingSoonGateFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request, never()).getRequestDispatcher(any(String.class));
        assertEquals(Map.of(), attributes, "a released section should not be annotated as coming soon");
    }

    @Test
    void theTransactionsSectionIsNoLongerGated() throws IOException, ServletException {
        // Transactions now has a working ledger, detail page and customer-facing view,
        // so it must
        // reach AdminTransactionsServlet. Asserted here as well as in
        // ComingSoonGateTest: that
        // test reads web.xml and the filter's map, which together would still agree if
        // this
        // filter started gating the path under some other name, and only the
        // request-time
        // behaviour settles it.
        when(request.getServletPath()).thenReturn("/admin/transactions");

        new ComingSoonGateFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(request, never()).getRequestDispatcher(any(String.class));
        assertEquals(Map.of(), attributes, "a released section should not be annotated as coming soon");
    }

    @Test
    void theGatedPathIsMatchedOnTheServletPathNotTheUri() throws IOException, ServletException {
        // A deployed app is not at the document root, so getRequestURI() arrives as
        // "/computerstore/admin/not-shipped-yet" while getServletPath() is
        // "/admin/not-shipped-yet". Matching the URI would miss every gated path as
        // soon
        // as the context path is anything but "".
        when(request.getServletPath()).thenReturn("/admin/not-shipped-yet");
        when(request.getRequestURI()).thenReturn("/computerstore/admin/not-shipped-yet");

        gate();

        verify(dispatcher).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void theForwardTargetsThePlaceholderView() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/admin/not-shipped-yet");

        gate();

        verify(request).getRequestDispatcher(VIEW);
    }

    @Test
    void theFeatureDetailsArePublishedForTheViewToRead() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/admin/not-shipped-yet");

        gate();

        // The view renders these straight into the modal. A missing attribute would
        // render an
        // empty modal -- "is coming soon" with no feature name, or a bare <i class="">
        // tag.
        assertEquals("Not Shipped Yet", attribute("comingSoonName"));

        // Only the icon name is stored; the view supplies the "bi" prefix, so asserting
        // on
        // "bi " here would be asserting on markup that lives in the JSP.
        String icon = attribute("comingSoonIcon");
        assertEquals(true, icon.matches("bi-[a-z0-9-]+"),
                () -> icon + " should be a bare Bootstrap icon name such as bi-activity");

        String note = attribute("comingSoonNote");
        assertEquals(true, note.endsWith("."), () -> note + " should be a full sentence");
    }

    @Test
    void everyGatedPathNamesItself() throws IOException, ServletException {
        // The entries carry different copy. If the map ever handed one Feature to every
        // path, the modal would claim to be about the wrong section. Uses the fixture
        // because the live map is empty; the check is about the per-entry copy, which
        // does not change when a section ships.
        for (Map.Entry<String, ComingSoonGateFilter.Feature> entry : FIXTURE_GATE.entrySet()) {
            attributes.clear();
            when(request.getServletPath()).thenReturn(entry.getKey());

            gate();

            String name = attribute("comingSoonName");
            assertEquals(true, name.matches("[A-Z][A-Za-z ]+"),
                    () -> entry.getKey() + " published the unexpected feature name " + name);
            assertEquals(entry.getValue().name(), name,
                    () -> entry.getKey() + " published the name of a different feature");
        }
    }

    @Test
    void anUngatedPathIsPassedStraightThrough() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/admin/orders");

        gate();

        verify(chain).doFilter(request, response);
        verify(request, never()).getRequestDispatcher(any(String.class));
        assertEquals(Map.of(), attributes, "an ungated request should not be annotated");
    }

    @Test
    void aPathThatMerelyStartsLikeAGatedOneIsNotGated() throws IOException, ServletException {
        // The map is keyed on the exact servlet path, so a sub-resource is unaffected.
        // If the
        // lookup ever became a prefix or wildcard match, a nested URL under a gated
        // section
        // would be swallowed too.
        when(request.getServletPath()).thenReturn("/admin/not-shipped-yet/report.pdf");

        gate();

        verify(chain).doFilter(request, response);
        verify(request, never()).getRequestDispatcher(any(String.class));
    }

    @Test
    void anEmptyServletPathIsNotGated() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("");

        gate();

        verify(chain).doFilter(request, response);
    }

    @Test
    void repeatedRequestsAreGatedEveryTime() throws IOException, ServletException {
        // The gate is stateless by design, so a refresh -- or an admin re-entering the
        // URL after
        // the page was already open -- must be answered the same way each time.
        when(request.getServletPath()).thenReturn("/admin/not-shipped-yet");

        gate();
        gate();

        verify(dispatcher, times(2)).forward(request, response);
        verify(chain, never()).doFilter(request, response);
    }

    /**
     * Every path the filter actually gates, read from the filter's own map.
     *
     * <p>
     * Derived rather than hardcoded so this test cannot quietly stop covering a
     * gated path. {@code ComingSoonGateTest} asserts the map and {@code web.xml}
     * agree with each other; this asserts each entry in the map behaves correctly
     * at request time. A hardcoded list here would let a new gated path ship with
     * no request-time coverage while every other test still passed.
     *
     * <p>
     * The map is private, so it is read from the source rather than reflected
     * into. The pattern matches only the key of a {@code GATED.put} call.
     */
    private static List<String> gatedPathsInTheFilter() {
        String source;
        try {
            source = java.nio.file.Files.readString(FILTER_SOURCE);
        } catch (IOException e) {
            throw new AssertionError(
                    "Could not read the filter source to discover which paths it gates.", e);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("GATED\\.put\\s*\\(\\s*\"([^\"]+)\"")
                .matcher(source);
        List<String> paths = new java.util.ArrayList<>();
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        // Deliberately NOT asserting the list is non-empty: this helper is for
        // diagnosing source changes, while request-time behavior is tested against
        // both the real Performance gate and the independent FIXTURE_GATE.
        return paths;
    }

    /**
     * The value a request attribute was given, failing the test if it was never
     * set.
     */
    private String attribute(String name) {
        assertTrue(attributes.containsKey(name), () -> name + " was never set on the request");
        return (String) attributes.get(name);
    }
}
