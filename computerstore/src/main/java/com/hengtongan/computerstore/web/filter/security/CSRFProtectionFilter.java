package com.hengtongan.computerstore.web.filter.security;

import com.hengtongan.computerstore.util.security.CSRFUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * CSRF Protection Filter.
 * Validates CSRF tokens for all POST requests to prevent cross-site request forgery attacks.
 * Excludes login and register endpoints as they don't require authentication.
 *
 * <p>Registered in web.xml (order is deterministic).</p>
 */
public class CSRFProtectionFilter implements Filter {

    // Every state-changing form in the application supplies a per-session
    // token. Login and registration are included: excluding them permits
    // login-CSRF, where a victim is silently signed into an attacker's account.
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI().substring(request.getContextPath().length());

        // Skip CSRF validation for GET requests and excluded paths
        if (!"POST".equalsIgnoreCase(request.getMethod()) || isExcludedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Validate CSRF token for POST requests
        if (!CSRFUtil.validateToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token. Please refresh the page and try again.");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.contains(path);
    }
}
