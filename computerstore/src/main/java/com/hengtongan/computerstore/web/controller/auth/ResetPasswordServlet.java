package com.hengtongan.computerstore.web.controller.auth;

import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.PasswordResetToken;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.security.CSRFUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 2 of the forgot-password flow: opens the email link, verifies the token,
 * and lets the customer choose a brand-new password without knowing the old one.
 */
@WebServlet("/reset")
public class ResetPasswordServlet extends BaseServlet {

    /**
     * Per-IP throttle for token-validation GETs. Password-reset tokens are
     * 256-bit secrets, so guessing is cryptographically infeasible; this guard
     * exists so an unauthenticated flood of GET probes cannot turn the
     * index-backed token lookup into a cheap DoS against the database.
     */
    static final class ProbeThrottle {
        private static final long WINDOW_MS = 60_000L;
        private static final int MAX_ATTEMPTS = 20;
        private static final ConcurrentHashMap<String, long[]> ATTEMPTS = new ConcurrentHashMap<>();

        private ProbeThrottle() {
        }

        /** True when this IP still has budget for a token probe this window. */
        static boolean allow(String ip) {
            long now = System.currentTimeMillis();
            long[] state = ATTEMPTS.compute(ip, (k, v) -> {
                if (v == null || now - v[1] >= WINDOW_MS) {
                    return new long[]{1L, now};
                }
                v[0]++;
                return v;
            });
            // Opportunistic reaping keeps the map bounded to recently-active IPs.
            if (ATTEMPTS.size() > 1000) {
                ATTEMPTS.entrySet().removeIf(e -> now - e.getValue()[1] >= WINDOW_MS);
            }
            return state[0] <= MAX_ATTEMPTS;
        }

        static void clearAll() {
            ATTEMPTS.clear();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (currentUser(request) != null) {
            redirect(request, response, "/products");
            return;
        }
        String token = request.getParameter("token");
        if (token != null) {
            // Token probes are unauthenticated; cap them per IP so this GET can
            // never be used to hammer the token lookup in the database.
            if (!ProbeThrottle.allow(AuditLogger.clientIp(request))) {
                response.sendError(429, "Too many reset-link attempts. Please try again later.");
                return;
            }
        }
        if (app().passwordResetService().validateToken(token) == null) {
            request.setAttribute("invalid", true);
            forward(request, response, "auth/reset-password.jsp");
            return;
        }
        HttpSession session = request.getSession(true);
        request.setAttribute("token", token);
        request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
        forward(request, response, "auth/reset-password.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = request.getParameter("token");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");

        try {
            PasswordResetToken reset = app().passwordResetService().validateToken(token);
            if (reset == null) {
                throw new ValidationException("This link is invalid or has expired. Please request a new one.");
            }
            app().passwordResetService().completeReset(token, newPassword, confirmPassword);
            AuditLogger.logAuthEvent("PASSWORD_RESET_COMPLETED", null,
                    AuditLogger.clientIp(request), "Password changed via emailed reset link (user #"
                            + reset.getUserId() + ")");

            // Password reset is a high-value state change: discard any CSRF
            // token that predates it so a leaked token cannot be replayed.
            HttpSession session = request.getSession(false);
            if (session != null) {
                CSRFUtil.rotateToken(session);
            }

            redirect(request, response, "/login?reset=1");
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("token", token);
            HttpSession session = request.getSession(true);
            request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
            forward(request, response, "auth/reset-password.jsp");
        }
    }
}