package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.cache.CountCache;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import com.hengtongan.computerstore.core.domain.entity.CartItem;

@WebServlet({"/cart", "/cart/add", "/cart/update", "/cart/remove"})
public class CartServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = sessionUser(request);
        List<CartItem> items = app().cartService().getCartItems(user.getUserId());
        request.setAttribute("items", items);
        request.setAttribute("total", app().cartService().getTotal(items));
        request.getRequestDispatcher("/WEB-INF/views/customer/cart/view.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        boolean ajax = "1".equals(request.getParameter("ajax"));
        try {
            switch (path) {
                case "/cart/add" -> {
                    int productId = requireInt(request, "productId");
                    int quantity = optionalInt(request, "quantity", 1);
                    app().cartService().add(sessionUser(request).getUserId(), productId, quantity);
                    Flash.success(request, "Item added to your cart.");
                }
                case "/cart/update" -> {
                    int cartItemId = requireInt(request, "cartItemId");
                    int quantity = requireInt(request, "quantity");
                    app().cartService().updateQuantity(sessionUser(request).getUserId(), cartItemId, quantity);
                    Flash.success(request, "Cart updated.");
                }
                case "/cart/remove" -> {
                    int cartItemId = requireInt(request, "cartItemId");
                    app().cartService().remove(sessionUser(request).getUserId(), cartItemId);
                    Flash.success(request, "Item removed from your cart.");
                }
                default -> throw new NotFoundException("Unknown cart action.");
            }
            CountCache.invalidate(request.getSession(false), "cart");
        } catch (ValidationException | NotFoundException e) {
            if (ajax) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST, false, e.getMessage(), 0);
                return;
            }
            Flash.error(request, e.getMessage());
        }
        if (ajax) {
            User user = sessionUser(request);
            int count = user == null ? 0 : app().cartService().countItems(user.getUserId());
            writeJson(response, HttpServletResponse.SC_OK, true, null, count);
            return;
        }
        response.sendRedirect(request.getContextPath() + "/cart");
    }

    private void writeJson(HttpServletResponse response, int status, boolean ok,
                           String message, int count) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print("{\"ok\":" + ok
                + ",\"message\":\"" + (message == null ? "" : escape(message)) + "\""
                + ",\"count\":" + count + "}");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private User sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute("user");
    }

    private int requireInt(HttpServletRequest request, String param) {
        Integer value = ValidationUtil.parseInt(request.getParameter(param));
        if (value == null) {
            throw new ValidationException("Invalid request.");
        }
        return value;
    }

    private int optionalInt(HttpServletRequest request, String param, int defaultValue) {
        Integer value = ValidationUtil.parseInt(request.getParameter(param));
        return value == null ? defaultValue : value;
    }
}