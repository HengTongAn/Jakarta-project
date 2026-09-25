package com.example.computer_store.web.controller.auth;

import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.util.web.AuditLogger;
import com.example.computer_store.util.security.CSRFUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Step 1 of the forgot-password flow: the customer enters the email their
 * account uses and we send a reset link. The response is identical whether or
 * not the account exists (no account enumeration).
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
        forward(request, response, "auth/forgot-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = request.getParameter("email");

        try {
            app().passwordResetService().requestReset(email, baseUrl(request));
            AuditLogger.logAuthEvent("PASSWORD_RESET_REQUESTED", email,
                    AuditLogger.clientIp(request), "Reset link requested via /forgot");
            request.setAttribute("info",
                    "If an account exists for that email, a reset link has been sent. Please check your inbox.");
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("email", email);
        }
        HttpSession session = request.getSession(true);
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