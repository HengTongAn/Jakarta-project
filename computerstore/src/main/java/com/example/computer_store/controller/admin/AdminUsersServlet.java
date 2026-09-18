package com.example.computer_store.controller.admin;

import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.User;
import com.example.computer_store.util.AuditLogger;
import com.example.computer_store.util.FileUploadUtil;
import com.example.computer_store.util.Flash;
import com.example.computer_store.util.UploadConfig;
import com.example.computer_store.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import com.example.computer_store.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;

/**
 * Admin user / team management:
 *   GET  /admin/users                    -> user table
 *   GET  /admin/users?action=new         -> create form
 *   GET  /admin/users?action=edit&id=ID  -> edit form
 *   GET  /admin/users?action=reset&id=ID -> password reset form
 *   GET  /admin/users?action=profile&id=ID -> profile (with live/not-live status)
 *   POST /admin/users                    -> create, update or reset password
 */
@WebServlet("/admin/users")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 5 * 1024 * 1024,
        maxRequestSize = 10 * 1024 * 1024
)
public class AdminUsersServlet extends BaseServlet {

    private static final long PRESENCE_WINDOW_MS = 5 * 60_000; // 5 minutes

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        Integer userId = ValidationUtil.parseInt(request.getParameter("id"));
        if ("new".equals(action)) {
            forwardForm(request, response, null);
            return;
        }
        if (("edit".equals(action) || "reset".equals(action)) && userId != null) {
            try {
                User target = app().userService().get(userId);
                if ("edit".equals(action)) {
                    forwardForm(request, response, target);
                } else {
                    request.setAttribute("mode", "reset");
                    request.setAttribute("targetUser", target);
                    request.getRequestDispatcher("/WEB-INF/views/admin/user-form.jsp").forward(request, response);
                }
                return;
            } catch (NotFoundException e) {
                Flash.error(request, e.getMessage());
                response.sendRedirect(request.getContextPath() + "/admin/users");
                return;
            }
        }
        if ("profile".equals(action) && userId != null) {
            try {
                User target = app().userService().get(userId);
                request.setAttribute("profileUser", target);
                request.setAttribute("onlineThreshold", System.currentTimeMillis() - PRESENCE_WINDOW_MS);
                request.getRequestDispatcher("/WEB-INF/views/admin/user-profile.jsp").forward(request, response);
                return;
            } catch (NotFoundException e) {
                Flash.error(request, e.getMessage());
                response.sendRedirect(request.getContextPath() + "/admin/users");
                return;
            }
        }
        request.setAttribute("users", app().userService().getAll());
        request.setAttribute("onlineThreshold", System.currentTimeMillis() - PRESENCE_WINDOW_MS);
        request.getRequestDispatcher("/WEB-INF/views/admin/users.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User actor = (User) request.getSession().getAttribute("user");
        String actorUsername = actor != null ? actor.getUsername() : "UNKNOWN";
        String action = request.getParameter("action");
        Integer userId = ValidationUtil.parseInt(request.getParameter("userId"));
        String avatarUrl = null;

        try {
            Part avatarPart = request.getPart("avatarFile");
            if (avatarPart != null && avatarPart.getSize() > 0 && ("create".equals(action) || "update".equals(action))) {
                avatarUrl = FileUploadUtil.saveUploadedFile(avatarPart, UploadConfig.getUploadBasePath(), "avatars");
            }
            if ("create".equals(action)) {
                User created = app().userService().create(
                        request.getParameter("username"),
                        request.getParameter("fullName"),
                        request.getParameter("email"),
                        request.getParameter("password"),
                        request.getParameter("confirmPassword"),
                        parseRole(request.getParameter("role")));
                if (avatarUrl != null) {
                    app().userService().updateAvatar(created.getUserId(), avatarUrl);
                }
                AuditLogger.logAdminAction("USER_CREATE", actorUsername, "user",
                        "Created account '" + request.getParameter("username") + "' role=" + request.getParameter("role"));
                Flash.success(request, "User account created successfully.");
            } else if ("update".equals(action) && userId != null) {
                app().userService().updateProfile(
                        actor == null ? 0 : actor.getUserId(),
                        userId,
                        request.getParameter("username"),
                        request.getParameter("fullName"),
                        request.getParameter("email"),
                        parseRole(request.getParameter("role")));
                if (avatarUrl != null) {
                    User previous = app().userService().get(userId);
                    app().userService().updateAvatar(userId, avatarUrl);
                    if (previous.getAvatarUrl() != null && !previous.getAvatarUrl().isBlank()) {
                        FileUploadUtil.deleteFile(previous.getAvatarUrl(), UploadConfig.getUploadBasePath(), "avatars");
                    }
                }
                AuditLogger.logAdminAction("USER_UPDATE", actorUsername, "user #" + userId,
                        "Updated profile of '" + request.getParameter("username") + "'");
                Flash.success(request, "User account updated successfully.");
            } else if ("reset".equals(action) && userId != null) {
                app().userService().resetPassword(
                        userId,
                        request.getParameter("newPassword"),
                        request.getParameter("confirmPassword"));
                AuditLogger.logAdminAction("USER_PASSWORD_RESET", actorUsername, "user #" + userId,
                        "Admin reset the password (no old password required)");
                Flash.success(request, "Password reset successfully.");
            } else {
                throw new ValidationException("Unrecognized action.");
            }
            response.sendRedirect(request.getContextPath() + "/admin/users");
        } catch (ValidationException | NotFoundException e) {
            if (avatarUrl != null) {
                FileUploadUtil.deleteFile(avatarUrl, UploadConfig.getUploadBasePath(), "avatars");
            }
            request.setAttribute("error", e.getMessage());
            request.setAttribute("mode", action);
            if (userId != null && ("update".equals(action) || "reset".equals(action))) {
                try {
                    request.setAttribute("targetUser", app().userService().get(userId));
                } catch (NotFoundException ignored) {
                    // target user no longer exists; just re-render the form with the error
                }
            }
            request.setAttribute("formUsername", request.getParameter("username"));
            request.setAttribute("formFullName", request.getParameter("fullName"));
            request.setAttribute("formEmail", request.getParameter("email"));
            request.setAttribute("formRole", request.getParameter("role"));
            request.getRequestDispatcher("/WEB-INF/views/admin/user-form.jsp").forward(request, response);
        } catch (IOException | ServletException | IllegalStateException e) {
            if (avatarUrl != null) {
                FileUploadUtil.deleteFile(avatarUrl, UploadConfig.getUploadBasePath(), "avatars");
            }
            request.setAttribute("error", "Avatar upload failed: " + e.getMessage());
            request.setAttribute("mode", action);
            if (userId != null && "update".equals(action)) {
                try {
                    request.setAttribute("targetUser", app().userService().get(userId));
                } catch (NotFoundException ignored) {
                    // The edit target disappeared while the request was being processed.
                }
            }
            request.setAttribute("formUsername", request.getParameter("username"));
            request.setAttribute("formFullName", request.getParameter("fullName"));
            request.setAttribute("formEmail", request.getParameter("email"));
            request.setAttribute("formRole", request.getParameter("role"));
            request.getRequestDispatcher("/WEB-INF/views/admin/user-form.jsp").forward(request, response);
        }
    }

    private void forwardForm(HttpServletRequest request, HttpServletResponse response,
                             User user) throws ServletException, IOException {
        request.setAttribute("mode", user == null ? "create" : "update");
        if (user != null) {
            request.setAttribute("targetUser", user);
        }
        request.getRequestDispatcher("/WEB-INF/views/admin/user-form.jsp").forward(request, response);
    }

    private User.Role parseRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new ValidationException("Please select a role.");
        }
        try {
            return User.Role.valueOf(role.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid role selected.");
        }
    }
}
