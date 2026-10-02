package com.hengtongan.computerstore.web.controller.auth;

import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.security.CSRFUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Step 3 of the forgot-password flow: customer enters their new password
 * after successfully verifying the 6-digit code.
 *
 * <p>Reachable only through a session that already verified a code: both the
 * code id and the address it was issued for come from the session, never from
 * the request, so this page cannot be used to reset an account whose code the
 * caller never received.</p>
 */
@WebServlet("/new-password")
public class NewPasswordServlet extends BaseServlet {

    /** Session attribute holding the address whose code was verified. */
    static final String SESSION_EMAIL = "passwordResetEmail";

    /** Session attribute holding the id of the verified code. */
    static final String SESSION_CODE_ID = "passwordResetCodeId";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (currentUser(request) != null) {
            redirect(request, response, "/products");
            return;
        }
        HttpSession session = request.getSession(false);
        String email = session == null ? null : (String) session.getAttribute(SESSION_EMAIL);
        if (email == null || email.isBlank()) {
            redirect(request, response, "/forgot");
            return;
        }
        request.setAttribute("csrfToken", CSRFUtil.generateToken(request.getSession(true)));
        request.setAttribute("email", email);
        forward(request, response, "auth/new-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        HttpSession session = request.getSession(false);

        try {
            Integer codeId = session == null ? null : (Integer) session.getAttribute(SESSION_CODE_ID);
            // The verified address is deliberately NOT read from the request:
            // it is the one the code was actually issued for.
            String verifiedEmail = session == null ? null : (String) session.getAttribute(SESSION_EMAIL);
            if (codeId == null || verifiedEmail == null) {
                throw new ValidationException("Session expired. Please start the password reset process again.");
            }
            app().passwordResetService().completeResetWithCode(codeId, verifiedEmail, newPassword, confirmPassword);
            session.removeAttribute(SESSION_CODE_ID);
            session.removeAttribute(SESSION_EMAIL);
            AuditLogger.logAuthEvent("PASSWORD_RESET_COMPLETED", verifiedEmail,
                    AuditLogger.clientIp(request), "Password reset completed via code verification");
            flashSuccess(request, "Your password has been updated. Please log in with your new password.");
            redirect(request, response, "/login");
        } catch (ValidationException e) {
            String email = session == null ? null : (String) session.getAttribute(SESSION_EMAIL);
            request.setAttribute("error", e.getMessage());
            request.setAttribute("email", email);
            HttpSession newSession = request.getSession(true);
            request.setAttribute("csrfToken", CSRFUtil.generateToken(newSession));
            forward(request, response, "auth/new-password.jsp");
        }
    }
}
