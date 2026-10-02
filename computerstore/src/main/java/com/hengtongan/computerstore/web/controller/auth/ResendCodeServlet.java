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
 * Allows the customer to request a new 6-digit code if they didn't receive it.
 */
@WebServlet("/resend-code")
public class ResendCodeServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(true);
        // Session first, form second, for the same reason /verify-code does it: the
        // hidden field is a convenience for rendering, not the authority on which
        // address gets a new code.
        String email = (String) session.getAttribute(VerifyCodeServlet.SESSION_PENDING_EMAIL);
        if (email == null || email.isBlank()) {
            email = request.getParameter("email");
        }

        try {
            app().passwordResetService().requestResetWithCode(email, baseUrl(request));
            AuditLogger.logAuthEvent("PASSWORD_CODE_RESENT", email,
                    AuditLogger.clientIp(request), "Password reset code resent via /resend-code");
            // Re-pin it: requestResetWithCode invalidates every prior code for this
            // address, and the session must still name the address being waited on.
            session.setAttribute(VerifyCodeServlet.SESSION_PENDING_EMAIL, email.trim());
            flashSuccess(request, "A new code has been sent to your email. Please check your inbox.");
        } catch (ValidationException e) {
            flashError(request, e.getMessage());
        }
        // No email in the query string: it would sit in browser history and go out
        // in the Referer header to anything the page loads.
        redirect(request, response, "/verify-code");
    }

    private String baseUrl(HttpServletRequest request) {
        String configured = System.getProperty("computerstore.app.baseUrl");
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/+$", "");
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if (host == null || !isTrustedHost(host)) {
            host = "localhost";
            scheme = "http";
            port = 8080;
        }
        String base = scheme + "://" + host;
        if (!("http".equals(scheme) && port == 80) && !("https".equals(scheme) && port == 443)) {
            base += ":" + port;
        }
        return base + request.getContextPath();
    }

    private boolean isTrustedHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || host.toLowerCase().endsWith(".localhost");
    }
}
