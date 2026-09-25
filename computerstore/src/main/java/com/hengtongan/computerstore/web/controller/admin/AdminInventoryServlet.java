package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.domain.entity.InventoryLog;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.web.Flash;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin inventory management:
 *   GET  /admin/inventory     -> stock overview, low stock, out of stock, logs
 *   POST /admin/inventory     -> manual stock adjustment
 */
@WebServlet("/admin/inventory")
public class AdminInventoryServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<Product> products = app().productService().getAll();

        // Low / out-of-stock are subsets of the product list already loaded
        // above, so deriving them in memory avoids two extra round trips.
        int threshold = Product.LOW_STOCK_THRESHOLD;
        List<Product> lowStock = new ArrayList<>();
        List<Product> outOfStock = new ArrayList<>();
        for (Product p : products) {
            if (p.getStatus() == Product.Status.DISCONTINUED) {
                continue;
            }
            int stock = p.getStockQuantity();
            if (stock == 0) {
                outOfStock.add(p);
            } else if (stock <= threshold) {
                lowStock.add(p);
            }
        }

        request.setAttribute("lowStock", lowStock);
        request.setAttribute("outOfStock", outOfStock);
        request.setAttribute("logs", app().inventoryService().getRecentLogs(15));
        request.setAttribute("products", products);
        request.getRequestDispatcher("/WEB-INF/views/admin/inventory.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        try {
            int productId = Integer.parseInt(request.getParameter("productId"));
            int newQuantity = Integer.parseInt(request.getParameter("newQuantity"));
            app().inventoryService().adjustStock(productId, newQuantity, user.getUserId());
            AuditLogger.logAdminAction("STOCK_ADJUST",
                    user != null ? user.getUsername() : "UNKNOWN",
                    "product #" + productId,
                    "Manual stock change to " + newQuantity);
            Flash.success(request, "Stock updated.");
        } catch (NumberFormatException e) {
            Flash.error(request, "Invalid stock quantity.");
        } catch (RuntimeException e) {
            Flash.error(request, e.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/admin/inventory");
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}