package com.example.computer_store.web.controller.customer;

import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.util.web.AuditLogger;
import com.example.computer_store.util.security.CSRFUtil;
import com.example.computer_store.util.web.Flash;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/** Password and account-security settings for the signed-in customer. */
public class SecuritySettingsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/customer/account/security-settings.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String currentPassword = request.getParameter("currentPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        try {
            if (newPassword == null || !newPassword.equals(confirmPassword)) {
                throw new ValidationException("New password and confirmation do not match.");
            }
            app().userService().changePassword(user.getUserId(), currentPassword, newPassword);
            AuditLogger.logAuthEvent("PASSWORD_CHANGE", user.getUsername(), AuditLogger.clientIp(request),
                    "Password changed");
            Flash.success(request, "Your password was updated successfully.");
            // Rotate the CSRF token after a high-value state change so a token
            // leaked before the password change cannot be replayed.
            CSRFUtil.rotateToken(session);
        } catch (ValidationException e) {
            Flash.error(request, e.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/account/settings");
    }
}
