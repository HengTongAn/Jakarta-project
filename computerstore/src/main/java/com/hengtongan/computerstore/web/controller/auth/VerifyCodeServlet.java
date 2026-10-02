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
 * Step 2 of the forgot-password flow: customer enters the 6-digit code
 * sent to their email. If correct, they proceed to choose a new password.
 *
 * <p>Reached from {@code /forgot} after a code was emailed. The address being
 * waited on lives in {@link #SESSION_PENDING_EMAIL}, deliberately <em>not</em> in
 * {@link NewPasswordServlet#SESSION_EMAIL}: that one means "a code for this address
 * was verified" and is what unlocks the password change. Reusing it here to remember
 * which address was asked about would hand every visitor who typed an email a
 * session that can reach {@code /new-password} without ever proving anything.</p>
 */
@WebServlet("/verify-code")
public class VerifyCodeServlet extends BaseServlet {

    /**
     * Session attribute holding the address a code was emailed to.
     *
     * <p>Names the address the flow is <em>waiting</em> on. It proves nothing, and
     * nothing may be authorised from it.
     */
    static final String SESSION_PENDING_EMAIL = "passwordResetPendingEmail";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (currentUser(request) != null) {
            redirect(request, response, "/products");
            return;
        }
        HttpSession session = request.getSession(true);
        // The pending address is read from the session first and the query string
        // only as a fallback for someone who bookmarked the step. Keeping it in the
        // session is what lets /forgot redirect here without putting the address in
        // the URL, where it would sit in browser history and leak by Referer.
        String email = (String) session.getAttribute(SESSION_PENDING_EMAIL);
        if (email == null || email.isBlank()) {
            email = request.getParameter("email");
        }
        if (email == null || email.isBlank()) {
            redirect(request, response, "/forgot");
            return;
        }
        request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
        request.setAttribute("email", email);
        forward(request, response, "auth/verify-code.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        // Session first, form second. The form carries the address so the page can
        // render it, but the session is what the flow actually asked about, and a
        // tampered hidden field must not decide which account's code gets checked.
        // The code hash lookup is keyed on both, so this is a narrowing rather than
        // the control that prevents impersonation.
        String email = (String) session.getAttribute(SESSION_PENDING_EMAIL);
        if (email == null || email.isBlank()) {
            email = request.getParameter("email");
        }
        String code = request.getParameter("code");

        try {
            int codeId = app().passwordResetService().verifyCode(email, code);
            // The code id AND the address it was issued for are pinned to this
            // session. The reset step reads the account from the pinned address,
            // so the email posted to /new-password can never redirect the change
            // at a different account. Redirecting without the email in the query
            // string also keeps it out of browser history and referrer headers.
            session.setAttribute(NewPasswordServlet.SESSION_CODE_ID, codeId);
            session.setAttribute(NewPasswordServlet.SESSION_EMAIL, email.trim());
            // The flow is past the "waiting on a code" step. Leaving the pending
            // address behind would let a later /verify-code visit in this session
            // start comparing codes for an address the code was already spent on.
            session.removeAttribute(SESSION_PENDING_EMAIL);
            response.sendRedirect(request.getContextPath() + "/new-password");
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("email", email);
            request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
            forward(request, response, "auth/verify-code.jsp");
        }
    }
}
