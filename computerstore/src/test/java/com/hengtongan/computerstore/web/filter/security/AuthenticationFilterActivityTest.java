package com.hengtongan.computerstore.web.filter.security;

import com.hengtongan.computerstore.core.config.AppContext;
import com.hengtongan.computerstore.core.domain.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * The presence heartbeat is throttled per session, so where the stamp is written
 * relative to the {@code UPDATE} decides how a database blip escalates.
 *
 * <p>With no {@link AppContext} installed the {@code UPDATE} cannot run and
 * throws, which is precisely the situation the throttle has to survive - so
 * these tests drive the real code path instead of a stubbed service.</p>
 */
class AuthenticationFilterActivityTest {

    private static final String STAMP = "lastActivityTouch";

    private AuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private HttpSession session;
    private Map<String, Object> sessionAttributes;

    @BeforeEach
    void setUp() {
        // Guarantees updateLastActive() fails, exercising the best-effort path.
        AppContext.resetForTests(null);

        filter = new AuthenticationFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        session = mock(HttpSession.class);

        sessionAttributes = new HashMap<>();
        doAnswer(inv -> {
            sessionAttributes.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        when(session.getAttribute(anyString()))
                .thenAnswer(inv -> sessionAttributes.get(inv.getArgument(0)));

        User user = new User();
        user.setUserId(7);
        when(session.getAttribute("user")).thenReturn(user);
        when(request.getSession(false)).thenReturn(session);
        when(request.getContextPath()).thenReturn("/computerstore");
    }

    @AfterEach
    void tearDown() {
        AppContext.resetForTests(null);
    }

    @Test
    void stampIsSetOnTheFirstAuthenticatedRequest() throws IOException, ServletException {
        filter.doFilter(request, response, chain);

        assertNotNull(sessionAttributes.get(STAMP));
        verify(chain).doFilter(request, response);
    }

    @Test
    void stampStillAdvancesWhenTheWriteFails() throws IOException, ServletException {
        // Regression: the stamp used to be written only after a successful
        // UPDATE, so one failed write left it stale and every later request in
        // the session retried - each retry paying the pool's connection timeout.
        filter.doFilter(request, response, chain);

        assertNotNull(sessionAttributes.get(STAMP),
                "a failed presence write must still advance the throttle stamp");
        verify(chain).doFilter(request, response);
    }

    @Test
    void recentStampIsNotRewritten() throws IOException, ServletException {
        long existing = System.currentTimeMillis();
        sessionAttributes.put(STAMP, existing);

        filter.doFilter(request, response, chain);

        assertTrue(sessionAttributes.get(STAMP).equals(existing),
                "a request inside the 60s window must not re-stamp the session");
    }

    @Test
    void staleStampIsRefreshed() throws IOException, ServletException {
        long stale = System.currentTimeMillis() - 61_000L;
        sessionAttributes.put(STAMP, stale);

        filter.doFilter(request, response, chain);

        long now = (Long) sessionAttributes.get(STAMP);
        assertTrue(now > stale, "a stamp older than the interval must be refreshed");
    }

    @Test
    void unauthenticatedRequestIsRedirectedAndLeavesNoStamp()
            throws IOException, ServletException {
        when(session.getAttribute("user")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/computerstore/admin/orders");

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect(contains("/login?return="));
        verify(chain, never()).doFilter(any(), any());
        assertTrue(sessionAttributes.isEmpty(), "no activity may be tracked without a user");
    }

    @Test
    void redirectKeepsTheOriginalQueryString() throws IOException, ServletException {
        when(session.getAttribute("user")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/computerstore/account");
        when(request.getQueryString()).thenReturn("tab=orders");

        filter.doFilter(request, response, chain);

        verify(response).sendRedirect(contains("tab%3Dorders"));
    }
}
