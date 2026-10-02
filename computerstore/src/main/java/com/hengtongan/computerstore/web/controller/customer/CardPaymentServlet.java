package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Payment;
import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

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
        // Pre-resolved so the confirmed panel can offer the receipt without the view
        // reaching into a service or guessing an id. The receipt for an order is one
        // query scoped to this customer; see CheckoutServlet.receiptPathFor for why
        // it can legitimately resolve to the order page instead.
        request.setAttribute("receiptPath", receiptPathFor(orderId, request));
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
     * The receipt for this order, or the order page when there is not one to show.
     * <p>
     * Scoped to the session user: the order id in the URL is user-supplied, and this
     * is the lookup that turns it into a payment id, so it must not be answerable for
     * somebody else's order. Returns the order path on any miss or error, since this
     * page is only ever shown to a customer who already owns the order and a receipt
     * is an improvement rather than a precondition.
     */
    private String receiptPathFor(int orderId, HttpServletRequest request) {
        User user = currentUser(request);
        String orderPath = "/account/orders?id=" + orderId;
        if (user == null) {
            return orderPath;
        }
        try {
            Transaction settled = app().transactionService()
                    .getSettledPaymentForUser(orderId, user.getUserId());
            if (settled == null) {
                return orderPath;
            }
            return "/account/transactions?id=" + settled.getTransactionId() + "&view=receipt";
        } catch (SQLException e) {
            return orderPath;
        }
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
