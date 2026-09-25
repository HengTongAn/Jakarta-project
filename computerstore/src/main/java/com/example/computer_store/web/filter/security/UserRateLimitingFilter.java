package com.example.computer_store.web.filter.security;

import com.example.computer_store.core.domain.entity.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user rate limiting filter to prevent abuse from authenticated users.
 * Complements the IP-based rate limiting by adding user-specific limits.
 */
public class UserRateLimitingFilter implements Filter {

    // Rate limits per user tier
    private static final int CUSTOMER_REQUESTS_PER_MINUTE = 30;
    private static final int CUSTOMER_REQUESTS_PER_HOUR = 200;
    private static final int ADMIN_REQUESTS_PER_MINUTE = 60;
    private static final int ADMIN_REQUESTS_PER_HOUR = 500;

    private static final long MINUTE_WINDOW_MS = 60_000;
    private static final long HOUR_WINDOW_MS = 60 * MINUTE_WINDOW_MS;

    // Store request counts per user ID
    private static final ConcurrentHashMap<Integer, UserRateLimitInfo> userRateLimits = new ConcurrentHashMap<>();

    // Full-map reaping is expensive; run it at most once a minute instead of
    // on every request (the map only grows with users active in the hour window).
    private static final long CLEANUP_INTERVAL_MS = 60_000;
    private long lastCleanupAt = System.currentTimeMillis();

    static class UserRateLimitInfo {
        // long counters: int would overflow after ~2.1 billion requests, which
        // the limiter must never allow itself to approach.
        private long minuteCount;
        private long hourCount;
        private long minuteWindowStarted = System.currentTimeMillis();
        private long hourWindowStarted = minuteWindowStarted;

        synchronized boolean tryAcquire(long now, int minuteLimit, int hourLimit) {
            if (now - minuteWindowStarted >= MINUTE_WINDOW_MS) {
                minuteCount = 0;
                minuteWindowStarted = now;
            }
            if (now - hourWindowStarted >= HOUR_WINDOW_MS) {
                hourCount = 0;
                hourWindowStarted = now;
            }
            if (minuteCount >= minuteLimit || hourCount >= hourLimit) {
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

        // Only rate limit authenticated users
        HttpSession session = request.getSession(false);
        if (session == null) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        User user = (User) session.getAttribute("user");
        if (user == null) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Skip rate limiting for GET requests (read operations)
        if (!"POST".equalsIgnoreCase(request.getMethod()) && !"PUT".equalsIgnoreCase(request.getMethod())
                && !"DELETE".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Reap expired entries at most once a minute (not on every request).
        maybeCleanupExpired();

        // Determine limits based on user role
        int minuteLimit = user.isAdmin() ? ADMIN_REQUESTS_PER_MINUTE : CUSTOMER_REQUESTS_PER_MINUTE;
        int hourLimit = user.isAdmin() ? ADMIN_REQUESTS_PER_HOUR : CUSTOMER_REQUESTS_PER_HOUR;

        // Check rate limits
        if (!checkRateLimit(user.getUserId(), minuteLimit, hourLimit)) {
            response.setHeader("Retry-After", "60");
            response.sendError(429, "Too many requests. Please try again later.");
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private boolean checkRateLimit(int userId, int minuteLimit, int hourLimit) {
        UserRateLimitInfo info = userRateLimits.computeIfAbsent(userId, k -> new UserRateLimitInfo());
        return info.tryAcquire(System.currentTimeMillis(), minuteLimit, hourLimit);
    }

    private void maybeCleanupExpired() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupAt >= CLEANUP_INTERVAL_MS) {
            lastCleanupAt = now;
            userRateLimits.entrySet().removeIf(entry -> entry.getValue().isIdleSince(now));
        }
    }

    /**
     * Clears rate limit data for a specific user (e.g., after role change).
     */
    public static void clearUserRateLimit(int userId) {
        userRateLimits.remove(userId);
    }

    /**
     * Clears all rate limit data.
     */
    public static void clearAll() {
        userRateLimits.clear();
    }
}
