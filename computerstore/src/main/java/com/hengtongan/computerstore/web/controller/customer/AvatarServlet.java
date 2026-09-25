package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.file.FileUploadUtil;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.util.file.UploadConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;

/**
 * Handles avatar uploads for logged-in customers and admins.
 * The file is saved under ~/.computerstore/uploads/avatars/ and the
 * relative path is stored in users.avatar_url.
 */
@WebServlet("/account/avatar")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 5 * 1024 * 1024,
        maxRequestSize = 10 * 1024 * 1024
)
public class AvatarServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");

        if (user == null) {
            Flash.error(request, "Please log in first.");
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        String avatarUrl = null;
        try {
            Part filePart = request.getPart("avatarFile");
            if (filePart == null || filePart.getSize() == 0) {
                Flash.error(request, "Please choose an image to upload.");
                response.sendRedirect(request.getContextPath() + "/account/profile");
                return;
            }
            String basePath = UploadConfig.getUploadBasePath();
            // save new avatar  (saveUploadedFile returns "avatars/uuid.ext")
            avatarUrl = FileUploadUtil.saveUploadedFile(filePart, basePath, "avatars");

            // update db + session
            app().userService().updateAvatar(user.getUserId(), avatarUrl);
            String previousAvatarUrl = user.getAvatarUrl();
            user.setAvatarUrl(avatarUrl);

            // The replacement is confirmed, so it is now safe to remove the old file.
            if (previousAvatarUrl != null && !previousAvatarUrl.isEmpty()) {
                FileUploadUtil.deleteFile(previousAvatarUrl, basePath, "avatars");
            }

            AuditLogger.logDataModification("AVATAR_UPDATE", user.getUsername(), "USER",
                    String.valueOf(user.getUserId()), "Profile picture updated");
            Flash.success(request, "Profile picture updated.");
        } catch (IOException | ServletException | IllegalStateException e) {
            Flash.error(request, "Could not save image: " + e.getMessage());
        } catch (RuntimeException e) {
            // Do not leave an orphan file when the database update fails.
            if (avatarUrl != null) {
                FileUploadUtil.deleteFile(avatarUrl, UploadConfig.getUploadBasePath(), "avatars");
            }
            Flash.error(request, "Could not update your profile picture. Please try again.");
        }

        response.sendRedirect(request.getContextPath() + "/account/profile");
    }
}
