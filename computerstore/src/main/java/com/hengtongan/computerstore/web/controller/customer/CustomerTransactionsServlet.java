package com.hengtongan.computerstore.web.controller.customer;

import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * A customer's own payment and refund history:
 *   GET  /account/transactions                     -> this customer's transactions
 *   GET  /account/transactions?id=42               -> one of this customer's transactions
 *   GET  /account/transactions?id=42&view=receipt  -> the printable receipt for it
 *
 * <h2>Everything here is scoped to the signed-in user</h2>
 * Two rules keep one customer's ledger out of another's hands:
 *
 * <ul>
 *   <li>The list comes from {@code getTransactionsByUser}, which filters on
 *       {@code user_id} in SQL.</li>
 *   <li>The detail comes from {@code getTransactionForUser}, which constrains the
 *       lookup on {@code user_id} as well as the id. Fetching by id and comparing
 *       owners in Java would work, but it loads another customer's row first and
 *       depends on a check nobody would think to remove.</li>
 * </ul>
 *
 * A transaction that is not the caller's answers 404 rather than 403, and the two
 * cases are deliberately indistinguishable: telling them apart would confirm whether
 * somebody else's transaction id exists.
 *
 * <h2>Read-only</h2>
 * A customer can look but not touch. Refunds are issued by staff against a gateway,
 * never by the payer, and the rows here are the record of what a gateway actually
 * did -- so there is no POST to handle.
 *
 * <h2>The receipt is the same row, not a second source of truth</h2>
 * {@code view=receipt} re-reads the transaction through the identical
 * user-scoped getter the detail view uses and forwards to a different template.
 * There is deliberately no receipt table: a receipt is a rendering of a payment
 * record, and a stored copy would be able to disagree with it. That is what makes
 * the ownership argument above cover the receipt without any new code -- the
 * receipt cannot be reached by a path that does not already pass the same check.
 *
 * <p>Shipping charges the receipt its line items, so the receipt also reads the
 * order. That read is scoped the same way ({@code getOrderForUser}) and a miss
 * degrades to a receipt without lines rather than a 404: the payment is real
 * whether or not the order row can still be joined to it, and a customer holding
 * a declined attempt or a legacy order should not be shown an error instead of
 * their own money.
 */
@WebServlet("/account/transactions")
public class CustomerTransactionsServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User user = currentUser(request);
        Integer transactionId = ValidationUtil.parseInt(request.getParameter("id"));
        if (transactionId != null) {
            if ("receipt".equals(request.getParameter("view"))) {
                showReceipt(transactionId, user, request, response);
                return;
            }
            showDetail(transactionId, user, request, response);
            return;
        }

        try {
            List<Transaction> transactions =
                    app().transactionService().getTransactionsByUser(user.getUserId());
            request.setAttribute("transactions", transactions);
            request.setAttribute("stats", app().transactionService()
                    .getTransactionStatsForUser(user.getUserId()));
            request.setAttribute("viewTitle", "My Transactions");
            request.getRequestDispatcher("/WEB-INF/views/customer/account/transactions.jsp")
                    .forward(request, response);
        } catch (SQLException e) {
            Flash.error(request, "Could not load your transactions. Please try again.");
            redirect(request, response, "/account");
        }
    }

    /**
     * The printable receipt for one of the caller's transactions.
     * <p>
     * Same ownership check as {@link #showDetail} and for the same reason; see the
     * class javadoc. {@code view} is read from the request but only ever compared
     * against one string, so an unrecognised value falls through to the detail view
     * rather than reaching a template it was not written for.
     */
    private void showReceipt(int transactionId, User user, HttpServletRequest request,
                             HttpServletResponse response) throws ServletException, IOException {
        try {
            Transaction transaction =
                    app().transactionService().getTransactionForUser(transactionId, user.getUserId());
            if (transaction == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("transaction", transaction);
            // Best-effort, and scoped: the lines are for the customer's own order.
            // A miss leaves the attribute null and the receipt renders without them.
            Order order = null;
            try {
                order = app().orderService().getOrderForUser(transaction.getOrderId(), user.getUserId());
            } catch (NotFoundException e) {
                // Deliberately swallowed. NotFoundException is the service's signal for
                // "not yours or not there", and the transaction is already proven to be
                // this customer's, so this can only mean the order row is gone.
            }
            request.setAttribute("order", order);
            request.setAttribute("viewTitle", "Receipt #" + transactionId);
            forward(request, response, "customer/account/receipt.jsp");
        } catch (SQLException e) {
            Flash.error(request, "Could not load that receipt. Please try again.");
            redirect(request, response, "/account/transactions");
        }
    }

    private void showDetail(int transactionId, User user, HttpServletRequest request,
                            HttpServletResponse response) throws ServletException, IOException {
        try {
            Transaction transaction =
                    app().transactionService().getTransactionForUser(transactionId, user.getUserId());
            if (transaction == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("transaction", transaction);
            // Only this customer's own attempts at this order. Querying by order alone
            // would pull in every row the order has, which leaks the other attempts
            // recorded against it.
            request.setAttribute("orderTransactions", app().transactionService()
                    .getTransactionsByOrderForUser(transaction.getOrderId(), user.getUserId()));
            request.setAttribute("viewTitle", "Transaction #" + transactionId);
            request.getRequestDispatcher("/WEB-INF/views/customer/account/transaction-detail.jsp")
                    .forward(request, response);
        } catch (SQLException e) {
            Flash.error(request, "Could not load that transaction. Please try again.");
            redirect(request, response, "/account/transactions");
        }
    }
}