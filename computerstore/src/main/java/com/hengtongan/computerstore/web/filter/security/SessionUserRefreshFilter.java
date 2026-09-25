package com.hengtongan.computerstore.web.filter.security;

import com.hengtongan.computerstore.core.config.AppContext;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.web.RequestUtil;
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
 * database so a role change (e.g. an admin demoted to customer, or a customer
 * promoted to admin) takes effect without requiring logout/login. Profile
 * edits (name, email, avatar) are picked up by the same refresh. If the
 * account no longer exists or was soft-deleted, the session is ended
 * immediately.
 *
 * <p>Refresh is throttled to one check per {@link #REFRESH_INTERVAL_MS} and
 * skipped for static assets so a single page load does not trigger a DB
 * round-trip for every CSS/JS/image request. The user row is served by the
 * user cache (invalidated on every DB write), so a steady-state tick is a
 * cache hit, not a query.
 *
 * <p><b>Resilience:</b> a transient DB/pool failure never aborts the request
 * nor hot-loops the filter: every tick advances {@link #LAST_REFRESH_ATTR}
 * before touching the database, and non-fatal errors keep the current session
 * user. Only a definitive "row gone" or "soft-deleted" ends the session.
 *
 * <p>Registered in web.xml BEFORE AuthenticationFilter / AdminAuthorizationFilter
 * so those filters always see fresh role data.
 */
public class SessionUserRefreshFilter implements Filter {

    private static final String LAST_REFRESH_ATTR = "userRefreshAt";
    /** How often to re-query the user row for a logged-in session. */
    private static final long REFRESH_INTERVAL_MS = 15_000L;

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

        long now = System.currentTimeMillis();
        Long lastRefresh = (Long) session.getAttribute(LAST_REFRESH_ATTR);
        if (lastRefresh != null && now - lastRefresh < REFRESH_INTERVAL_MS) {
            return;
        }

        // Advance the tick unconditionally: if the DB is down we already paid
        // for one attempt and must NOT retry on the very next request (that is
        // how a struggling pool becomes an exhausted one).
        session.setAttribute(LAST_REFRESH_ATTR, now);

        try {
            User fresh = AppContext.get().userService().get(sessionUser.getUserId());
            if (fresh.isDeleted()) {
                session.invalidate();
                return;
            }
            fresh.setPasswordHash(null);
            session.setAttribute("user", fresh);
        } catch (NotFoundException e) {
            // Account no longer exists: the session must not survive.
            session.invalidate();
        } catch (RuntimeException e) {
            // Transient DB/pool failure: keep the current session user so
            // navigation is never blocked; the next tick retries.
        }
    }
}