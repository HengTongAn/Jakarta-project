package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.MailMessage;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.cache.CountCache;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Gmail-style admin mailbox: inbox of customer messages, sent, compose and reply.
 */
@MultipartConfig
@WebServlet({"/admin/mail", "/admin/mail/sent", "/admin/mail/compose", "/admin/mail/view", "/admin/mail/toggle", "/admin/mail/read-all", "/admin/mail/json"})
public class AdminMailServlet extends BaseServlet {

    private static final int SNIPPET_LENGTH = 90;
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        switch (request.getServletPath()) {
            case "/admin/mail/sent" -> listMail(request, response, user, true);
            case "/admin/mail/compose" -> compose(request, response, user);
            case "/admin/mail/view" -> view(request, response, user);
            case "/admin/mail/json" -> json(request, response, user);
            default -> listMail(request, response, user, false);
        }
    }

    private void listMail(HttpServletRequest request, HttpServletResponse response, User user, boolean sent)
            throws ServletException, IOException {
        if (sent) {
            request.setAttribute("messages", app().mailService().getSent(user.getUserId()));
            request.setAttribute("folder", "sent");
            request.getRequestDispatcher("/WEB-INF/views/admin/mail/sent.jsp").forward(request, response);
        } else {
            String filter = request.getParameter("filter");
            Boolean readOnly = "unread".equals(filter) ? Boolean.FALSE
                    : "read".equals(filter) ? Boolean.TRUE : null;
            request.setAttribute("filter", readOnly == null ? "all" : readOnly ? "read" : "unread");
            request.setAttribute("messages", app().mailService().getInbox(user.getUserId(), readOnly));
            int unread = app().mailService().countUnread(user.getUserId());
            request.setAttribute("unreadCount", unread);
            request.setAttribute("readCount", Math.max(0, app().mailService().getInbox(user.getUserId()).size() - unread));
            request.setAttribute("folder", "inbox");
            request.getRequestDispatcher("/WEB-INF/views/admin/mail/inbox.jsp").forward(request, response);
        }
    }

    private void compose(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {
        Integer replyTo = ValidationUtil.parseInt(request.getParameter("replyTo"));
        if (replyTo != null) {
            try {
                MailMessage original = app().mailService().getMessageForUser(replyTo, user.getUserId());
                request.setAttribute("replyTo", replyTo);
                request.setAttribute("original", original);
                request.setAttribute("replySubject", "Re: " + original.getSubject());
            } catch (NotFoundException e) {
                Flash.error(request, e.getMessage());
                response.sendRedirect(request.getContextPath() + "/admin/mail");
                return;
            }
        } else {
            request.setAttribute("recipients", app().userService().getCustomers());
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/mail/compose.jsp").forward(request, response);
    }

    private void view(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {
        Integer id = ValidationUtil.parseInt(request.getParameter("id"));
        if (id == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            MailMessage message = app().mailService().getMessageForUser(id, user.getUserId());
            request.setAttribute("m", message);
            request.setAttribute("isRecipient", message.getRecipientId() == user.getUserId());
            request.setAttribute("profile", app().userService().get(message.getSenderId()));
            request.getRequestDispatcher("/WEB-INF/views/admin/mail/view.jsp").forward(request, response);
        } catch (NotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void json(HttpServletRequest request, HttpServletResponse response, User user) throws IOException {
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        String path = request.getServletPath();
        if ("/admin/mail/toggle".equals(path)) {
            Integer id = ValidationUtil.parseInt(request.getParameter("id"));
            if (id == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            try {
                MailMessage message = app().mailService().toggleRead(id, user.getUserId());
                CountCache.invalidate(request.getSession(false), "mail");
                response.sendRedirect(request.getContextPath() + "/admin/mail/view?id=" + message.getMessageId());
            } catch (NotFoundException e) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }
        if ("/admin/mail/read-all".equals(path)) {
            app().mailService().markAllRead(user.getUserId());
            CountCache.invalidate(request.getSession(false), "mail");
            Flash.success(request, "All messages marked as read.");
            response.sendRedirect(request.getContextPath() + "/admin/mail");
            return;
        }
        if (!"/admin/mail/compose".equals(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Integer replyTo = ValidationUtil.parseInt(request.getParameter("replyTo"));
        try {
            MailMessage created;
            if (replyTo != null) {
                created = app().mailService().replyTo(user.getUserId(), replyTo, request.getParameter("body"));
                AuditLogger.logAdminAction("MAIL_REPLY", user.getUsername(),
                        "message #" + replyTo, "Admin replied to a customer message");
                Flash.success(request, "Reply sent.");
                response.sendRedirect(request.getContextPath() + "/admin/mail/view?id=" + created.getMessageId());
            } else {
                Integer recipientId = ValidationUtil.parseInt(request.getParameter("recipientId"));
                if (recipientId == null) {
                    throw new ValidationException("Please choose a recipient.");
                }
                created = app().mailService().send(user.getUserId(), recipientId,
                        request.getParameter("subject"), request.getParameter("body"));
                AuditLogger.logAdminAction("MAIL_SEND", user.getUsername(),
                        "user #" + recipientId, "Admin sent message '" + request.getParameter("subject") + "'");
                Flash.success(request, "Message sent.");
                response.sendRedirect(request.getContextPath() + "/admin/mail/sent");
            }
        } catch (ValidationException | NotFoundException e) {
            Flash.error(request, e.getMessage());
            String back = replyTo != null ? "/admin/mail/compose?replyTo=" + replyTo : "/admin/mail/compose";
            response.sendRedirect(request.getContextPath() + back);
        }
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}
