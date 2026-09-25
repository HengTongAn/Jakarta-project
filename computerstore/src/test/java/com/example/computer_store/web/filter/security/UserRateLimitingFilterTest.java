package com.example.computer_store.web.filter.security;

import com.example.computer_store.core.domain.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserRateLimitingFilterTest {

    private UserRateLimitingFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private HttpSession session;

    @BeforeEach
    void setUp() {
        UserRateLimitingFilter.clearAll();
        filter = new UserRateLimitingFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        session = mock(HttpSession.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getSession(false)).thenReturn(session);

        User user = new User();
        user.setUserId(7);
        user.setRole(User.Role.CUSTOMER);
        when(session.getAttribute("user")).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        UserRateLimitingFilter.clearAll();
    }

    @Test
    void testAllowsGetRequests() throws IOException, ServletException {
        when(request.getMethod()).thenReturn("GET");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void testAnonymousRequestsAreNotRateLimited() throws IOException, ServletException {
        when(request.getSession(false)).thenReturn(null);

        for (int i = 0; i < 200; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(200)).doFilter(request, response);
        verify(response, never()).sendError(eq(429), anyString());
    }

    @Test
    void testBlocksAfterCustomerMinuteLimit() throws IOException, ServletException {
        // Customer limit is 30 POSTs per minute -> 30 pass, the 31st is
        // rejected with 429 and the chain is not invoked for it.
        for (int i = 0; i < 30; i++) {
            filter.doFilter(request, response, chain);
        }
        verify(chain, times(30)).doFilter(request, response);
        verify(response, never()).sendError(eq(429), anyString());

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(429), anyString());
        verify(response).setHeader("Retry-After", "60");
        verify(chain, times(30)).doFilter(request, response);
    }

    @Test
    void testDifferentUsersHaveSeparateBudgets() throws IOException, ServletException {
        for (int i = 0; i < 30; i++) {
            filter.doFilter(request, response, chain);
        }

        // A second user on a fresh session is not affected by user #7.
        User other = new User();
        other.setUserId(99);
        other.setRole(User.Role.CUSTOMER);
        when(session.getAttribute("user")).thenReturn(other);

        filter.doFilter(request, response, chain);
        verify(chain, times(31)).doFilter(request, response);
    }
}