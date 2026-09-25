package com.example.computer_store.web.filter.performance;

import com.example.computer_store.util.web.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Static Resource Cache Filter.
 * Adds long-lived Cache-Control headers to CSS/JS/image/font assets so repeat
 * page views are served from the browser cache instead of hitting Tomcat.
 *
 * <p>Versioned build assets (e.g. {@code /assets/css/main-1a2b3c.css}) can be
 * marked {@code immutable}; everything else gets a conservative 7-day TTL with
 * {@code must-revalidate} so a file change is picked up within a week.</p>
 *
 * <p>Registered in web.xml before the request-scoped filters.</p>
 */
public class StaticResourceCacheFilter implements Filter {

    private static final String CACHE_IMMUTABLE = "public, max-age=31536000, immutable";
    private static final String CACHE_REVALIDATE = "public, max-age=604800, must-revalidate";

    private static final String[] VERSIONED_HINTS = {
            ".min.css", ".min.js", ".css?v=", ".js?v="
    };

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (RequestUtil.isStaticOrStream(request)) {
            String method = request.getMethod();
            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                response.setHeader("Cache-Control", isVersioned(request) ? CACHE_IMMUTABLE : CACHE_REVALIDATE);
            }
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private boolean isVersioned(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String hint : VERSIONED_HINTS) {
            if (uri.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
