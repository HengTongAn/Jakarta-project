package com.hengtongan.computerstore.web.filter.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate Limiting Filter.
 * Prevents brute force attacks by limiting auth-attempt rates per IP address.
 *
 * <p>Registered in web.xml (order is deterministic).</p>
 *
 * <h2>Scope: only the endpoints that accept a guessable secret</h2>
 *
 * <p>The quota applies to the credential-bearing POSTs listed in
 * {@link #GUARDED_PATHS} -- login, register, the four password-reset steps, and
 * 2FA enable/disable (a 6-digit TOTP code is guessable in the same way a reset
 * code is). It used to apply to <em>every</em> POST in the application, because
 * the only check was the HTTP method. That meant the documented 10/minute
 * ceiling was charged to a customer clicking normally through the shop: add to
 * cart, change a quantity, submit a review, update a profile. Ten POSTs in a
 * minute while shopping is not abusive, and those requests have no guessable
 * secret in them -- they are session-authenticated and CSRF-protected. The
 * result was legitimate shoppers being answered with 429 "Too many requests".
 * The javadoc already claimed only auth attempts consumed the quota; the code
 * now does that.</p>
 *
 * <p>Cart and checkout POSTs are deliberately NOT guarded here: rate limiting a
 * payment path invites dropping a real order, and the cost of an extra
 * unauthenticated POST is bounded by CSRF plus authentication.</p>
 */
public class RateLimitingFilter implements Filter {

    // Store request counts and timestamps per IP. Forwarded headers are only
    // trusted when a reverse proxy has been explicitly configured to sanitize them.
    private static final ConcurrentHashMap<String, RateLimitInfo> rateLimits = new ConcurrentHashMap<>();

    // Guards the expiry sweep so it runs at most once per window rather than
    // once per request; see cleanupExpiredEntries().
    private static final AtomicLong lastCleanup = new AtomicLong(0);

    private static final long MINUTE_WINDOW_MS = 60_000;
    private static final long HOUR_WINDOW_MS = 60 * MINUTE_WINDOW_MS;
    private static final boolean TRUST_FORWARDED_HEADERS = Boolean.parseBoolean(
            System.getProperty("computerstore.trust-forwarded-headers", "false"));

    /**
     * Context-relative paths whose POSTs consume the quota. Compared against
     * {@code getServletPath()} with any trailing slash ignored, so
     * {@code /login/} cannot slip past the match on {@code /login}.
     */
    private static final Set<String> GUARDED_PATHS = Set.of(
            "/login",
            "/register",
            "/forgot",
            "/reset",
            "/verify-code",
            "/new-password",
            "/resend-code",
            "/account/2fa");

    // Per-IP ceilings for auth POSTs (/login, /register, /forgot, /reset,
    // /verify-code, /new-password, /resend-code). The reset-code steps are
    // included because a 6-digit code is a guessable secret: the endpoint that
    // checks it must not be an unlimited oracle.
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
        // that can authenticate, create an account or guess a reset/2FA code
        // consume the quota -- see the class javadoc for why an unguarded
        // every-POST version of this broke ordinary shopping.
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !isGuarded(request)) {
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

    /**
     * Whether this POST is one of the credential-accepting endpoints.
     *
     * <p>Matched on {@code getServletPath()} rather than
     * {@code getRequestURI()}: the URI includes the context path, which is
     * configurable, so a hard-coded prefix would silently stop matching (and
     * stop rate limiting) on any deployment mounted somewhere other than
     * {@code /computerstore}.</p>
     */
    static boolean isGuarded(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            // Fall back to the URI with the context path stripped. Only reached
            // for a mapped request, where one of the two is always populated.
            String uri = request.getRequestURI();
            String context = request.getContextPath();
            path = (context != null && !context.isEmpty() && uri.startsWith(context))
                    ? uri.substring(context.length())
                    : uri;
        }
        if (path == null) {
            return false;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return GUARDED_PATHS.contains(path.toLowerCase(Locale.ROOT));
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
        // At most once a minute. This used to run on every POST, and
        // removeIf() takes the monitor of every entry it visits, so each
        // request paid an O(number of distinct IPs) synchronized sweep. The
        // number of entries is attacker-influenced -- one is added per source
        // IP that ever posts -- so the work a single request could trigger grew
        // with the size of an attack, which is the wrong shape for a defence
        // against that attack. Entries are also only removed after a full idle
        // HOUR_WINDOW_MS, so sweeping more often than that cannot evict
        // anything a sweep a minute ago did not already evict.
        long last = lastCleanup.get();
        if (now - last < MINUTE_WINDOW_MS || !lastCleanup.compareAndSet(last, now)) {
            return;
        }
        rateLimits.entrySet().removeIf(entry -> entry.getValue().isIdleSince(now));
    }
}
