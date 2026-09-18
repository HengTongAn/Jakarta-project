package com.example.computer_store.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Security Headers Filter.
 * Adds security headers to all HTTP responses to protect against various web vulnerabilities.
 */
@WebFilter("/*")
public class SecurityHeadersFilter implements Filter {

    private static final boolean ENABLE_HSTS = Boolean.parseBoolean(
            System.getProperty("security.hsts.enabled", "false"));

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Prevent clickjacking attacks
        response.setHeader("X-Frame-Options", "DENY");

        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Enable XSS protection (modern browsers have built-in protection, but this helps older browsers)
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Content Security Policy to restrict resource loading
        response.setHeader("Content-Security-Policy", 
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' https://cdn.jsdelivr.net; " +
                "connect-src 'self'; " +
                "frame-ancestors 'none'; " +
                "form-action 'self';");

        // Referrer Policy to control how much referrer information is sent
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions Policy to control browser features
        response.setHeader("Permissions-Policy", 
                "geolocation=(), " +
                "microphone=(), " +
                "camera=(), " +
                "payment=(), " +
                "usb=()");

        // Strict Transport Security (only for HTTPS in production)
        // Enable with -Dsecurity.hsts.enabled=true JVM argument when using HTTPS
        if (ENABLE_HSTS && request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        }

        chain.doFilter(servletRequest, servletResponse);
    }
}