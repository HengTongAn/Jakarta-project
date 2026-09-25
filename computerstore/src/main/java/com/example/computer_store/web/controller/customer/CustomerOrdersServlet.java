package com.example.computer_store.web.controller.customer;

import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.Order;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.util.web.Flash;
import com.example.computer_store.util.validation.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.example.computer_store.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

/**
 * Customer order history:
 *   GET  /account/orders           -> list of orders
 *   GET  /account/orders?id=12     -> order detail
 *   POST /account/orders           -> cancel a pending order (action=cancel)
 */
@WebServlet("/account/orders")
public class CustomerOrdersServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        Integer orderId = ValidationUtil.parseInt(request.getParameter("id"));
        if (orderId != null) {
            showDetail(orderId, user, request, response);
            return;
        }
        List<Order> orders = app().orderService().getOrdersForUser(user.getUserId());
        request.setAttribute("orders", orders);
        request.getRequestDispatcher("/WEB-INF/views/customer/account/orders.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        Integer orderId = ValidationUtil.parseInt(request.getParameter("orderId"));
        if ("cancel".equals(request.getParameter("action")) && orderId != null) {
            try {
                app().orderService().cancelByCustomer(orderId, user.getUserId());
                Flash.success(request, "Order #" + orderId + " was cancelled and the items returned to stock.");
            } catch (ValidationException | NotFoundException e) {
                Flash.error(request, e.getMessage());
            }
            response.sendRedirect(request.getContextPath() + "/account/orders?id=" + orderId);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/account/orders");
    }

    private void showDetail(int orderId, User user, HttpServletRequest request,
                            HttpServletResponse response) throws ServletException, IOException {
        try {
            Order order = app().orderService().getOrderForUser(orderId, user.getUserId());
            request.setAttribute("order", order);
            request.getRequestDispatcher("/WEB-INF/views/customer/account/order-detail.jsp")
                    .forward(request, response);
        } catch (NotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}
