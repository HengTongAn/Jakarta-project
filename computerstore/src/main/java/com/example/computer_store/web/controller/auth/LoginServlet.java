package com.example.computer_store.web.controller.auth;

import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.util.security.CSRFUtil;
import com.example.computer_store.infrastructure.security.TwoFactorAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@WebServlet("/login")
public class LoginServlet extends BaseServlet {

    private static final String PENDING_2FA_USER = "pending2faUser";
    private static final String PENDING_2FA_RETURN = "pending2faReturn";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (currentUser(request) != null) {
            redirect(request, response, "/products");
            return;
        }
        if ("1".equals(request.getParameter("logout"))) {
            request.setAttribute("info", "You have been logged out.");
        }
        if ("1".equals(request.getParameter("reset"))) {
            request.setAttribute("info", "Your password was updated. Please log in with your new password.");
        }
        HttpSession session = request.getSession(true);
        request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
        forward(request, response, "auth/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("verify2fa".equals(request.getParameter("action"))) {
            completeTwoFactorLogin(request, response);
            return;
        }

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String returnPath = request.getParameter("return");

        try {
            User user = app().authService().login(username, password, request);
            // Rotate the session identifier on authentication so an attacker
            // cannot reuse a session id obtained before the user logged in.
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession session = request.getSession(true);
            CSRFUtil.generateToken(session);

            if (TwoFactorAuthService.isTwoFactorEnabled(user.getUserId())) {
                session.setAttribute(PENDING_2FA_USER, user);
                session.setAttribute(PENDING_2FA_RETURN, returnPath);
                request.setAttribute("requiresTwoFactor", true);
                request.setAttribute("csrfToken", CSRFUtil.getToken(session));
                forward(request, response, "auth/login.jsp");
                return;
            }

            completeLogin(request, response, session, user, returnPath);
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("username", username);
            HttpSession session = request.getSession(true);
            request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
            forward(request, response, "auth/login.jsp");
        }
    }

    private void completeTwoFactorLogin(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute(PENDING_2FA_USER);
        if (user == null) {
            redirect(request, response, "/login");
            return;
        }
        String code = request.getParameter("code");
        if (code == null || !code.matches("\\d{6}")
                || !TwoFactorAuthService.verifyCode(user.getUserId(), Integer.parseInt(code))) {
            request.setAttribute("requiresTwoFactor", true);
            request.setAttribute("error", "Enter a valid authentication code.");
            request.setAttribute("csrfToken", CSRFUtil.getToken(session));
            forward(request, response, "auth/login.jsp");
            return;
        }
        String returnPath = (String) session.getAttribute(PENDING_2FA_RETURN);
        session.removeAttribute(PENDING_2FA_USER);
        session.removeAttribute(PENDING_2FA_RETURN);
        completeLogin(request, response, session, user, returnPath);
    }

    private void completeLogin(HttpServletRequest request, HttpServletResponse response,
                               HttpSession session, User user, String returnPath) throws IOException {
        session.setAttribute("user", user);
        String target = user.isAdmin() ? "/admin" : "/products";
        if (isSafeReturnPath(returnPath, request)) {
            target = returnPath;
        }
        redirect(request, response, target);
    }

    private boolean isSafeReturnPath(String path, HttpServletRequest request) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        try {
            URI uri = new URI(path);
            String host = uri.getHost();

            if (host == null) {
                return path.startsWith("/") && !path.startsWith("//");
            }
            return host.equals(request.getServerName())
                    && (uri.getScheme() == null || uri.getScheme().equals(request.getScheme()));
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
