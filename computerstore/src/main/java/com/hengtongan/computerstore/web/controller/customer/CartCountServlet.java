package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.domain.entity.User;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Lightweight JSON endpoint giving the current user's cart item count.
 * Used by the frontend to refresh the navbar basket badge without a reload.
 */
@WebServlet("/cart/count")
public class CartCountServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        Object raw = session == null ? null : session.getAttribute("user");
        if (!(raw instanceof User)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().print("{\"count\":0}");
            return;
        }
        int count = app().cartService().countItems(((User) raw).getUserId());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print("{\"count\":" + count + "}");
    }
}