package com.hengtongan.computerstore.infrastructure.realtime;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.hengtongan.computerstore.core.domain.entity.User;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-Sent Events stream. A page keeps one long-lived GET connection open
 * to {@code /realtime}; every committed data change is pushed to the open
 * tabs of all connected users. Optional {@code ?topics=stock,orders}
 * parameter narrows the subscription for pages that only care about one
 * dataset; the default subscribes to everything.
 *
 * <p>Stock is public (the shop pages show it to everyone), but order and cart
 * events carry per-user data, so subscribing to those requires a logged-in
 * session. Anonymous clients are silently downgraded to {@code stock}.
 */
@WebServlet(urlPatterns = "/realtime", asyncSupported = true)
public class RealtimeStreamServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Set<String> PUBLIC_TOPICS = Collections.singleton("stock");
    private static final Set<String> PRIVATE_TOPICS = Set.of("orders", "cart", "*");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        if (!EventHub.enabled()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        Set<String> requested = parseTopics(request.getParameter("topics"));
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        boolean authenticated = user != null;
        Set<String> topics = new HashSet<>();
        // Anonymous clients are downgraded to public topics only; asking for
        // nothing but private topics without a session is refused outright.
        boolean wantsAnythingPublic = requested.contains("stock") || requested.contains("*");
        for (String topic : requested) {
            if (PUBLIC_TOPICS.contains(topic) || (authenticated && PRIVATE_TOPICS.contains(topic))) {
                topics.add(topic);
            }
        }
        if (topics.isEmpty() && !wantsAnythingPublic) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (topics.isEmpty()) {
            topics = new HashSet<>(PUBLIC_TOPICS);
        }

        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        AsyncContext ac = request.startAsync();
        ac.setTimeout(0L);
        ac.getRequest().setAttribute(EventHub.TOPICS_KEY, topics);
        if (user != null) {
            ac.getRequest().setAttribute(EventHub.USER_ID_KEY, user.getUserId());
            ac.getRequest().setAttribute(EventHub.ADMIN_KEY, user.isAdmin());
        }
        EventHub.register(ac);

        try {
            ac.getResponse().setCharacterEncoding("UTF-8");
            ac.getResponse().getWriter().write("retry: 3000\n\n");
            ac.getResponse().getWriter().flush();
        } catch (IOException ignored) {
            // Client vanished during the handshake; the listener cleans up.
        }
    }

    private static Set<String> parseTopics(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.singleton("*");
        }
        Set<String> topics = new HashSet<>();
        for (String t : raw.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                topics.add(trimmed);
            }
        }
        return topics.isEmpty() ? Collections.singleton("*") : topics;
    }
}
