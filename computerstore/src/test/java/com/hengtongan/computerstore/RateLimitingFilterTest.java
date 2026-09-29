package com.hengtongan.computerstore;

import com.hengtongan.computerstore.web.filter.security.RateLimitingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitingFilterTest {

    @Test
    void limitsOnlyPostAttemptsAndRejectsTheSixthAttemptInAMinute() throws Exception {
        System.setProperty("computerstore.rl.perIp.maxPerMinute", "5");
        try {
            RateLimitingFilter filter = new RateLimitingFilter();
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            when(request.getMethod()).thenReturn("POST");
            when(request.getRemoteAddr()).thenReturn("rate-test-" + System.nanoTime());

            for (int attempt = 0; attempt < 6; attempt++) {
                filter.doFilter(request, response, chain);
            }

            verify(chain, times(5)).doFilter(request, response);
            verify(response).setHeader("Retry-After", "60");
            verify(response).sendError(eq(429), anyString());
        } finally {
            System.clearProperty("computerstore.rl.perIp.maxPerMinute");
        }
    }

    @Test
    void rejectsOnceTheGlobalBudgetIsExhausted() throws Exception {
        // Fresh filter instance with a tiny global ceiling: the 4th POST in
        // the same minute is refused even though the per-IP limit (5/min)
        // would still have allowed it.
        System.setProperty("computerstore.rl.global.maxPerMinute", "3");
        try {
            RateLimitingFilter filter = new RateLimitingFilter();
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            FilterChain chain = mock(FilterChain.class);
            when(request.getMethod()).thenReturn("POST");
            when(request.getRemoteAddr()).thenReturn("global-budget-test");

            for (int attempt = 0; attempt < 4; attempt++) {
                filter.doFilter(request, response, chain);
            }

            verify(chain, times(3)).doFilter(request, response);
            verify(response).setHeader("Retry-After", "60");
            verify(response).sendError(eq(429), anyString());
        } finally {
            System.clearProperty("computerstore.rl.global.maxPerMinute");
        }
    }
}
