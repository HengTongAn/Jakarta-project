package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import com.hengtongan.computerstore.core.exception.InsufficientStockException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.CartItem;
import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Payment;
import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.service.PaymentService;
import com.hengtongan.computerstore.util.validation.CardValidator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
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
        // The ABA option only exists when an admin has switched it on, so an
        // unconfigured store shows exactly the two methods it always did.
        request.setAttribute("abaAvailable", app().paymentService().isAbaAvailable());
        request.setAttribute("abaSimulated", app().paymentService().isSimulated());
        // Independent of ABA: the card option has its own switch, so an admin can
        // run either, both, or neither.
        request.setAttribute("cardAvailable", app().paymentService().isCardAvailable());
        request.setAttribute("cardSimulated", app().paymentService().isCardSimulated());
        if (app().paymentService().isCardAvailable() && app().paymentService().isCardSimulated()) {
            // Only useful while the method really is a demo. The list contains no
            // real numbers, so showing it in a live configuration would be noise.
            request.setAttribute("cardTestCards", CardValidator.demoCards());
        }
        forward(request, response, "customer/checkout.jsp");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request);
        String method;
        try {
            // Validated before the order is created: a tampered or stale form
            // must not reserve stock for a payment that can never be settled.
            method = app().paymentService().normaliseMethod(request.getParameter("paymentMethod"));
            if (PaymentService.METHOD_ABA.equals(method) && !app().paymentService().isAbaAvailable()) {
                throw new ValidationException("ABA Payway is not available right now. Choose another method.");
            }
            if (PaymentService.METHOD_CARD.equals(method) && !app().paymentService().isCardAvailable()) {
                throw new ValidationException("Card payment is not available right now. Choose another method.");
            }
        } catch (ValidationException e) {
            flashError(request, e.getMessage());
            redirect(request, response, "/cart");
            return;
        }

        // Read and validated before the order exists, so a mistyped card does not
        // reserve stock and then bounce the customer to an order they cannot pay.
        CardValidator.CardDetails card = null;
        if (PaymentService.METHOD_CARD.equals(method)) {
            try {
                card = CardValidator.validate(
                        request.getParameter("cardNumber"),
                        request.getParameter("cardExpiry"),
                        request.getParameter("cardCvv"),
                        request.getParameter("cardName"));
            } catch (ValidationException e) {
                flashError(request, e.getMessage());
                redirect(request, response, "/checkout");
                return;
            }
        }

        try {
            Order order = app().orderService().checkout(user, method);
            if (PaymentService.METHOD_ABA.equals(method)) {
                // The order and its stock reservation already exist and are
                // committed. Opening the charge afterwards means a gateway
                // outage leaves a pending order to retry, not a lost cart.
                app().paymentService().startAbaPayment(order.getOrderId(), gatewayBaseUrl(request));
                redirect(request, response, "/payment/aba?order=" + order.getOrderId());
                return;
            }
            if (PaymentService.METHOD_CARD.equals(method)) {
                // Authorised inline: there is no "go to the bank and come back"
                // step for a card, so the answer is already known here. Only the
                // brand and last four digits travel any further.
                Payment payment = app().paymentService().startCardPayment(order.getOrderId(), card);
                if (payment.getStatus() == Payment.Status.PAID) {
                    flashSuccess(request, "Payment of " + card.masked() + " accepted. Order #"
                            + order.getOrderId() + " is confirmed.");
                    // To the receipt, matching the ABA return: the card is authorised
                    // inline here, so this is the same "the money has moved" moment the
                    // gateway return endpoint handles. Falls back to the order page if
                    // no completed transaction row was recorded.
                    redirect(request, response, receiptPathFor(order, user));
                } else {
                    // The order stays PENDING so the customer can try another card.
                    flashError(request, payment.getMessage() + " Your order #" + order.getOrderId()
                            + " is saved - you can try a different card.");
                    redirect(request, response, "/payment/card?order=" + order.getOrderId());
                }
                return;
            }
            flashSuccess(request, "Order #" + order.getOrderId() + " placed successfully.");
            redirect(request, response, "/account/orders?id=" + order.getOrderId());
        } catch (InsufficientStockException | ValidationException e) {
            flashError(request, e.getMessage());
            redirect(request, response, "/cart");
        }
    }

    /**
     * Where to send the browser once a card payment has actually settled.
     * <p>
     * The receipt when a completed transaction row exists, the order page when it
     * does not. That second case is reachable: {@code startCardPayment} writes the
     * transaction and settles the order in separate transactions, so a failure
     * between them is caught and logged rather than propagated, leaving the order
     * PAID with nothing to receipt. The order page still reports the paid state
     * from the order table, so the customer is not left on an error.
     * <p>
     * The lookup takes the signed-in user rather than trusting {@code order}, so an
     * order id in the URL cannot be used to obtain another customer's payment id.
     */
    private String receiptPathFor(Order order, User user) {
        String orderPath = "/account/orders?id=" + order.getOrderId();
        if (user == null) {
            return orderPath;
        }
        try {
            Transaction settled = app().transactionService()
                    .getSettledPaymentForUser(order.getOrderId(), user.getUserId());
            if (settled == null) {
                return orderPath;
            }
            return "/account/transactions?id=" + settled.getTransactionId() + "&view=receipt";
        } catch (SQLException e) {
            return orderPath;
        }
    }

    /**
     * Absolute origin of this deployment, which ABA needs for its return URLs.
     * Derived from the request rather than configured, so it follows whatever
     * host the store is actually reached on.
     */
    private String gatewayBaseUrl(HttpServletRequest request) {
        String scheme = request.isSecure() ? "https" : "http";
        return scheme + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                        ? "" : ":" + request.getServerPort())
                + request.getContextPath();
    }
}
