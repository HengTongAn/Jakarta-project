package com.hengtongan.computerstore.web.filter.security;

import com.hengtongan.computerstore.core.config.AppContext;

import com.hengtongan.computerstore.core.domain.entity.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Protects every protected URL (admin, cart, checkout, account pages).
 * Unauthenticated users are redirected to the login page.
 * For authenticated users it records last-activity (throttled) for the
 * live/not-live presence indicator.
 */
public class AuthenticationFilter implements Filter {

    private static final long ACTIVITY_UPDATE_INTERVAL_MS = 60_000;
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            String returnPath = request.getRequestURI();
            if (returnPath.startsWith(request.getContextPath())) {
                returnPath = returnPath.substring(request.getContextPath().length());
            }
            if (request.getQueryString() != null) {
                returnPath += "?" + request.getQueryString();
            }
            response.sendRedirect(request.getContextPath() + "/login?return="
                    + URLEncoder.encode(returnPath, StandardCharsets.UTF_8));
            return;
        }

        trackActivity(session, user.getUserId());

        chain.doFilter(request, response);
    }

    private void trackActivity(HttpSession session, int userId) {
        long now = System.currentTimeMillis();
        Long lastTouch = (Long) session.getAttribute("lastActivityTouch");
        if (lastTouch == null || now - lastTouch >= ACTIVITY_UPDATE_INTERVAL_MS) {
            try {
                AppContext.get().userService().updateLastActive(userId);
                session.setAttribute("lastActivityTouch", now);
            } catch (RuntimeException ignored) {
                // presence tracking is best-effort; never block navigation on DB failure
            }
        }
    }
}
