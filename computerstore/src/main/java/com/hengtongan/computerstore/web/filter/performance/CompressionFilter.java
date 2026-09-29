package com.hengtongan.computerstore.web.filter.performance;

import com.hengtongan.computerstore.util.web.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Filters responses for HTTP gzip compression to reduce bandwidth and
 * improve load times for text-based content (HTML, CSS, JS, JSON, XML).
 *
 * <p><strong>How it works:</strong> responses are buffered in a response
 * wrapper, then replayed after the filter chain either gzip-compressed
 * (when the client accepts gzip, the content type is compressible and the
 * payload exceeds 1&nbsp;KB) or verbatim. The wrapper is never installed for
 * static assets and the realtime SSE stream ({@link RequestUtil#isStaticOrStream}),
 * because buffering those would defeat streaming and waste memory on binaries.</p>
 *
 * <p>Configurable via system property {@code computerstore.compression.enabled}
 * (default: {@code false}). Prefer Tomcat connector gzip
 * ({@code compression="on"} in {@code server.xml}) over this filter.</p>
 *
 * <p><strong>Why default off:</strong> the buffering wrapper cannot reliably
 * replay responses on Tomcat 11. JSP forwards go through
 * {@code ApplicationDispatcher}, whose {@code SuspendWrappedResponseAfterForward}
 * handling finishes the real response mid-chain, silently dropping everything
 * the filter writes after {@code chain.doFilter()} returns (observed as
 * {@code Content-Length: 0} / blank pages for gzip-negotiating clients). It
 * also buffers the full body before flush, which adds TTFB delay on every
 * HTML page. Opt in only with {@code -Dcomputerstore.compression.enabled=true}
 * when you know the connector cannot gzip.</p>
 */
public class CompressionFilter implements Filter {

    private static final Set<String> COMPRESSIBLE_TYPES = new HashSet<>();
    private static final int COMPRESSION_THRESHOLD = 1024; // Only compress responses > 1KB

    static {
        // Content types that benefit from compression
        COMPRESSIBLE_TYPES.add("text/html");
        COMPRESSIBLE_TYPES.add("text/plain");
        COMPRESSIBLE_TYPES.add("text/xml");
        COMPRESSIBLE_TYPES.add("text/css");
        COMPRESSIBLE_TYPES.add("text/javascript");
        COMPRESSIBLE_TYPES.add("application/javascript");
        COMPRESSIBLE_TYPES.add("application/json");
        COMPRESSIBLE_TYPES.add("application/xml");
        COMPRESSIBLE_TYPES.add("application/xhtml+xml");
    }

    private boolean enabled = false;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.enabled = Boolean.parseBoolean(
                System.getProperty("computerstore.compression.enabled", "false"));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Don't buffer streaming endpoints (SSE) or static assets/images:
        // buffering would break realtime delivery and waste memory on binaries.
        if (RequestUtil.isStaticOrStream(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // Check if client accepts gzip encoding
        String acceptEncoding = httpRequest.getHeader("Accept-Encoding");
        if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
            chain.doFilter(request, response);
            return;
        }

        // Let caches know the response varies by Accept-Encoding.
        appendVaryHeader(httpResponse);

        // Wrap response with compression
        CompressionResponse wrappedResponse = new CompressionResponse(httpResponse);
        chain.doFilter(request, wrappedResponse);

        // Never re-compress something the downstream code already encoded.
        if (httpResponse.containsHeader("Content-Encoding")) {
            wrappedResponse.writeOriginal();
            return;
        }

        // Apply compression if beneficial
        if (shouldCompress(wrappedResponse)) {
            byte[] compressedBytes = compress(wrappedResponse.getContent());
            if (compressedBytes != null && compressedBytes.length < wrappedResponse.getContent().length) {
                httpResponse.setHeader("Content-Encoding", "gzip");
                httpResponse.setContentLength(compressedBytes.length);
                httpResponse.getOutputStream().write(compressedBytes);
                return;
            }
        }

        // Fallback to original content
        wrappedResponse.writeOriginal();
    }

    private void appendVaryHeader(HttpServletResponse response) {
        String vary = response.getHeader("Vary");
        if (vary == null || !vary.contains("Accept-Encoding")) {
            if (vary == null || vary.isEmpty()) {
                response.setHeader("Vary", "Accept-Encoding");
            } else {
                response.setHeader("Vary", vary + ", Accept-Encoding");
            }
        }
    }

    private boolean shouldCompress(CompressionResponse response) {
        // Check content type
        String contentType = response.getContentType();
        if (contentType == null) {
            return false;
        }

        String baseType = contentType.split(";")[0].trim();
        if (!COMPRESSIBLE_TYPES.contains(baseType)) {
            return false;
        }

        // Check size threshold
        return response.getContent().length >= COMPRESSION_THRESHOLD;
    }

    private byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
                gzip.write(data);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            return null; // Fallback to uncompressed
        }
    }

    @Override
    public void destroy() {
        // Nothing to release: the wrapper is per-request.
    }

    /**
     * Custom response wrapper that buffers content so the filter can decide
     * after the chain whether compression actually helps.
     */
    private static class CompressionResponse extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream content = new ByteArrayOutputStream();
        private String contentType;
        private PrintWriter writer;

        public CompressionResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(content, StandardCharsets.UTF_8));
            }
            return writer;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    content.write(b);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    content.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    content.write(b, off, len);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    // Never used: the filter chain writes synchronously and the
                    // real output stream is only written after the chain returns.
                }
            };
        }

        @Override
        public void setContentType(String type) {
            this.contentType = type;
            super.setContentType(type);
        }

        // The body is not written until the filter decides whether it will be
        // gzipped. Delegating a servlet/JSP supplied length would commit an
        // incorrect length and was the source of truncated responses.
        @Override
        public void setContentLength(int len) {
            // deliberately deferred
        }

        @Override
        public void setContentLengthLong(long len) {
            // deliberately deferred
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        /**
         * Flush the buffered writer into {@link #content} without committing the
         * real response. The default {@code HttpServletResponseWrapper} delegates
         * to the real response, which would commit an empty response ahead of the
         * compression decision (notably when Jasper flushes while compiling a
         * cold JSP), leaving clients with a zero-byte body.
         */
        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            content.flush();
        }

        /** The real response stays uncommitted until the filter replays the buffer. */
        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public void resetBuffer() {
            content.reset();
            if (writer != null) {
                writer = new PrintWriter(new OutputStreamWriter(content, StandardCharsets.UTF_8));
            }
        }

        @Override
        public void reset() {
            resetBuffer();
            super.reset();
        }

        public byte[] getContent() {
            if (writer != null) {
                writer.flush();
            }
            return content.toByteArray();
        }

        public void writeOriginal() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            getResponse().getOutputStream().write(content.toByteArray());
        }
    }
}
