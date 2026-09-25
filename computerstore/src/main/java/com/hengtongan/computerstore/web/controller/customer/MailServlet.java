package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.MailMessage;
import com.hengtongan.computerstore.core.domain.entity.User;
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

/**
 * Gmail-style customer mailbox: inbox, sent, compose and reply.
 */
@MultipartConfig
@WebServlet({"/mail", "/mail/sent", "/mail/compose", "/mail/view", "/mail/toggle", "/mail/read-all"})
public class MailServlet extends BaseServlet {
    private boolean isSentView(HttpServletRequest request) {
        return "/mail/sent".equals(request.getServletPath());
    }

    private void listMail(HttpServletRequest request, HttpServletResponse response, User user)
            throws ServletException, IOException {
        if (isSentView(request)) {
            request.setAttribute("messages", app().mailService().getSent(user.getUserId()));
            request.setAttribute("folder", "sent");
            request.getRequestDispatcher("/WEB-INF/views/customer/mail/sent.jsp").forward(request, response);
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
            request.getRequestDispatcher("/WEB-INF/views/customer/mail/inbox.jsp").forward(request, response);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        switch (request.getServletPath()) {
            case "/mail/sent" -> listMail(request, response, user);
            case "/mail/compose" -> compose(request, response, user);
            case "/mail/view" -> view(request, response, user);
            default -> listMail(request, response, user);
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
                response.sendRedirect(request.getContextPath() + "/mail");
                return;
            }
        } else {
            request.setAttribute("recipients", app().userService().getAdmins());
        }
        request.getRequestDispatcher("/WEB-INF/views/customer/mail/compose.jsp").forward(request, response);
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
            request.getRequestDispatcher("/WEB-INF/views/customer/mail/view.jsp").forward(request, response);
        } catch (NotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        String path = request.getServletPath();

        if ("/mail/compose".equals(path)) {
            Integer replyTo = ValidationUtil.parseInt(request.getParameter("replyTo"));
            try {
                MailMessage created;
                if (replyTo != null) {
                    created = app().mailService().replyTo(user.getUserId(), replyTo, request.getParameter("body"));
                    Flash.success(request, "Reply sent.");
                    response.sendRedirect(request.getContextPath() + "/mail/view?id=" + created.getMessageId());
                } else {
                    Integer recipientId = ValidationUtil.parseInt(request.getParameter("recipientId"));
                    if (recipientId == null) {
                        throw new ValidationException("Please choose a recipient.");
                    }
                    created = app().mailService().send(user.getUserId(), recipientId,
                            request.getParameter("subject"), request.getParameter("body"));
                    Flash.success(request, "Message sent.");
                    response.sendRedirect(request.getContextPath() + "/mail/sent");
                }
            } catch (ValidationException | NotFoundException e) {
                Flash.error(request, e.getMessage());
                String back = replyTo != null ? "/mail/compose?replyTo=" + replyTo : "/mail/compose";
                response.sendRedirect(request.getContextPath() + back);
            }
        } else if ("/mail/read-all".equals(path)) {
            app().mailService().markAllRead(user.getUserId());
            Flash.success(request, "All messages marked as read.");
            response.sendRedirect(request.getContextPath() + "/mail");
        } else if ("/mail/toggle".equals(path)) {
            Integer id = ValidationUtil.parseInt(request.getParameter("id"));
            if (id == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            try {
                MailMessage message = app().mailService().toggleRead(id, user.getUserId());
                response.sendRedirect(request.getContextPath() + "/mail/view?id=" + message.getMessageId());
            } catch (NotFoundException e) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}
