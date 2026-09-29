package com.hengtongan.computerstore.infrastructure.realtime;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * A browser closes its {@code EventSource} on every navigation, so dead streams
 * are routine. Each one has to be retired twice over: removed from the fan-out
 * set so no further event is written to it, and completed so the container
 * releases the request instead of holding it until the dead socket is noticed.
 *
 * <p>The status such a stream is logged with is deliberately not asserted here.
 * Tomcat stamps 500 while finalising a request whose peer vanished, after every
 * {@code AsyncListener} callback has returned, so no application code can change
 * it - these tests must not pretend otherwise.</p>
 */
class EventHubDisconnectTest {

    private final Set<AsyncContext> clients = clients();
    private final AtomicReference<AsyncListener> listener = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        clients.clear();
        listener.set(null);
        System.setProperty(EventHub.GATE, "true");
    }

    @AfterEach
    void tearDown() {
        clients.clear();
        System.clearProperty(EventHub.GATE);
    }

    @SuppressWarnings("unchecked")
    private static Set<AsyncContext> clients() {
        try {
            Field f = EventHub.class.getDeclaredField("CLIENTS");
            f.setAccessible(true);
            return (Set<AsyncContext>) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A healthy tracked stream, as returned by {@link EventHub#register}. */
    private AsyncContext registeredStream() throws IOException {
        HttpServletResponse resp = healthyResponse();
        AsyncContext ac = mock(AsyncContext.class);
        when(ac.getRequest()).thenReturn(mock(HttpServletRequest.class));
        when(ac.getResponse()).thenReturn(resp);
        doAnswer(i -> {
            listener.set(i.getArgument(0, AsyncListener.class));
            return null;
        }).when(ac).addListener(any(AsyncListener.class));

        EventHub.register(ac);
        assertTrue(clients.contains(ac), "a registered stream must be tracked");
        return ac;
    }

    private HttpServletResponse healthyResponse() throws IOException {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        return resp;
    }

    @Test
    void peerDisconnectCompletesTheRequestAndDropsItFromTheFanOutSet() throws IOException {
        AsyncContext ac = registeredStream();

        fire(ac, new IOException("Broken pipe"));

        verify(ac).complete();
        assertTrue(clients.isEmpty(), "a dead stream must not stay in the fan-out set");
    }

    @Test
    void disconnectWithNoReportedCauseIsStillRetired() throws IOException {
        AsyncContext ac = registeredStream();

        fire(ac, null);

        verify(ac).complete();
        assertTrue(clients.isEmpty());
    }

    @Test
    void aTimedOutStreamIsRetiredToo() throws IOException {
        AsyncContext ac = registeredStream();

        AsyncEvent event = mock(AsyncEvent.class);
        when(event.getAsyncContext()).thenReturn(ac);
        listener.get().onTimeout(event);

        verify(ac).complete();
        assertTrue(clients.isEmpty());
    }

    @Test
    void normalCompletionStopsTrackingWithoutCompletingAgain() throws IOException {
        AsyncContext ac = registeredStream();

        AsyncEvent event = mock(AsyncEvent.class);
        when(event.getAsyncContext()).thenReturn(ac);
        listener.get().onComplete(event);

        verify(ac, never()).complete();
        assertTrue(clients.isEmpty());
    }

    @Test
    void aStreamWhoseWriteFailedIsCompletedRatherThanLeftDangling() throws IOException {
        AsyncContext ac = registeredStream();
        closeResponse(ac);

        EventHub.publishStock(1, 5, "IN_STOCK");

        verify(ac).complete();
        assertTrue(clients.isEmpty(),
                "a stream whose write failed must leave the fan-out set");
    }

    @Test
    void aDeadStreamIsAlsoRetiredByTheHeartbeat() throws IOException {
        AsyncContext ac = registeredStream();
        closeResponse(ac);

        EventHub.heartbeat();

        verify(ac).complete();
        assertTrue(clients.isEmpty());
    }

    /** Simulates the container having already finished the stream's response. */
    private void closeResponse(AsyncContext ac) throws IOException {
        HttpServletResponse closed = mock(HttpServletResponse.class);
        when(closed.getWriter()).thenThrow(new IllegalStateException("response closed"));
        when(ac.getResponse()).thenReturn(closed);
    }

    @Test
    void aHealthyStreamKeepsReceivingEvents() throws IOException {
        AsyncContext ac = registeredStream();
        HttpServletResponse resp = (HttpServletResponse) ac.getResponse();

        EventHub.publishStock(1, 5, "IN_STOCK");

        assertTrue(clients.contains(ac), "a live stream must stay in the fan-out set");
        verify(resp, atLeastOnce()).getWriter();
        verify(ac, never()).complete();
    }

    /** Delivers an AsyncEvent to the listener the hub registered for {@code ac}. */
    private void fire(AsyncContext ac, Throwable t) throws IOException {
        AsyncEvent event = mock(AsyncEvent.class);
        when(event.getAsyncContext()).thenReturn(ac);
        when(event.getThrowable()).thenReturn(t);
        AsyncListener l = listener.get();
        assertTrue(l != null, "register() must attach a listener");
        l.onError(event);
    }
}
