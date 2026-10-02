package com.hengtongan.computerstore.web.controller.auth;

import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.infrastructure.messaging.EmailUtil;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.security.CSRFUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Step 1 of the forgot-password flow: the customer enters the email their
 * account uses. The response is identical whether or not the account exists (no
 * account enumeration).
 *
 * <p>Two ways out from here, chosen by the form's {@code method} field:</p>
 * <ul>
 *   <li><b>code</b> (the default, and what the button sends) emails a 6-digit
 *       code and redirects to {@code /verify-code}, which is how Google and most
 *       banks do it. The address is pinned to the session on the way so it never
 *       has to travel in a query string.</li>
 *   <li><b>link</b> emails a single-use link to {@code /reset} instead, for a
 *       customer who would rather click than type a code.</li>
 * </ul>
 *
 * <p>The link flow is not the default because it leaves a live token in the
 * recipient's inbox and in any mail relay's logs, while the code flow leaves a
 * 10-minute, 5-attempt secret that is useless once spent.</p>
 */
@WebServlet("/forgot")
public class ForgotPasswordServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (currentUser(request) != null) {
            redirect(request, response, "/products");
            return;
        }
        HttpSession session = request.getSession(true);
        request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
        request.setAttribute("mailConfigured", EmailUtil.isConfigured());
        forward(request, response, "auth/forgot-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = request.getParameter("email");
        // Absent means "code", not "link": the code flow is the primary one and a
        // form that forgets the field must not silently fall back to the weaker one.
        boolean wantsLink = "link".equals(request.getParameter("method"));

        HttpSession session = request.getSession(true);
        try {
            if (wantsLink) {
                app().passwordResetService().requestReset(email, baseUrl(request));
                AuditLogger.logAuthEvent("PASSWORD_RESET_REQUESTED", email,
                        AuditLogger.clientIp(request), "Reset link requested via /forgot");
                request.setAttribute("info",
                        "If an account exists for that email, a reset link has been sent. Please check your inbox.");
            } else {
                app().passwordResetService().requestResetWithCode(email, baseUrl(request));
                AuditLogger.logAuthEvent("PASSWORD_RESET_CODE_REQUESTED", email,
                        AuditLogger.clientIp(request), "Reset code requested via /forgot");
                // Pinned before the redirect so /verify-code can show which address
                // it is asking about without the address being in the URL. This
                // records only that a code was *sent*; it grants nothing, which is
                // why it is a different key from NewPasswordServlet.SESSION_EMAIL.
                session.setAttribute(VerifyCodeServlet.SESSION_PENDING_EMAIL, email.trim());
                flashSuccess(request, "If an account exists for that email, we've sent a 6-digit code to it.");
                redirect(request, response, "/verify-code");
                return;
            }
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("email", email);
        }
        request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
        forward(request, response, "auth/forgot-password.jsp");
    }

    /**
     * Absolute base URL for the reset link, e.g. http://localhost:8080/computerstore
     * The {@code Host} header is never trusted blindly (host-header poisoning);
     * only loopback hosts or an explicitly configured base URL
     * ({@code -Dcomputerstore.app.baseUrl=https://shop.example.com/computerstore})
     * are accepted, everything else falls back to localhost.
     */
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