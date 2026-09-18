package com.example.computer_store.controller.auth;

import com.example.computer_store.controller.base.BaseServlet;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.User;
import com.example.computer_store.util.CSRFUtil;
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
        HttpSession session = request.getSession(true);
        request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
        forward(request, response, "auth/login.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
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
            session.setAttribute("user", user);
            CSRFUtil.generateToken(session);

            String target = user.isAdmin() ? "/admin" : "/products";
            if (isSafeReturnPath(returnPath, request)) {
                target = returnPath;
            }
            redirect(request, response, target);
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("username", username);
            HttpSession session = request.getSession(true);
            request.setAttribute("csrfToken", CSRFUtil.generateToken(session));
            forward(request, response, "auth/login.jsp");
        }
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
