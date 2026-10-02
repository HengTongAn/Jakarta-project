package com.hengtongan.computerstore.web.controller.monitoring;

import com.hengtongan.computerstore.core.config.AppContext;
import com.hengtongan.computerstore.core.domain.entity.PageExperienceSample;
import com.hengtongan.computerstore.infrastructure.monitoring.UserAgentClassifier;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Receives one Experienced Page Time sample per sampled page view.
 *
 * <h2>Why this is a GET and not a POST</h2>
 *
 * The browser is reporting something it measured, not asking the server to do
 * anything, and it does so while the page is still loading. A {@code POST} here
 * would have to satisfy {@code CSRFProtectionFilter}, which validates every POST
 * and exempts nothing. Exempting this endpoint would be a real weakening: it is
 * the one place where untrusted input reaches a table, and a CSRF hole in an
 * analytics endpoint is a forged-metrics hole for anyone who finds it.
 *
 * <p>A GET avoids that entirely, is not subject to either rate limiter (both skip
 * non-POST), and survives the page unloading. The price is that the sample lands in
 * the access log; it carries no personal data, so that is a fair trade.</p>
 *
 * <h2>Everything in the query string is treated as hostile</h2>
 *
 * These values are client-supplied, so none of them is trusted. {@code pt} must look
 * like a route template and is length-capped; the timings must parse as bounded
 * integers or the sample is dropped; and the browser is classified here from the
 * request's own User-Agent rather than taken from a parameter, so the one field
 * that groups the report cannot be forged by naming a browser that did not make the
 * request.
 */
@WebServlet(urlPatterns = "/realtime/ept")
public class ExperienceBeaconServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(ExperienceBeaconServlet.class.getName());

    /** Route templates only: lowercase path characters, optional :detail. */
    private static final Pattern PAGE_TYPE = Pattern.compile("^[a-z0-9][a-z0-9/_:-]{0,63}$");

    /** Reject absurd timings rather than storing them; 10 minutes is far past any real page. */
    private static final int MAX_MS = 600_000;
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    /** One in every N page views is stored. See {@link #sampleRate()}. */
    private static final int SAMPLE_RATE = Integer.getInteger("computerstore.rum.sampleRate", 10);

    /**
     * Purge roughly once per this many inserts.
     *
     * <p>Retention with no scheduler: the table is only written to by this servlet,
     * so rolling a counter forward here deletes old rows often enough without a
     * background thread that has to be started, stopped and reasoned about.</p>
     */
    private static final int PURGE_EVERY = 100;
    private final AtomicInteger sincePurge = new AtomicInteger();

    public static int sampleRate() {
        return Math.max(1, SAMPLE_RATE);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Nothing to render and nothing to confirm. A body would only tempt a
        // caller into treating the response as meaningful; the browser discards it.
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        response.setContentLength(0);
        // This endpoint must never be measured as a page view of its own, or the
        // report would contain the cost of collecting the report.
        response.setHeader("Cache-Control", "no-store");

        String userAgent = request.getHeader("User-Agent");
        if (UserAgentClassifier.isAutomatedClient(userAgent)) {
            return;
        }
        if (!isSameOrigin(request)) {
            return;
        }
        // Sampled here rather than in the browser so the rate is a property of the
        // deployment and cannot be turned off or up by the client.
        if (ThreadLocalRandom.current().nextInt(sampleRate()) != 0) {
            return;
        }

        String pageType = request.getParameter("pt");
        if (pageType == null || !PAGE_TYPE.matcher(pageType).matches()) {
            return;
        }

        int interactive = ms(request, "i");
        int domReady = ms(request, "d");
        int load = ms(request, "l");
        int ttfb = ms(request, "b");
        int server = ms(request, "s");
        if (interactive < 0 && domReady < 0 && load < 0 && ttfb < 0) {
            // No browser timing at all. Storing the row would contribute a server
            // figure to a report about experienced page time, which is the exact
            // conflation this feature is meant to avoid.
            return;
        }

        PageExperienceSample sample =
                new PageExperienceSample(pageType, UserAgentClassifier.browser(userAgent),
                        UserAgentClassifier.device(userAgent));
        sample.setInteractiveMs(interactive);
        sample.setDomReadyMs(domReady);
        sample.setLoadMs(load);
        sample.setTtfbMs(ttfb);
        sample.setServerMs(server);
        sample.setTransferBytes(bytes(request));

        try {
            AppContext.get().pageExperienceService().record(sample);
            maybePurge();
        } catch (RuntimeException e) {
            // A failed analytics write must never surface to a shopper. The service
            // already logs at WARN; this catches only the unexpected, such as AppContext
            // itself being unavailable during shutdown.
            LOGGER.log(Level.FINE, "Could not store page experience sample", e);
        }
    }

    /**
     * Rejects beacons that appear to come from another site.
     *
     * <p>This endpoint writes unauthenticated, so without a check any page on the
     * internet could make a visitor's browser file samples here and fill the
     * report with numbers describing somebody else's site. {@code Sec-Fetch-Site}
     * is set by the browser and cannot be set by a script, so a browser sends
     * {@code same-origin} for {@code rum.js} on our own pages and
     * {@code cross-site} for an image or beacon embedded elsewhere.</p>
     *
     * <p>Deliberately not a hard requirement. The header is absent on older
     * browsers and on non-browser clients, and those requests are still subjected
     * to the sampling and the input validation below. Rejecting them outright would
     * silently lose real traffic from exactly the older browsers whose performance
     * is worth knowing about.</p>
     */
    private static boolean isSameOrigin(HttpServletRequest request) {
        String site = request.getHeader("Sec-Fetch-Site");
        return site == null || "same-origin".equalsIgnoreCase(site)
                || "none".equalsIgnoreCase(site);
    }

    /** A bounded millisecond value, or -1 when absent or implausible. */
    private static int ms(HttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank() || raw.length() > 9) {
            return -1;
        }
        try {
            int value = Integer.parseInt(raw);
            return value < 0 || value > MAX_MS ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int bytes(HttpServletRequest request) {
        String raw = request.getParameter("t");
        if (raw == null || raw.isBlank() || raw.length() > 12) {
            return 0;
        }
        try {
            long value = Long.parseLong(raw);
            if (value < 0 || value > MAX_BYTES) {
                return 0;
            }
            return (int) Math.min(Integer.MAX_VALUE, value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void maybePurge() {
        if (sincePurge.incrementAndGet() >= PURGE_EVERY) {
            sincePurge.set(0);
            try {
                AppContext.get().pageExperienceService()
                        .purge(com.hengtongan.computerstore.core.repository
                                .PageExperienceRepository.DEFAULT_RETENTION_DAYS);
            } catch (RuntimeException e) {
                LOGGER.log(Level.FINE, "Could not purge old page experience samples", e);
            }
        }
    }
}
