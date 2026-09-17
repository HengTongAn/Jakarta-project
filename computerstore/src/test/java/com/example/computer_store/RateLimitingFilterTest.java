package com.example.computer_store;

import com.example.computer_store.filter.RateLimitingFilter;
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
    }
}
