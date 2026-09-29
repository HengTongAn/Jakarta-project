package com.hengtongan.computerstore.web.controller.base;

import com.hengtongan.computerstore.core.config.AppContext;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.web.Flash;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Shared HTTP helpers for MVC controllers. Obtain services via {@link #app()}.
 */
public abstract class BaseServlet extends HttpServlet {

    private static final String VIEWS = "/WEB-INF/views/";

    protected AppContext app() {
        return AppContext.get();
    }

    protected User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object user = session.getAttribute("user");
        return user instanceof User ? (User) user : null;
    }

    protected String currentUsername(HttpServletRequest request) {
        User user = currentUser(request);
        return user == null ? "UNKNOWN" : user.getUsername();
    }

    /**
     * @param viewPath path under {@code /WEB-INF/views/}, e.g. {@code customer/products.jsp}
     */
    protected void forward(HttpServletRequest request, HttpServletResponse response, String viewPath)
            throws ServletException, IOException {
        String path = viewPath.startsWith(VIEWS) ? viewPath : VIEWS + viewPath;
        request.getRequestDispatcher(path).forward(request, response);
    }

    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    protected void flashSuccess(HttpServletRequest request, String message) {
        Flash.success(request, message);
    }

    protected void flashError(HttpServletRequest request, String message) {
        Flash.error(request, message);
    }

    protected void flashWarning(HttpServletRequest request, String message) {
        Flash.warning(request, message);
    }
}
