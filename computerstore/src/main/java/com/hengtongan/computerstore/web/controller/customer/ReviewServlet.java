package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Customer review submission.
 *   POST /products/review -> creates a PENDING review and returns to the
 *                            product page (Post/Redirect/Get).
 *
 * <p>Not covered by {@code AuthenticationFilter} (only admin/cart/account
 * paths are), so an explicit login check happens here.</p>
 */
@WebServlet("/products/review")
public class ReviewServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer productId = ValidationUtil.parseInt(request.getParameter("productId"));
        User user = currentUser(request);

        if (user == null) {
            String target = "/products" + (productId == null ? "" : "?id=" + productId);
            response.sendRedirect(request.getContextPath() + "/login?return="
                    + URLEncoder.encode(target, StandardCharsets.UTF_8));
            return;
        }
        if (productId == null) {
            flashError(request, "Product not found.");
            redirect(request, response, "/products");
            return;
        }

        Integer rating = ValidationUtil.parseInt(request.getParameter("rating"));
        String title = request.getParameter("title");
        String reviewText = request.getParameter("reviewText");

        try {
            app().reviewService().submit(user.getUserId(), productId, rating == null ? 0 : rating,
                    title, reviewText);
            flashSuccess(request, "Thank you! Your review has been submitted and will appear "
                    + "once it is approved.");
        } catch (ValidationException e) {
            flashError(request, e.getMessage());
        }
        redirect(request, response, "/products?id=" + productId);
    }
}
