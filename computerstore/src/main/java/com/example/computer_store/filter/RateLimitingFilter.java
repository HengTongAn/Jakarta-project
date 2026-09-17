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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter.
 * Prevents brute force attacks and DoS by limiting request rates per IP address.
 */
@WebFilter({"/login", "/register"})
public class RateLimitingFilter implements Filter {

    // Store request counts and timestamps per IP. Forwarded headers are only
    // trusted when a reverse proxy has been explicitly configured to sanitize them.
    private static final ConcurrentHashMap<String, RateLimitInfo> rateLimits = new ConcurrentHashMap<>();
    
    // Rate limiting configuration
    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final int MAX_REQUESTS_PER_HOUR = 20;
    private static final long MINUTE_WINDOW_MS = 60_000;
    private static final long HOUR_WINDOW_MS = 60 * MINUTE_WINDOW_MS;
    private static final boolean TRUST_FORWARDED_HEADERS = Boolean.parseBoolean(
            System.getProperty("computerstore.trust-forwarded-headers", "false"));
    
    static class RateLimitInfo {
        private int minuteCount;
        private int hourCount;
        private long minuteWindowStarted = System.currentTimeMillis();
        private long hourWindowStarted = minuteWindowStarted;

        synchronized boolean tryAcquire(long now) {
            if (now - minuteWindowStarted >= MINUTE_WINDOW_MS) {
                minuteCount = 0;
                minuteWindowStarted = now;
            }
            if (now - hourWindowStarted >= HOUR_WINDOW_MS) {
                hourCount = 0;
                hourWindowStarted = now;
            }
            if (minuteCount >= MAX_REQUESTS_PER_MINUTE || hourCount >= MAX_REQUESTS_PER_HOUR) {
                return false;
            }
            minuteCount++;
            hourCount++;
            return true;
        }

        synchronized boolean isIdleSince(long now) {
            return now - hourWindowStarted >= HOUR_WINDOW_MS;
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Login and registration pages must remain available. Only attempts
        // that can authenticate or create an account consume the quota.
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        String clientIp = getClientIp(request);
        
        // Clean up expired entries periodically
        cleanupExpiredEntries();
        
        // Check rate limits
        if (!checkRateLimit(clientIp)) {
            response.setHeader("Retry-After", "60");
            response.sendError(429, // HTTP 429 Too Many Requests
                    "Too many requests. Please try again later.");
            return;
        }
        
        chain.doFilter(servletRequest, servletResponse);
    }

    private String getClientIp(HttpServletRequest request) {
        if (!TRUST_FORWARDED_HEADERS) {
            return request.getRemoteAddr();
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For (take the first one)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private boolean checkRateLimit(String ip) {
        RateLimitInfo info = rateLimits.computeIfAbsent(ip, k -> new RateLimitInfo());
        return info.tryAcquire(System.currentTimeMillis());
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        rateLimits.entrySet().removeIf(entry -> entry.getValue().isIdleSince(now));
    }
}
