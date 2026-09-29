package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.domain.entity.MailMessage;
import com.hengtongan.computerstore.core.domain.entity.User;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Lightweight JSON endpoint for the live mailbox updates used by
 * {@code assets/js/mail-live.js}: returns the unread count plus the current
 * inbox list (newest first). Covered by the existing {@code /mail/*} auth mapping.
 */
@WebServlet("/mail/json")
public class MailJsonServlet extends BaseServlet {

    private static final int SNIPPET_LENGTH = 90;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User user = sessionUser(request);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String filter = request.getParameter("filter");
        Boolean readOnly = "unread".equals(filter) ? Boolean.FALSE
                : "read".equals(filter) ? Boolean.TRUE : null;
        List<MailMessage> inbox = app().mailService().getInbox(user.getUserId(), readOnly);
        int unread = app().mailService().countUnread(user.getUserId());

        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        out.write("{\"unread\":" + unread + ",\"messages\":[");
        for (int i = 0; i < inbox.size(); i++) {
            MailMessage m = inbox.get(i);
            if (i > 0) {
                out.write(",");
            }
            out.write("{");
            out.write("\"id\":" + m.getMessageId() + ",");
            out.write("\"senderName\":\"" + esc(m.getSenderName()) + "\",");
            out.write("\"senderAvatarUrl\":\"" + esc(m.getSenderAvatarUrl()) + "\",");
            out.write("\"subject\":\"" + esc(m.getSubject()) + "\",");
            out.write("\"snippet\":\"" + esc(snippet(m.getBody())) + "\",");
            out.write("\"read\":" + (m.isReadFlag() ? "true" : "false") + ",");
            out.write("\"time\":" + m.getCreatedAt().getTime());
            out.write("}");
        }
        out.write("]}");
    }

    private static String snippet(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        return body.length() > SNIPPET_LENGTH ? body.substring(0, SNIPPET_LENGTH) + "…" : body;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}
