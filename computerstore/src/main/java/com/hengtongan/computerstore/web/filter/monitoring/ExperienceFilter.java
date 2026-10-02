package com.hengtongan.computerstore.web.filter.monitoring;

import com.hengtongan.computerstore.infrastructure.monitoring.MetricsCollector;
import com.hengtongan.computerstore.infrastructure.monitoring.PageType;
import com.hengtongan.computerstore.infrastructure.monitoring.UserAgentClassifier;
import com.hengtongan.computerstore.util.web.RequestUtil;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Labels each page request with the page type, and records how long the server
 * itself spent on it.
 *
 * <h2>Why a request attribute and not a response header</h2>
 *
 * The page type has to reach the browser, because the browser is the only party
 * that knows when the page became usable, so the browser is the one that reports
 * the measurement -- and it needs to know which page it is reporting about.
 *
 * <p>The obvious mechanism, a response header that {@code rum.js} reads, does not
 * work. The Navigation Timing API exposes timings and a status code, not response
 * headers, and there is no synchronous read of the document response available to
 * a deferred script. A header set here would be correct and permanently invisible.
 * The only things a page can hand to a script are its own markup, cookies, and the
 * storage APIs, so the label travels as a request attribute that
 * {@code header.jspf} renders into the page.</p>
 *
 * <h2>Why the server timing is not sent to the browser at all</h2>
 *
 * It cannot be. How long the chain took is only known once the chain has
 * finished, by which point the response is already being written, so there is no
 * place left to put the number that the script could read. An earlier version of
 * this filter tried to publish it in a header for the same reason it publishes
 * the page type there was removed: it looked like it worked and produced nothing.
 *
 * <p>So the server's own elapsed time goes to {@link MetricsCollector} instead,
 * where it lives alongside the other JVM read metrics this page already shows,
 * and the browser reports the wait it actually experienced -- which is the number
 * that matters anyway. {@code rum.js} measures the server think time itself from
 * {@code responseStart - requestStart}. The two are genuinely different
 * measurements and are labelled as such rather than merged into one figure that
 * means neither.</p>
 *
 * <h2>Why nothing is written to the database here</h2>
 *
 * A request whose script never ran -- a bot, a blocked asset, a customer with
 * JavaScript disabled -- has no Experienced Page Time at all. Writing a server row
 * for it would put an unpaired server number into a report about what customers
 * experienced, where it would read as a very fast page. Samples are written only
 * by {@code ExperienceBeaconServlet}, so a page view produces one complete sample
 * or none at all.
 */
public class ExperienceFilter implements Filter {

    /** Request attribute holding the normalised page type. Read by {@code header.jspf}. */
    public static final String ATTR_PAGE_TYPE = "pageExperienceType";

    /** MetricsCollector series for server time spent inside the chain. */
    public static final String METRIC_SERVER_MS = "page.server.ms";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest request)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String pageType = PageType.of(request);
        if (pageType == null || RequestUtil.isStaticOrStream(request)) {
            // Not a page view, or a stream this filter must not interfere with.
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        request.setAttribute(ATTR_PAGE_TYPE, pageType);

        // Bots are timed but not labelled: they have no experience to report, and
        // letting a crawler's fast responses into the server series would make the
        // server look quicker than it is for people.
        boolean human = !UserAgentClassifier.isAutomatedClient(request.getHeader("User-Agent"));

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(servletRequest, servletResponse);
        } finally {
            // Measured in a finally block on purpose: a page that errors still cost
            // time, and an operator chasing a slow page wants the failures in the
            // distribution rather than a flattering subset of the successes.
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (human) {
                MetricsCollector.recordHistogram(METRIC_SERVER_MS, elapsedMs);
            }
        }
        // The response is deliberately never touched. Wrapping it to observe the
        // first byte written would mean wrapping ServletOutputStream, which risks
        // the async and SSE paths CompressionFilter already goes out of its way to
        // avoid -- and the browser measures its own wait anyway.
    }
}