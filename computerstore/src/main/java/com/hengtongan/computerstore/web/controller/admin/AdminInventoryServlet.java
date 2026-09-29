package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.domain.entity.InventoryLog;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.service.InventoryService;
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
import java.util.Map;

/**
 * Admin inventory management:
 *   GET  /admin/inventory     -> stock overview, low stock, out of stock, logs
 *   POST /admin/inventory     -> manual stock adjustment (delta + reason)
 */
@WebServlet("/admin/inventory")
public class AdminInventoryServlet extends BaseServlet {

    private static final Map<String, String> REASON_LABELS = Map.of(
            InventoryService.REASON_RECEIVED, "Received",
            InventoryService.REASON_DAMAGED, "Damaged",
            InventoryService.REASON_COUNT, "Counted",
            InventoryService.REASON_RETURNED, "Return",
            InventoryService.REASON_CORRECTION, "Correction");

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

        request.setAttribute("lowStockThreshold", threshold);
        request.setAttribute("reorderTarget", InventoryService.REORDER_TARGET);
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
        int productId;
        int delta;
        String reason;
        try {
            productId = Integer.parseInt(request.getParameter("productId"));
            delta = Integer.parseInt(request.getParameter("delta").trim());
            reason = request.getParameter("reason");
        } catch (NumberFormatException | NullPointerException e) {
            Flash.error(request, "Invalid stock quantity.");
            response.sendRedirect(request.getContextPath() + "/admin/inventory");
            return;
        }

        if (!InventoryService.isValidReason(reason)) {
            Flash.error(request, "Choose a reason for the change.");
            response.sendRedirect(request.getContextPath() + "/admin/inventory");
            return;
        }
        if (delta == 0) {
            Flash.error(request, "A change of 0 does nothing.");
            response.sendRedirect(request.getContextPath() + "/admin/inventory");
            return;
        }
        if (InventoryService.REASON_RECEIVED.equals(reason) && delta < 0) {
            Flash.error(request, "Receive quantity must be positive.");
            response.sendRedirect(request.getContextPath() + "/admin/inventory");
            return;
        }

        String reasonLabel = REASON_LABELS.getOrDefault(reason, reason);
        try {
            app().inventoryService().adjustStock(productId, delta, reason, user.getUserId());
            AuditLogger.logAdminAction("STOCK_ADJUST",
                    user != null ? user.getUsername() : "UNKNOWN",
                    "product #" + productId,
                    (delta > 0 ? "+" : "") + delta + " (" + reasonLabel + ")");
            Flash.success(request, "Stock updated (" + reasonLabel + ").");
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