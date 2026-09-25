package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.Order;
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
 * Admin order management:
 *   GET  /admin/orders            -> order list (paginated)
 *   GET  /admin/orders?id=ID      -> order detail
 *   POST /admin/orders            -> update order status
 */
@WebServlet("/admin/orders")
public class AdminOrdersServlet extends BaseServlet {

    private static final int PAGE_SIZE = 25;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer orderId = ValidationUtil.parseInt(request.getParameter("id"));
        if (orderId != null) {
            try {
                Order order = app().orderService().getOrder(orderId);
                request.setAttribute("order", order);
                request.getRequestDispatcher("/WEB-INF/views/admin/orders/detail.jsp")
                        .forward(request, response);
            } catch (NotFoundException e) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
            return;
        }

        Order.Status status = null;
        String statusParam = request.getParameter("status");
        if (statusParam != null && !statusParam.isEmpty()) {
            try {
                status = Order.Status.valueOf(statusParam);
            } catch (IllegalArgumentException ignored) {
                status = null;
            }
        }

        int page = parsePage(request.getParameter("page"));
        int total = app().orderService().countOrders(status);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        if (page > totalPages) {
            page = totalPages;
        }
        int offset = (page - 1) * PAGE_SIZE;

        request.setAttribute("orders", app().orderService().getOrdersPage(status, offset, PAGE_SIZE));
        request.setAttribute("selectedStatus", status);
        request.setAttribute("page", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalOrders", total);
        request.getRequestDispatcher("/WEB-INF/views/admin/orders/list.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer orderId = ValidationUtil.parseInt(request.getParameter("orderId"));
        String statusParam = request.getParameter("status");
        if (orderId == null || statusParam == null) {
            Flash.error(request, "Invalid request.");
            response.sendRedirect(request.getContextPath() + "/admin/orders");
            return;
        }
        try {
            Order.Status status = Order.Status.valueOf(statusParam);
            User admin = (User) request.getSession().getAttribute("user");
            String actor = admin != null ? admin.getUsername() : "UNKNOWN";
            app().orderService().updateStatus(orderId, status, actor, null);
            AuditLogger.logAdminAction("ORDER_STATUS", actor,
                    "order #" + orderId, "Status changed to " + status.name());
            Flash.success(request, "Order #" + orderId + " status changed to " + status.name() + ".");
        } catch (IllegalArgumentException e) {
            Flash.error(request, "Unknown order status.");
        } catch (ValidationException | NotFoundException e) {
            Flash.error(request, e.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/admin/orders?id=" + orderId);
    }

    private static int parsePage(String raw) {
        Integer page = ValidationUtil.parseInt(raw);
        return page == null || page < 1 ? 1 : page;
    }
}
