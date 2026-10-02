package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Payment;
import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * The ABA Payway payment page and the gateway return endpoint.
 * <pre>
 *   GET  /payment/aba?order=N          -&gt; QR page for the customer's own order
 *   POST /payment/aba?order=N          -&gt; demo controls (simulate mode only)
 *   GET  /payment/aba/return?order=N   -&gt; where the gateway sends the browser
 * </pre>
 *
 * <h2>The return endpoint trusts nothing from the browser</h2>
 * The query string on {@code /return} is entirely customer-controlled. The
 * {@code status} parameter it carries is read only to pick a message; the
 * decision to mark an order paid is delegated to
 * {@link com.hengtongan.computerstore.core.service.PaymentService#confirm(int, String)},
 * which re-asks the gateway. A customer who hand-edits the URL to
 * {@code status=1} gets an error page and an order that is still unpaid.
 */
@WebServlet("/payment/aba/*")
public class AbaPaymentServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getPathInfo() != null && request.getPathInfo().startsWith("/return")) {
            handleReturn(request, response);
            return;
        }
        render(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer orderId = ValidationUtil.parseInt(request.getParameter("order"));
        if (orderId == null || ownOrder(request, response, orderId) == null) {
            return;
        }
        // Demo affordances, refused the moment simulate mode is off.
        String action = request.getParameter("action");
        try {
            if ("simulate_paid".equals(action)) {
                app().paymentService().setSimulatedResult(orderId, "1");
                flashSuccess(request, "Simulated gateway: payment approved.");
            } else if ("simulate_cancelled".equals(action)) {
                app().paymentService().setSimulatedResult(orderId, "2");
                flashSuccess(request, "Simulated gateway: customer cancelled.");
            }
        } catch (ValidationException e) {
            flashError(request, e.getMessage());
        }
        redirect(request, response, "/payment/aba?order=" + orderId);
    }

    /** Gateway redirect target. Never marks anything paid on its own authority. */
    private void handleReturn(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Integer orderId = ValidationUtil.parseInt(request.getParameter("order"));
        Order order = orderId == null ? null : ownOrder(request, response, orderId);
        if (order == null) {
            return;
        }

        String browserStatus = request.getParameter("status");
        Payment.Status confirmed;
        try {
            confirmed = app().paymentService().confirm(orderId, browserStatus);
        } catch (ValidationException e) {
            flashError(request, e.getMessage());
            redirect(request, response, "/payment/aba?order=" + orderId);
            return;
        }

        if (confirmed == Payment.Status.PAID) {
            flashSuccess(request, "Payment confirmed. Thank you!");
            // Straight to the receipt rather than to the order page. This is the
            // moment the money moved, so it is the moment the customer wants the
            // document in front of them. getSettledPaymentForUser is what makes
            // that safe: it returns nothing when this order has no completed
            // payment, and a receipt for a payment that is not in the ledger would
            // be a document nobody can honour.
            redirect(request, response, receiptPathFor(orderId, request));
            return;
        }
        if (confirmed == Payment.Status.PENDING) {
            flashError(request, "The gateway has not confirmed this payment yet. "
                    + "It stays pending until ABA reports success.");
        } else {
            flashError(request, "Payment " + confirmed.name().toLowerCase() + ".");
        }
        redirect(request, response, "/payment/aba?order=" + orderId);
    }

    /**
     * Where to send the browser once a payment has actually settled.
     * <p>
     * The receipt when there is one to show, the order page when there is not. That
     * second case is reachable rather than theoretical: {@code confirm} records the
     * transaction and the order in two separate writes, so a crash or a database
     * error between them (both are caught and logged, not propagated) leaves the
     * order marked PAID with no completed row behind it. The customer has paid, so
     * they must still land somewhere that says so -- but a receipt printed from a
     * transaction that is not in the ledger is a document the store cannot honour,
     * so it is not manufactured here.
     *
     * <p>Scoped to the session user on purpose. The order id is user-supplied, and
     * this lookup is the one that decides whose payment id gets put in a URL.
     */
    private String receiptPathFor(int orderId, HttpServletRequest request) {
        User user = currentUser(request);
        String orderPath = "/account/orders?id=" + orderId;
        if (user == null) {
            return orderPath;
        }
        try {
            Transaction settled =
                    app().transactionService().getSettledPaymentForUser(orderId, user.getUserId());
            if (settled == null) {
                return orderPath;
            }
            return "/account/transactions?id=" + settled.getTransactionId() + "&view=receipt";
        } catch (SQLException e) {
            // The order page renders from the order table, which was written
            // before the payment was confirmed, so it still shows the paid state.
            // Falling back to it degrades the page rather than breaking the flow.
            return orderPath;
        }
    }

    private void render(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer orderId = ValidationUtil.parseInt(request.getParameter("order"));
        Order order = orderId == null ? null : ownOrder(request, response, orderId);
        if (order == null) {
            return;
        }
        request.setAttribute("order", order);
        // Pre-resolved so the confirmed panel can offer the receipt without the
        // view having to reach into a service, and without guessing an id.
        request.setAttribute("receiptPath", receiptPathFor(order.getOrderId(), request));
        request.setAttribute("payment", app().paymentService().latestFor(orderId));
        request.setAttribute("simulated", app().paymentService().isSimulated());
        forward(request, response, "customer/payment-aba.jsp");
    }

    /**
     * Loads the order, or {@code null} after having redirected away.
     * <p>
     * The user-scoped lookup is the point: a customer must not be able to read
     * another customer's amount or transaction id by guessing an order number,
     * so a mismatch is indistinguishable from a missing order.
     * <p>
     * {@code getOrderForUser} signals a miss by <em>throwing</em> NotFoundException
     * rather than returning null, so it has to be caught here. Left uncaught it
     * becomes a 500, which is both a poor answer to "that order is not yours" and
     * a needless stack trace in the log for what is an ordinary wrong guess.
     * <p>
     * The null-user guard is deliberate belt-and-braces. AuthenticationFilter
     * already covers {@code /payment/aba/*} and bounces anonymous requests to
     * the login page, so this branch should be unreachable -- but a servlet that
     * dereferences the session user on a customer-scoped page should degrade to
     * a redirect if that ever stops being true, not answer a 500.
     */
    private Order ownOrder(HttpServletRequest request, HttpServletResponse response, int orderId)
            throws IOException {
        User user = currentUser(request);
        if (user == null) {
            flashError(request, "Please sign in to view a payment.");
            redirect(request, response, "/login");
            return null;
        }
        Order order;
        try {
            order = app().orderService().getOrderForUser(orderId, user.getUserId());
        } catch (NotFoundException e) {
            // Deliberately the same response as a genuinely missing order, so
            // probing ids cannot be used to confirm which orders exist.
            flashError(request, "Order not found.");
            redirect(request, response, "/account/orders");
            return null;
        }
        if (order == null) {
            flashError(request, "Order not found.");
            redirect(request, response, "/account/orders");
            return null;
        }
        return order;
    }
}
