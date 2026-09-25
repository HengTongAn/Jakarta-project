package com.hengtongan.computerstore.web.filter.performance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

class StaticResourceCacheFilterTest {

    private StaticResourceCacheFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new StaticResourceCacheFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);

        when(request.getContextPath()).thenReturn("/computerstore");
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    void testSetsLongCacheForStaticCss() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/computerstore/assets/css/style.css");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Cache-Control", "public, max-age=604800, must-revalidate");
        verify(chain).doFilter(request, response);
    }

    @Test
    void testSetsImmutableCacheForVersionedMinifiedAsset() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/computerstore/assets/vendor/bootstrap/bootstrap.bundle.min.js");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Cache-Control", "public, max-age=31536000, immutable");
        verify(chain).doFilter(request, response);
    }

    @Test
    void testSetsImmutableForQueryVersionedCss() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/computerstore/assets/css/style.css?v=1a2b3c");

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Cache-Control", "public, max-age=31536000, immutable");
        verify(chain).doFilter(request, response);
    }

    @Test
    void testSkipsDynamicPages() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/computerstore/products");

        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void testSkipsNonGetMethods() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/computerstore/assets/css/style.css");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader(eq("Cache-Control"), anyString());
        verify(chain).doFilter(request, response);
    }
}