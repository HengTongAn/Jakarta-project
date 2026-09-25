package com.hengtongan.computerstore.web.controller.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorServletTest {

    private ErrorServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;
    private StringWriter responseWriter;
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new ErrorServlet();

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);
        responseWriter = new StringWriter();
        printWriter = new PrintWriter(responseWriter);

        when(request.getSession(false)).thenReturn(session);
        when(request.getAttribute("jakarta.servlet.error.status_code")).thenReturn(500);
        when(request.getAttribute("jakarta.servlet.error.request_uri")).thenReturn("/test");
        when(request.getAttribute("jakarta.servlet.error.servlet_name")).thenReturn("TestServlet");
        when(request.getAttribute("jakarta.servlet.error.exception")).thenReturn(null);
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        when(response.getWriter()).thenReturn(printWriter);
        
        // Make stubbing lenient to avoid strict stubbing issues
        Mockito.lenient().when(request.getAttribute(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        Mockito.framework().clearInlineMocks();
    }

    @Test
    void test500ErrorForwardsToErrorPage() throws Exception {
        when(request.getAttribute("jakarta.servlet.error.status_code")).thenReturn(500);

        servlet.doGet(request, response);

        verify(request).getRequestDispatcher("/WEB-INF/views/errors/500.jsp");
        verify(dispatcher).forward(request, response);
        verify(response).setStatus(500);
    }

    @Test
    void test404ErrorForwardsToErrorPage() throws Exception {
        when(request.getAttribute("jakarta.servlet.error.status_code")).thenReturn(404);

        servlet.doGet(request, response);

        verify(request).getRequestDispatcher("/WEB-INF/views/errors/404.jsp");
    }

    @Test
    void test403ErrorForwardsToErrorPage() throws Exception {
        when(request.getAttribute("jakarta.servlet.error.status_code")).thenReturn(403);

        servlet.doGet(request, response);

        verify(request).getRequestDispatcher("/WEB-INF/views/errors/403.jsp");
    }

    @Test
    void testJsonResponseForAjaxRequest() throws Exception {
        when(request.getHeader("Accept")).thenReturn("application/json");
        when(request.getAttribute("jakarta.servlet.error.status_code")).thenReturn(400);

        servlet.doGet(request, response);

        String responseBody = responseWriter.toString();
        assertTrue(responseBody.contains("\"error\""));
        assertTrue(responseBody.contains("\"status\":400"));
    }

    @Test
    void testCorrelationIdInRequest() throws Exception {
        when(request.getAttribute("correlationId")).thenReturn("test-correlation-123");
        when(request.getAttribute("jakarta.servlet.error.status_code")).thenReturn(500);

        servlet.doGet(request, response);

        verify(request).setAttribute(eq("correlationId"), eq("test-correlation-123"));
    }

    @Test
    void testEscapeJson() throws Exception {
        var method = ErrorServlet.class.getDeclaredMethod("escapeJson", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(servlet, "test\"with\nspecial\tt chars");
        assertTrue(result.contains("\\\""));
        assertTrue(result.contains("\\n"));
        assertTrue(result.contains("\\t"));
    }
}