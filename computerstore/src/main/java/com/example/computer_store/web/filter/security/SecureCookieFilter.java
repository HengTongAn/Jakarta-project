package com.example.computer_store.web.filter.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;

/**
 * Conditionally marks session cookies (and every other Set-Cookie header)
 * with the {@code Secure} attribute.
 *
 * <p>web.xml's static {@code <cookie-config>} cannot be conditional, so while
 * local development runs over plain HTTP we keep {@code Secure} off there and
 * stamp it at the response layer instead. The attribute is added when the
 * request arrived over TLS ({@code request.isSecure()}, i.e. a direct HTTPS
 * connector) or when the operator sets
 * {@code -Dcomputerstore.session.cookie.secure=true} — the recommended value
 * behind a TLS-terminating reverse proxy such as Caddy.
 */
public class SecureCookieFilter implements Filter {

    private static final boolean FORCE_SECURE = Boolean.parseBoolean(
            System.getProperty("computerstore.session.cookie.secure", "false"));

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        if (!FORCE_SECURE && !request.isSecure()) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        chain.doFilter(servletRequest, new SecureResponseWrapper((HttpServletResponse) servletResponse));
    }

    @Override
    public void destroy() {
    }

    /** Rewrites Set-Cookie headers (and cookies) to carry the Secure attribute. */
    private static final class SecureResponseWrapper extends HttpServletResponseWrapper {

        SecureResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, "Set-Cookie".equalsIgnoreCase(name) ? secure(value) : value);
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, "Set-Cookie".equalsIgnoreCase(name) ? secure(value) : value);
        }

        @Override
        public void addCookie(Cookie cookie) {
            cookie.setSecure(true);
            super.addCookie(cookie);
        }

        private static String secure(String cookieHeader) {
            if (cookieHeader == null || cookieHeader.isEmpty()) {
                return cookieHeader;
            }
            if (cookieHeader.toLowerCase().contains("; secure")) {
                return cookieHeader;
            }
            return cookieHeader + "; Secure";
        }
    }
}
