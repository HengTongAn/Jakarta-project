package com.hengtongan.computerstore.web.filter.monitoring;

import com.hengtongan.computerstore.util.web.AuditLogger;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Seeds the per-request audit context (request id, session id, client IP)
 * so AuditLogger can correlate every event recorded during the request
 * back to a session and a single HTTP request. Runs on every URL pattern,
 * before the authentication filters.
 */
public class RequestAuditContextFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String sessionId = null;
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            sessionId = session.getId();
        }
        String clientIp = AuditLogger.clientIp(httpRequest);
        String requestId = AuditLogger.newRequestId();
        AuditLogger.bindContext(new AuditLogger.RequestContext(requestId, sessionId, clientIp));
        try {
            chain.doFilter(request, response);
        } finally {
            AuditLogger.clearContext();
        }
    }
}
