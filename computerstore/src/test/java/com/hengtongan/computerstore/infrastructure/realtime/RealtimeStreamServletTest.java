package com.hengtongan.computerstore.infrastructure.realtime;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Topic authorisation and stream setup for {@code /realtime}.
 *
 * <p>Deliberately no test here asserts a broken-pipe path: {@code PrintWriter}
 * swallows {@code IOException}, so a mock that throws from {@code flush()} does
 * not reproduce how the container really signals an aborted handshake. That
 * behaviour is covered end-to-end against a live container instead.</p>
 */
class RealtimeStreamServletTest {

    private RealtimeStreamServlet servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private AsyncContext asyncContext;
    private HttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        servlet = new RealtimeStreamServlet();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        asyncContext = mock(AsyncContext.class);
        session = mock(HttpSession.class);

        when(request.getSession(false)).thenReturn(session);
        when(request.startAsync()).thenReturn(asyncContext);
        when(asyncContext.getRequest()).thenReturn(request);
        when(asyncContext.getResponse()).thenReturn(response);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    @Test
    void anonymousClientAskingOnlyForPrivateTopicsIsRefused() throws Exception {
        when(request.getParameter("topics")).thenReturn("orders");

        servlet.doGet(request, response);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(request, never()).startAsync();
    }

    @Test
    void anonymousClientIsDowngradedToThePublicTopic() throws Exception {
        when(request.getParameter("topics")).thenReturn("stock,orders");

        servlet.doGet(request, response);

        verify(request).startAsync();
        verify(asyncContext, never()).complete();
        verify(request).setAttribute(eq(EventHub.TOPICS_KEY), argThat(t -> t.equals(java.util.Set.of("stock"))));
    }

    @Test
    void successfulHandshakeLeavesTheStreamOpen() throws Exception {
        when(request.getParameter("topics")).thenReturn("stock");

        servlet.doGet(request, response);

        verify(request).startAsync();
        verify(asyncContext).setTimeout(0L);
        verify(response).setContentType("text/event-stream");
        verify(response).setHeader("Cache-Control", "no-cache");
        verify(asyncContext, never()).complete();
    }

    @Test
    void realtimeDisabledReturnsNoContentWithoutStartingAStream() throws Exception {
        System.setProperty(EventHub.GATE, "false");
        try {
            when(request.getParameter("topics")).thenReturn("stock");

            servlet.doGet(request, response);

            verify(response).setStatus(HttpServletResponse.SC_NO_CONTENT);
            verify(request, never()).startAsync();
        } finally {
            System.clearProperty(EventHub.GATE);
        }
    }
}
