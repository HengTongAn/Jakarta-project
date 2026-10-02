package com.hengtongan.computerstore.web.filter.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Forwards unfinished admin sections to the shared Coming Soon view.
 *
 * <p>
 * The real destination remains in the navigation, so direct URLs, refresh,
 * keyboard activation and opening the link in a new tab all receive the same
 * placeholder. Admin authorization runs before this filter in {@code web.xml}.
 * A section is removed from {@link #GATED} when it is ready to ship.
 * </p>
 */
public final class ComingSoonGateFilter implements Filter {

    private static final String VIEW = "/WEB-INF/views/admin/coming-soon.jsp";
    private static final Map<String, Feature> GATED = new LinkedHashMap<>();

    static {
        GATED.put("/admin/performance", new Feature(
                "Performance", "bi-activity",
                "Performance monitoring tools are being prepared and will be available soon."));
    }

    /** True exactly when no admin sections are currently gated. */
    public static final boolean DORMANT = GATED.isEmpty();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        doFilter(request, response, chain, GATED);
    }

    /**
     * Applies an exact-path gate; package-private so the forwarding behavior is
     * fixture-testable.
     */
    static void doFilter(ServletRequest request, ServletResponse response, FilterChain chain,
            Map<String, Feature> gated) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Feature feature = gated.get(httpRequest.getServletPath());
        if (feature == null) {
            chain.doFilter(request, response);
            return;
        }

        httpRequest.setAttribute("comingSoonName", feature.name());
        httpRequest.setAttribute("comingSoonIcon", feature.icon());
        httpRequest.setAttribute("comingSoonNote", feature.note());
        httpRequest.getRequestDispatcher(VIEW).forward(request, response);
    }

    /** Name, Bootstrap icon and explanatory copy for a gated section. */
    record Feature(String name, String icon, String note) {
    }
}
