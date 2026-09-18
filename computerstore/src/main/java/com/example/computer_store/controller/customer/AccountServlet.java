package com.example.computer_store.controller.customer;

import com.example.computer_store.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.example.computer_store.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Customer dashboard page at /account.
 */
@WebServlet("/account")
public class AccountServlet extends BaseServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        request.setAttribute("user", user);
        request.setAttribute("recentOrders", app().orderService().getOrdersForUser(user.getUserId())
                .stream().limit(5).toList());
        request.setAttribute("cartCount", app().cartService().countItems(user.getUserId()));
        request.getRequestDispatcher("/WEB-INF/views/customer/account.jsp").forward(request, response);
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }
}