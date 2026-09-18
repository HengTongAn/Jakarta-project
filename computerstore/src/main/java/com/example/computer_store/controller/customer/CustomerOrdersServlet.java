package com.example.computer_store.controller.customer;

import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.model.Order;
import com.example.computer_store.model.User;
import com.example.computer_store.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.example.computer_store.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;

/**
 * Customer order history:
 *   GET /account/orders           -> list of orders
 *   GET /account/orders?id=12     -> order detail
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
        request.getRequestDispatcher("/WEB-INF/views/customer/my-orders.jsp")
                .forward(request, response);
    }

    private void showDetail(int orderId, User user, HttpServletRequest request,
                            HttpServletResponse response) throws ServletException, IOException {
        try {
            Order order = app().orderService().getOrderForUser(orderId, user.getUserId());
            request.setAttribute("order", order);
            request.getRequestDispatcher("/WEB-INF/views/customer/order-detail.jsp")
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