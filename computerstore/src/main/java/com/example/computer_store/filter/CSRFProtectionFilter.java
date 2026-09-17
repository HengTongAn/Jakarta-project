package com.example.computer_store.filter;

import com.example.computer_store.util.CSRFUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
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
 */
@WebFilter("/*")
public class CSRFProtectionFilter implements Filter {

    // Endpoints that are excluded from CSRF protection
    private static final Set<String> EXCLUDED_PATHS = new HashSet<>(Arrays.asList(
            "/login",
            "/register"
    ));

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
