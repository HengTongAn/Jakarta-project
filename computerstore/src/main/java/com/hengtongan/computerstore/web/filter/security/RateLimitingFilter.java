package com.hengtongan.computerstore.web.filter.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Filter.
 * Prevents brute force attacks and DoS by limiting request rates per IP address.
 *
 * <p>Registered in web.xml (order is deterministic).</p>
 */
public class RateLimitingFilter implements Filter {

    // Store request counts and timestamps per IP. Forwarded headers are only
    // trusted when a reverse proxy has been explicitly configured to sanitize them.
    private static final ConcurrentHashMap<String, RateLimitInfo> rateLimits = new ConcurrentHashMap<>();

    private static final long MINUTE_WINDOW_MS = 60_000;
    private static final long HOUR_WINDOW_MS = 60 * MINUTE_WINDOW_MS;
    private static final boolean TRUST_FORWARDED_HEADERS = Boolean.parseBoolean(
            System.getProperty("computerstore.trust-forwarded-headers", "false"));

    // Per-IP ceilings for auth POSTs (/login, /register, /forgot, /reset).
    // Instance-level (read at construction, like globalMaxPerMinute) so tests
    // can pin values and a NAT'd office IP is not blocked: defaults 10/min and
    // 60/hour are generous for legitimate shared-IP use while still throttling
    // brute force. Tunable via system properties.
    private final int maxRequestsPerMinute = Integer.getInteger(
            "computerstore.rl.perIp.maxPerMinute", 10);
    private final int maxRequestsPerHour = Integer.getInteger(
            "computerstore.rl.perIp.maxPerHour", 60);

    // Whole-system auth-attempt ceiling: even a distributed attack (many
    // distinct IPs) cannot exceed this many POSTs per minute across /login,
    // /register, /forgot and /reset combined. The per-IP limits apply first;
    // this is the safety net on top. Instance state (web.xml registers exactly
    // one filter instance, so this is a true whole-system budget in
    // production; tests can build an isolated filter). Tunable via
    // -Dcomputerstore.rl.global.maxPerMinute, read at construction.
    private final int globalMaxPerMinute = Integer.getInteger(
            "computerstore.rl.global.maxPerMinute", 200);
    private final Object globalLock = new Object();
    private long globalMinuteCount = 0;
    private long globalWindowStarted = System.currentTimeMillis();

    /** @return false when the global per-minute budget for this rollover window is exhausted. */
    private boolean tryAcquireGlobal(long now) {
        synchronized (globalLock) {
            if (now - globalWindowStarted >= MINUTE_WINDOW_MS) {
                globalMinuteCount = 0;
                globalWindowStarted = now;
            }
            if (globalMinuteCount >= globalMaxPerMinute) {
                return false;
            }
            globalMinuteCount++;
            return true;
        }
    }

    static class RateLimitInfo {
        private final int maxPerMinute;
        private final int maxPerHour;
        // long counters: int would overflow after ~2.1 billion requests, which
        // the limiter itself must never let happen (the overflow risk-free
        // defense is to never risk it in the first place).
        private long minuteCount;
        private long hourCount;
        private long minuteWindowStarted = System.currentTimeMillis();
        private long hourWindowStarted = minuteWindowStarted;

        RateLimitInfo(int maxPerMinute, int maxPerHour) {
            this.maxPerMinute = maxPerMinute;
            this.maxPerHour = maxPerHour;
        }

        /** Used by the window-reset unit test; limits generous enough that a single acquire succeeds. */
        RateLimitInfo() {
            this(1_000, 10_000);
        }

        synchronized boolean tryAcquire(long now) {
            if (now - minuteWindowStarted >= MINUTE_WINDOW_MS) {
                minuteCount = 0;
                minuteWindowStarted = now;
            }
            if (now - hourWindowStarted >= HOUR_WINDOW_MS) {
                hourCount = 0;
                hourWindowStarted = now;
            }
            if (minuteCount >= maxPerMinute || hourCount >= maxPerHour) {
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

        // Check rate limits (per-IP first, then the global budget)
        if (!checkRateLimit(clientIp) || !tryAcquireGlobal(System.currentTimeMillis())) {
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
        RateLimitInfo info = rateLimits.computeIfAbsent(ip,
                k -> new RateLimitInfo(maxRequestsPerMinute, maxRequestsPerHour));
        return info.tryAcquire(System.currentTimeMillis());
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        rateLimits.entrySet().removeIf(entry -> entry.getValue().isIdleSince(now));
    }
}
