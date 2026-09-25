package com.example.computer_store.web.controller.customer;

import com.example.computer_store.util.file.UploadConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Serves user avatar images from the external avatars upload directory.
 */
@WebServlet("/avatars/*")
public class AvatarImageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.contains("..")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String fileName = pathInfo.substring(1).replace('\\', '/');
        if (fileName.contains("/") || fileName.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        File imageFile = new File(UploadConfig.getAvatarDirPath(), fileName);
        if (!imageFile.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentType(getServletContext().getMimeType(fileName));
        response.setContentLengthLong(imageFile.length());
        response.setHeader("Cache-Control", "public, max-age=604800");
        response.setHeader("Content-Disposition", "inline");

        try (InputStream in = Files.newInputStream(imageFile.toPath());
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
