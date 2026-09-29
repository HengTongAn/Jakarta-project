package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.Category;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Admin category CRUD:
 *   GET  /admin/categories?edit=ID      -> edit form (same page)
 *   POST /admin/categories?action=delete -> delete
 *   POST /admin/categories               -> create / update
 */
@WebServlet("/admin/categories")
public class AdminCategoriesServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer editId = ValidationUtil.parseInt(request.getParameter("edit"));
        if (editId != null) {
            try {
                request.setAttribute("editing", app().categoryService().get(editId));
            } catch (NotFoundException e) {
                Flash.error(request, e.getMessage());
                response.sendRedirect(request.getContextPath() + "/admin/categories");
                return;
            }
        }
        request.setAttribute("categories", app().categoryService().getAll());
        request.getRequestDispatcher("/WEB-INF/views/admin/categories.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("delete".equals(request.getParameter("action"))) {
            deleteCategory(request, response);
            return;
        }

        Integer categoryId = ValidationUtil.parseInt(request.getParameter("categoryId"));
        String name = request.getParameter("name");
        String description = request.getParameter("description");

        try {
            if (categoryId == null) {
                app().categoryService().create(name, description);
                AuditLogger.logAdminAction("CATEGORY_CREATE", sessionUsername(request),
                        "category", "Created '" + name + "'");
                Flash.success(request, "Category created successfully.");
            } else {
                app().categoryService().update(categoryId, name, description);
                AuditLogger.logAdminAction("CATEGORY_UPDATE", sessionUsername(request),
                        "category #" + categoryId, "Updated '" + name + "'");
                Flash.success(request, "Category updated successfully.");
            }
            response.sendRedirect(request.getContextPath() + "/admin/categories");
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            if (categoryId != null) {
                try {
                    request.setAttribute("editing", app().categoryService().get(categoryId) != null ? app().categoryService().get(categoryId) : null);
                } catch (NotFoundException ignored) {
                    // category vanished mid-edit; just re-render the list with the error
                }
            }
            request.setAttribute("categories", app().categoryService().getAll());
            request.getRequestDispatcher("/WEB-INF/views/admin/categories.jsp").forward(request, response);
        }
    }

    private void deleteCategory(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer categoryId = ValidationUtil.parseInt(request.getParameter("categoryId"));
        try {
            if (categoryId == null) {
                throw new ValidationException("Invalid category.");
            }
            app().categoryService().delete(categoryId);
            AuditLogger.logAdminAction("CATEGORY_DELETE", sessionUsername(request),
                    "category #" + categoryId, "Category removed");
            Flash.success(request, "Category deleted.");
        } catch (ValidationException e) {
            Flash.error(request, e.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/admin/categories");
    }

    private String sessionUsername(HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute("user");
        if (user instanceof User) {
            return ((User) user).getUsername();
        }
        return "UNKNOWN";
    }
}
