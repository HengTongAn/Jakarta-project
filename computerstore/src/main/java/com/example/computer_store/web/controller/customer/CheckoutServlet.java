package com.example.computer_store.web.controller.customer;

import com.example.computer_store.web.controller.base.BaseServlet;
import com.example.computer_store.core.exception.InsufficientStockException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.CartItem;
import com.example.computer_store.core.domain.entity.Order;
import com.example.computer_store.core.domain.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet("/checkout")
public class CheckoutServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request);
        List<CartItem> items = app().cartService().getCartItems(user.getUserId());
        if (items.isEmpty()) {
            flashError(request, "Your cart is empty. Add some products first.");
            redirect(request, response, "/cart");
            return;
        }
        request.setAttribute("items", items);
        request.setAttribute("total", app().cartService().getTotal(items));
        forward(request, response, "customer/checkout.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request);
        try {
            Order order = app().orderService().checkout(user);
            flashSuccess(request, "Order #" + order.getOrderId() + " placed successfully.");
            redirect(request, response, "/account/orders?id=" + order.getOrderId());
        } catch (InsufficientStockException | ValidationException e) {
            flashError(request, e.getMessage());
            redirect(request, response, "/cart");
        }
    }
}
