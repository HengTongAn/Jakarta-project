package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.Brand;
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
 * Admin brand CRUD:
 *   GET  /admin/brands?edit=ID          -> edit form (same page)
 *   POST /admin/brands?action=delete    -> delete
 *   POST /admin/brands                  -> create / update
 */
@WebServlet("/admin/brands")
public class AdminBrandsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer editId = ValidationUtil.parseInt(request.getParameter("edit"));
        if (editId != null) {
            try {
                request.setAttribute("editing", app().brandService().get(editId));
            } catch (NotFoundException e) {
                Flash.error(request, e.getMessage());
                response.sendRedirect(request.getContextPath() + "/admin/brands");
                return;
            }
        }
        request.setAttribute("brands", app().brandService().getAll());
        request.getRequestDispatcher("/WEB-INF/views/admin/brands.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("delete".equals(request.getParameter("action"))) {
            deleteBrand(request, response);
            return;
        }

        Integer brandId = ValidationUtil.parseInt(request.getParameter("brandId"));
        String name = request.getParameter("name");
        String description = request.getParameter("description");

        try {
            if (brandId == null) {
                app().brandService().create(name, description);
                AuditLogger.logAdminAction("BRAND_CREATE", sessionUsername(request),
                        "brand", "Created '" + name + "'");
                Flash.success(request, "Brand created successfully.");
            } else {
                app().brandService().update(brandId, name, description);
                AuditLogger.logAdminAction("BRAND_UPDATE", sessionUsername(request),
                        "brand #" + brandId, "Updated '" + name + "'");
                Flash.success(request, "Brand updated successfully.");
            }
            response.sendRedirect(request.getContextPath() + "/admin/brands");
        } catch (ValidationException e) {
            request.setAttribute("error", e.getMessage());
            if (brandId != null) {
                try {
                    request.setAttribute("editing", app().brandService().get(brandId) != null ? app().brandService().get(brandId) : null);
                } catch (NotFoundException ignored) {
                    // brand vanished mid-edit; just re-render the list with the error
                }
            }
            request.setAttribute("brands", app().brandService().getAll());
            request.getRequestDispatcher("/WEB-INF/views/admin/brands.jsp").forward(request, response);
        }
    }

    private void deleteBrand(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer brandId = ValidationUtil.parseInt(request.getParameter("brandId"));
        try {
            if (brandId == null) {
                throw new ValidationException("Invalid brand.");
            }
            app().brandService().delete(brandId);
            AuditLogger.logAdminAction("BRAND_DELETE", sessionUsername(request),
                    "brand #" + brandId, "Brand removed");
            Flash.success(request, "Brand deleted.");
        } catch (ValidationException e) {
            Flash.error(request, e.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/admin/brands");
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
