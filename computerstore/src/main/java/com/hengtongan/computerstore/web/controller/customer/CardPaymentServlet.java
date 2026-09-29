package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Payment;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * The card payment status page: what happened to a card order, and how to try again.
 * <pre>
 *   GET  /payment/card?order=N   -&gt; outcome of the last card attempt
 * </pre>
 *
 * <p>Read-only by design. Authorisation happens during the checkout POST, so
 * there is nothing to submit here: a page that could change a payment's state
 * would be a page whose behaviour depends on a link, which is exactly the
 * mistake the ABA return endpoint is careful to avoid.
 *
 * <p>No card number reaches this class, and none can: the only card data in the
 * system is the brand and last four digits stored on the attempt.
 */
@WebServlet("/payment/card/*")
public class CardPaymentServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer orderId = ValidationUtil.parseInt(request.getParameter("order"));
        if (orderId == null) {
            flashError(request, "No order was specified.");
            redirect(request, response, "/account/orders");
            return;
        }
        Order order = ownOrder(request, response, orderId);
        if (order == null) {
            return;
        }

        Payment payment = app().paymentService().latestFor(orderId);
        request.setAttribute("order", order);
        request.setAttribute("payment", payment);
        // The demo hint is only meaningful while the method is actually a demo.
        request.setAttribute("simulated", app().paymentService().isCardSimulated());
        forward(request, response, "customer/payment-card.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // Defensive: nothing on this page submits, so refuse rather than 405 and
        // leave it ambiguous whether a state change was attempted.
        redirect(request, response, "/payment/card?order="
                + (request.getParameter("order") == null ? "" : request.getParameter("order")));
    }

    /**
     * Loads the order, or {@code null} after having redirected away.
     * <p>
     * Same reasoning as the ABA page: the lookup is scoped to the signed-in
     * customer so a guessed order number reveals nothing, {@code getOrderForUser}
     * signals a miss by throwing rather than returning null, and a missing session
     * user degrades to a redirect instead of a 500.
     */
    private Order ownOrder(HttpServletRequest request, HttpServletResponse response, int orderId)
            throws IOException {
        User user = currentUser(request);
        if (user == null) {
            flashError(request, "Please sign in to view a payment.");
            redirect(request, response, "/login");
            return null;
        }
        try {
            return app().orderService().getOrderForUser(orderId, user.getUserId());
        } catch (NotFoundException e) {
            // Same answer as a genuinely missing order, so probing ids cannot be
            // used to confirm which orders exist.
            flashError(request, "Order not found.");
            redirect(request, response, "/account/orders");
            return null;
        }
    }
}
