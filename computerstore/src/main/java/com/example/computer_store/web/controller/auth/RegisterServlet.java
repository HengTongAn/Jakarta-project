package com.example.computer_store.web.controller.auth;

import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.util.web.AuditLogger;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.example.computer_store.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebServlet("/register")
public class RegisterServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (sessionUser(request) != null) {
            response.sendRedirect(request.getContextPath() + "/products");
            return;
        }
        request.getRequestDispatcher("/WEB-INF/views/auth/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String username = request.getParameter("username");
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");

        try {
            User user = app().authService().register(username, fullName, email, password, confirmPassword);
            // Public registration can start with an anonymous session too;
            // rotate it before attaching an authenticated principal.
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute("user", user);
            session.setAttribute("flashMessage", "Welcome, " + user.getFullName() + "! Your account was created.");
            session.setAttribute("flashType", "success");
            AuditLogger.logAuthEvent("REGISTER", user.getUsername(), AuditLogger.clientIp(request),
                    "Account created");
            response.sendRedirect(request.getContextPath() + "/products");
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            request.setAttribute("username", username);
            request.setAttribute("fullName", fullName);
            request.setAttribute("email", email);
            request.getRequestDispatcher("/WEB-INF/views/auth/register.jsp").forward(request, response);
        }
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}
