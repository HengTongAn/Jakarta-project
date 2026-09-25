package com.hengtongan.computerstore.util.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CSRFUtilTest {

    @Test
    void testGenerateTokenCreatesNewToken() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("csrfToken")).thenReturn(null);

        String token1 = CSRFUtil.generateToken(session);
        
        // After first call, the token should be stored in session
        when(session.getAttribute("csrfToken")).thenReturn(token1);
        String token2 = CSRFUtil.generateToken(session);

        assertNotNull(token1);
        assertEquals(token1, token2); // Should return same token on second call
        assertTrue(token1.matches("[0-9a-f-]{36}")); // UUID format
    }

    @Test
    void testValidateTokenSuccess() {
        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        String token = "test-token-123";
        when(session.getAttribute("csrfToken")).thenReturn(token);
        when(request.getParameter("csrfToken")).thenReturn(token);
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");
        when(request.getSession(false)).thenReturn(session);

        assertTrue(CSRFUtil.validateToken(request));
    }

    @Test
    void testValidateTokenFromHeader() {
        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        String token = "header-token-456";
        when(session.getAttribute("csrfToken")).thenReturn(token);
        when(request.getParameter("csrfToken")).thenReturn(null);
        when(request.getHeader("X-CSRF-Token")).thenReturn(token);
        when(request.getContentType()).thenReturn("application/json");
        when(request.getSession(false)).thenReturn(session);

        assertTrue(CSRFUtil.validateToken(request));
    }

    @Test
    void testValidateTokenMismatch() {
        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(session.getAttribute("csrfToken")).thenReturn("session-token");
        when(request.getParameter("csrfToken")).thenReturn("different-token");
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");
        when(request.getSession(false)).thenReturn(session);

        assertFalse(CSRFUtil.validateToken(request));
    }

    @Test
    void testValidateTokenNoSession() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);

        assertFalse(CSRFUtil.validateToken(request));
    }

    @Test
    void testValidateTokenNoTokenInSession() {
        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(session.getAttribute("csrfToken")).thenReturn(null);
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("csrfToken")).thenReturn("some-token");
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");

        assertFalse(CSRFUtil.validateToken(request));
    }

    @Test
    void testValidateTokenNoTokenInRequest() {
        HttpSession session = mock(HttpSession.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(session.getAttribute("csrfToken")).thenReturn("session-token");
        when(request.getSession(false)).thenReturn(session);
        when(request.getParameter("csrfToken")).thenReturn(null);
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);
        when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");

        assertFalse(CSRFUtil.validateToken(request));
    }

    @Test
    void testClearToken() {
        HttpSession session = mock(HttpSession.class);
        CSRFUtil.clearToken(session);
        Mockito.verify(session).removeAttribute("csrfToken");
    }

    @Test
    void testRotateTokenReplacesExistingToken() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("csrfToken")).thenReturn("old-token");

        String rotated = CSRFUtil.rotateToken(session);
        assertNotNull(rotated);
        assertNotEquals("old-token", rotated);
        assertTrue(rotated.matches("[0-9a-f-]{36}")); // UUID format
        Mockito.verify(session).setAttribute("csrfToken", rotated);

        // Subsequent reads return the rotated token.
        when(session.getAttribute("csrfToken")).thenReturn(rotated);
        assertEquals(rotated, CSRFUtil.getToken(session));
    }

    @Test
    void testGetToken() {
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("csrfToken")).thenReturn("existing-token");

        String token = CSRFUtil.getToken(session);
        assertEquals("existing-token", token);
    }
}