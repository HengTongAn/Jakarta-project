package com.example.computer_store.web.filter.security;

import com.example.computer_store.core.config.AppContext;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.util.web.RequestUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Kills stale sessions. Periodically re-reads the session user from the
 * database so a role change (e.g. an admin demoted to customer) takes effect
 * without requiring logout. If the account no longer exists or was soft-deleted,
 * the session is ended immediately.
 *
 * <p>Refresh is throttled and skipped for static assets so a single page load
 * does not trigger one DB round-trip per CSS/JS/image request.
 *
 * Registered in web.xml BEFORE AuthenticationFilter / AdminAuthorizationFilter
 * so those filters always see fresh role data.
 */
public class SessionUserRefreshFilter implements Filter {

    private static final String LAST_REFRESH_ATTR = "userRefreshAt";
    /** How often to re-query the user row for a logged-in session. */
    private static final long REFRESH_INTERVAL_MS = 30_000L;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        if (!RequestUtil.isStaticOrStream(request)) {
            refreshSessionUser(request);
        }
        chain.doFilter(servletRequest, servletResponse);
    }

    private void refreshSessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User sessionUser = session == null ? null : (User) session.getAttribute("user");
        if (sessionUser == null) {
            return;
        }

        Long lastRefresh = (Long) session.getAttribute(LAST_REFRESH_ATTR);
        long now = System.currentTimeMillis();
        if (lastRefresh != null && now - lastRefresh < REFRESH_INTERVAL_MS) {
            return;
        }

        User fresh = null;
        try {
            fresh = AppContext.get().userService().get(sessionUser.getUserId());
        } catch (NotFoundException ignored) {
            // account no longer exists: the session must not survive
        }
        if (fresh == null || fresh.isDeleted()) {
            session.invalidate();
            return;
        }
        fresh.setPasswordHash(null);
        session.setAttribute("user", fresh);
        session.setAttribute(LAST_REFRESH_ATTR, now);
    }
}
