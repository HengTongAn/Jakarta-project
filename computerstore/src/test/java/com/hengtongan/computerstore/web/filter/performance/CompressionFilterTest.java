package com.hengtongan.computerstore.web.filter.performance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hermetic tests for CompressionFilter: no DB, no network access. The real
 * (mock) response output stream captures whatever bytes the filter finally
 * writes, so we can assert gzip magic bytes and round-trip the payload.
 */
class CompressionFilterTest {

    private static final String PAYLOAD = "A".repeat(2048);

    private CompressionFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ByteArrayOutputStream rawBytes;

    @BeforeEach
    void setUp() throws Exception {
        // Filter defaults to off (Tomcat 11 safety); tests exercise the ON path.
        System.setProperty("computerstore.compression.enabled", "true");
        filter = new CompressionFilter();
        filter.init(mock(FilterConfig.class));

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        rawBytes = new ByteArrayOutputStream();
        when(request.getContextPath()).thenReturn("/computerstore");
        when(request.getRequestURI()).thenReturn("/computerstore/products");
        when(request.getHeader("Accept-Encoding")).thenReturn("gzip");
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override
            public void write(int b) {
                rawBytes.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                rawBytes.write(b, off, len);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // not used in tests
            }
        });
    }

    private FilterChain writingChain(String contentType) {
        return (req, res) -> {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setContentType(contentType);
            httpRes.getWriter().write(PAYLOAD);
            httpRes.getWriter().flush();
        };
    }

    /**
     * Gosling's rule: any sufficiently large HTML page gets gzipped for a
     * client that advertises Accept-Encoding: gzip.
     */
    @Test
    void compressesHtmlOverOneKilobyte() throws IOException, ServletException {
        filter.doFilter(request, response, writingChain("text/html;charset=UTF-8"));

        verify(response).setHeader("Content-Encoding", "gzip");
        verify(response).setContentLength(anyInt());
        byte[] sent = rawBytes.toByteArray();
        // gzip magic bytes
        assertEquals(0x1f, sent[0] & 0xff);
        assertEquals(0x8b, sent[1] & 0xff);
        assertTrue(sent.length < PAYLOAD.getBytes(StandardCharsets.UTF_8).length,
                "compressed payload should be smaller than the original");
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), gunzip(sent));
    }

    @Test
    void compressesJsonOverOneKilobyte() throws IOException, ServletException {
        filter.doFilter(request, response, writingChain("application/json"));

        verify(response).setHeader("Content-Encoding", "gzip");
        assertEquals(0x1f, rawBytes.toByteArray()[0] & 0xff);
    }

    @Test
    void leavesSmallResponsesUncompressed() throws IOException, ServletException {
        String small = "Hello";
        FilterChain chain = (req, res) -> {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setContentType("text/html");
            httpRes.getWriter().write(small);
            httpRes.getWriter().flush();
        };

        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader("Content-Encoding", "gzip");
        assertArrayEquals(small.getBytes(StandardCharsets.UTF_8), rawBytes.toByteArray());
    }

    @Test
    void skipsCompressionWhenClientDoesNotAdvertiseGzip() throws IOException, ServletException {
        when(request.getHeader("Accept-Encoding")).thenReturn("identity");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(same(request), same(response));
        verify(response, never()).setHeader(eq("Content-Encoding"), anyString());
    }

    @Test
    void skipsNonCompressibleContentTypes() throws IOException, ServletException {
        FilterChain chain = writingChain("image/png");

        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader("Content-Encoding", "gzip");
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), rawBytes.toByteArray());
    }

    @Test
    void doesNotBufferStaticAssets() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/computerstore/assets/css/style.css");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(same(request), same(response));
        verify(response, never()).setHeader(eq("Content-Encoding"), anyString());
    }

    @Test
    void doesNotBufferRealtimeStream() throws IOException, ServletException {
        when(request.getRequestURI()).thenReturn("/computerstore/realtime");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(same(request), same(response));
        verify(response, never()).setHeader(eq("Content-Encoding"), anyString());
    }

    @Test
    void doesNotDoubleCompressAlreadyEncodedResponses() throws IOException, ServletException {
        when(response.containsHeader("Content-Encoding")).thenReturn(true);
        FilterChain chain = writingChain("text/html;charset=UTF-8");

        filter.doFilter(request, response, chain);

        verify(response, never()).setHeader("Content-Encoding", "gzip");
        assertArrayEquals(PAYLOAD.getBytes(StandardCharsets.UTF_8), rawBytes.toByteArray());
    }

    @Test
    void skipsWhenCompressionDisabledByDefault() throws IOException, ServletException {
        System.clearProperty("computerstore.compression.enabled");
        CompressionFilter offByDefault = new CompressionFilter();
        offByDefault.init(mock(FilterConfig.class));
        FilterChain chain = mock(FilterChain.class);

        offByDefault.doFilter(request, response, chain);

        verify(chain).doFilter(same(request), same(response));
        verify(response, never()).setHeader(eq("Content-Encoding"), anyString());
    }

    private static byte[] gunzip(byte[] gzipped) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            return in.readAllBytes();
        }
    }
}