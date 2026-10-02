package com.hengtongan.computerstore.web.filter.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        // Clear any leftover system properties, then pin per-IP limits so the
        // tests keep their original 5/min semantics regardless of the
        // production defaults (10/min, 60/hour). Must be set BEFORE the filter
        // is constructed: per-IP limits are read at construction time.
        System.clearProperty("computerstore.rl.perIp.maxPerMinute");
        System.clearProperty("computerstore.rl.perIp.maxPerHour");
        System.clearProperty("computerstore.trust-forwarded-headers");
        System.setProperty("computerstore.rl.perIp.maxPerMinute", "5");
        System.setProperty("computerstore.rl.perIp.maxPerHour", "20");

        filter = new RateLimitingFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        responseWriter = new StringWriter();

        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        // The quota only applies to the credential-accepting endpoints, so every
        // test below has to say WHICH endpoint it is posting to. Defaults to the
        // login POST; tests that care about a different path restub it.
        when(request.getServletPath()).thenReturn("/login");

        // Clear the rate limit map via reflection
        Field rateLimitsField = RateLimitingFilter.class.getDeclaredField("rateLimits");
        rateLimitsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, RateLimitingFilter.RateLimitInfo> rateLimits =
                (java.util.Map<String, RateLimitingFilter.RateLimitInfo>) rateLimitsField.get(null);
        rateLimits.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clear rate limits after each test
        Field rateLimitsField = RateLimitingFilter.class.getDeclaredField("rateLimits");
        rateLimitsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, RateLimitingFilter.RateLimitInfo> rateLimits =
                (java.util.Map<String, RateLimitingFilter.RateLimitInfo>) rateLimitsField.get(null);
        rateLimits.clear();

        System.clearProperty("computerstore.rl.perIp.maxPerMinute");
        System.clearProperty("computerstore.rl.perIp.maxPerHour");
        System.clearProperty("computerstore.trust-forwarded-headers");
    }

    @Test
    void testAllowsGetRequests() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void testAllowsRequestsUnderLimit() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void testBlocksAfterMinuteLimit() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");

        // Make 5 requests (the limit) - these should all pass through
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, chain);
            verify(response, never()).sendError(eq(429), anyString());
        }
        // Verify chain.doFilter was called 5 times
        verify(chain, times(5)).doFilter(request, response);

        // 6th request should be blocked
        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(429), anyString());
        verify(response).setHeader("Retry-After", "60");
        // chain.doFilter should NOT be called for the 6th request (total still 5)
        verify(chain, times(5)).doFilter(request, response);
    }

    @Test
    void testDifferentIPsHaveSeparateLimits() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("POST");

        // IP 1 makes 5 requests
        when(request.getRemoteAddr()).thenReturn("10.0.0.10");
        for (int i = 0; i < 5; i++) {
            filter.doFilter(request, response, chain);
        }

        // IP 2 should still be allowed
        when(request.getRemoteAddr()).thenReturn("10.0.0.11");
        filter.doFilter(request, response, chain);

        verify(chain, times(6)).doFilter(request, response);
    }

    @Test
    void testIgnoresXForwardedForWhenTrustDisabled() throws IOException, ServletException {
        // Default is disabled
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        filter.doFilter(request, response, chain);

        // Should use remoteAddr, not X-Forwarded-For
        verify(chain).doFilter(request, response);
    }

    // ---------------------------------------------------------------- scope

    /**
     * The bug this pins: the quota was charged to every POST in the app because
     * only the method was checked, so an ordinary customer clicking through the
     * shop -- add to cart, change quantity, review, profile -- could be answered
     * 429 after ten POSTs in a minute. None of those requests carry a guessable
     * secret; they are session-authenticated and CSRF-protected.
     */
    @Test
    void shoppingPostsDoNotConsumeTheAuthQuota() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/cart/add");

        // Far more POSTs in a minute than the 5/min ceiling set above.
        for (int i = 0; i < 25; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(25)).doFilter(request, response);
        verify(response, never()).sendError(eq(429), anyString());
    }

    @Test
    void checkoutPostsAreNotRateLimited() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/checkout");
        for (int i = 0; i < 25; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(25)).doFilter(request, response);
        verify(response, never()).sendError(eq(429), anyString());
    }

    /** Every credential-bearing POST must still be guarded. */
    @Test
    void everyGuessableSecretEndpointIsGuarded() throws Exception {
        String[] guarded = {"/login", "/register", "/forgot", "/reset",
                "/verify-code", "/new-password", "/resend-code", "/account/2fa"};
        for (String path : guarded) {
            Field f = RateLimitingFilter.class.getDeclaredField("rateLimits");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, ?> map = (java.util.Map<String, ?>) f.get(null);
            map.clear();

            when(request.getServletPath()).thenReturn(path);
            // 5 allowed at the pinned limit, 6th must be refused.
            for (int i = 0; i < 6; i++) {
                filter.doFilter(request, response, chain);
            }
            verify(chain, times(5)).doFilter(request, response);
            verify(response, atLeastOnce()).sendError(eq(429), anyString());
            reset(response);
            reset(chain);
        }
    }

    /** A trailing slash must not be a way around the match. */
    @Test
    void trailingSlashDoesNotEvadeTheQuota() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn("/login/");
        for (int i = 0; i < 6; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(5)).doFilter(request, response);
        verify(response, atLeastOnce()).sendError(eq(429), anyString());
    }

    /**
     * The match must not be built on a hard-coded context path. Deployment under
     * a different context must not silently disable rate limiting.
     */
    @Test
    void contextPathIsNotHardCodedIntoTheMatch() throws IOException, ServletException {
        when(request.getServletPath()).thenReturn(null);
        when(request.getContextPath()).thenReturn("/shop");
        when(request.getRequestURI()).thenReturn("/shop/login");

        for (int i = 0; i < 6; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(5)).doFilter(request, response);
        verify(response, atLeastOnce()).sendError(eq(429), anyString());
    }

    @Test
    void testRateLimitInfoWindowReset() throws Exception {
        var infoClass = RateLimitingFilter.class.getDeclaredClasses()[0]; // RateLimitInfo
        var constructor = infoClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object info = constructor.newInstance();

        var tryAcquire = infoClass.getDeclaredMethod("tryAcquire", long.class);
        tryAcquire.setAccessible(true);
        var isIdleSince = infoClass.getDeclaredMethod("isIdleSince", long.class);
        isIdleSince.setAccessible(true);

        long now = System.currentTimeMillis();

        // Should allow first request
        assertTrue((Boolean) tryAcquire.invoke(info, now));

        // Should be idle after hour window
        long future = now + 61 * 60 * 1000; // 61 minutes later
        assertTrue((Boolean) isIdleSince.invoke(info, future));
    }
}