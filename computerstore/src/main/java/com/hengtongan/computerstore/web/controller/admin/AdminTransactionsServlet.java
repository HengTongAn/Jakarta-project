package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.repository.TransactionRepository;
import com.hengtongan.computerstore.core.service.TransactionService;
import com.hengtongan.computerstore.util.web.AuditLogger;
import com.hengtongan.computerstore.util.web.Flash;
import com.hengtongan.computerstore.util.validation.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import com.hengtongan.computerstore.web.controller.base.BaseServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Admin transaction management:
 *   GET  /admin/transactions            -> transaction list (paginated, filterable)
 *   GET  /admin/transactions?id=ID      -> transaction detail
 *   GET  /admin/transactions?orderId=X -> transactions for specific order
 *   POST /admin/transactions            -> refund a settled payment (action=refund)
 *
 * <p>Refunds are whole and ledger-only: the row records the movement, and no
 * payment provider is called because neither one exposes a refund call. See
 * {@link TransactionService#refundPayment(long, String)} for what that does and
 * does not mean.
 */
@WebServlet("/admin/transactions")
public class AdminTransactionsServlet extends BaseServlet {

    private static final int PAGE_SIZE = 25;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer transactionId = ValidationUtil.parseInt(request.getParameter("id"));
        if (transactionId != null) {
            showDetail(request, response, transactionId);
            return;
        }

        Integer orderId = ValidationUtil.parseInt(request.getParameter("orderId"));
        if (orderId != null) {
            try {
                List<Transaction> transactions = app().transactionService().getTransactionsByOrder(orderId);
                request.setAttribute("transactions", transactions);
                request.setAttribute("orderId", orderId);
                request.setAttribute("viewTitle", "Transactions for Order #" + orderId);
                request.getRequestDispatcher("/WEB-INF/views/admin/transactions/list.jsp")
                        .forward(request, response);
            } catch (SQLException e) {
                Flash.error(request, "Error loading transactions: " + e.getMessage());
                response.sendRedirect(request.getContextPath() + "/admin/orders?id=" + orderId);
            }
            return;
        }

        // List view with filtering
        Transaction.TransactionStatus status = null;
        String statusParam = request.getParameter("status");
        if (statusParam != null && !statusParam.isEmpty()) {
            try {
                status = Transaction.TransactionStatus.valueOf(statusParam);
            } catch (IllegalArgumentException ignored) {
                status = null;
            }
        }

        String paymentMethod = request.getParameter("paymentMethod");
        if (paymentMethod != null && paymentMethod.isEmpty()) {
            paymentMethod = null;
        }

        int page = parsePage(request.getParameter("page"));
        int total = 0;
        try {
            total = app().transactionService().countTransactions(status, paymentMethod);
        } catch (SQLException e) {
            Flash.error(request, "Error counting transactions: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin");
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        if (page > totalPages) {
            page = totalPages;
        }
        int offset = (page - 1) * PAGE_SIZE;

        List<Transaction> transactions;
        try {
            transactions = app().transactionService().getAllTransactions(status, paymentMethod, PAGE_SIZE, offset);
        } catch (SQLException e) {
            Flash.error(request, "Error loading transactions: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin");
            return;
        }

        // Get statistics for dashboard
        TransactionRepository.TransactionStats stats;
        try {
            stats = app().transactionService().getTransactionStats();
        } catch (SQLException e) {
            stats = new TransactionRepository.TransactionStats();
        }

        request.setAttribute("transactions", transactions);
        request.setAttribute("selectedStatus", status);
        request.setAttribute("selectedPaymentMethod", paymentMethod);
        request.setAttribute("page", page);
        request.setAttribute("totalPages", totalPages);
        request.setAttribute("totalTransactions", total);
        request.setAttribute("stats", stats);
        request.setAttribute("viewTitle", "All Transactions");
        request.getRequestDispatcher("/WEB-INF/views/admin/transactions/list.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer transactionId = ValidationUtil.parseInt(request.getParameter("transactionId"));
        String action = request.getParameter("action");
        if (transactionId == null || !"refund".equals(action)) {
            Flash.error(request, "Invalid request.");
            response.sendRedirect(request.getContextPath() + "/admin/transactions");
            return;
        }

        try {
            Transaction payment = app().transactionService().getTransaction(transactionId);
            if (payment == null) {
                Flash.error(request, "That transaction no longer exists.");
                response.sendRedirect(request.getContextPath() + "/admin/transactions");
                return;
            }
            Order order = loadOrderQuietly(payment.getOrderId());

            // Refuse before writing anything rather than letting the refund land and
            // the order change fail afterwards. Same precondition the detail view
            // uses to decide whether to offer the button, so an admin is never shown
            // an action that this then rejects.
            String blocked = refundBlockedBecause(payment, order,
                    findSettledRefund(app().transactionService().getTransactionsByOrder(payment.getOrderId())));
            if (blocked != null) {
                Flash.error(request, blocked);
                response.sendRedirect(request.getContextPath() + "/admin/transactions?id=" + transactionId);
                return;
            }

            User admin = (User) request.getSession().getAttribute("user");
            String actor = admin != null ? admin.getUsername() : "UNKNOWN";
            long refundId = app().transactionService().refundPayment(transactionId, actor);

            // The ledger row is committed by now, so from here a failure is reported
            // as "the refund happened, the order did not" rather than as a failed
            // refund. An admin reading the transaction page sees the refund row and
            // can finish the order by hand; the reverse ordering would have returned
            // stock against a payment that was never handed back.
            String outcome;
            try {
                app().orderService().updateStatus(payment.getOrderId(), Order.Status.REFUNDED, actor,
                        "Refunded by transaction #" + refundId + " (" + transactionId + ")");
                outcome = "Transaction #" + transactionId + " refunded in full. "
                        + "Refund #" + refundId + " recorded and order #" + payment.getOrderId()
                        + " marked refunded with its stock returned.";
                Flash.success(request, outcome);
            } catch (ValidationException | NotFoundException e) {
                // The order moved under us between the check above and here.
                outcome = "Refund #" + refundId + " was recorded for transaction #" + transactionId
                        + ", but order #" + payment.getOrderId() + " was not updated: " + e.getMessage()
                        + " Set the order to refunded by hand.";
                Flash.warning(request, outcome);
            }
            AuditLogger.logAdminAction("TRANSACTION_REFUND", actor,
                    "transaction #" + transactionId, outcome);
        } catch (ValidationException e) {
            Flash.error(request, e.getMessage());
        } catch (SQLException e) {
            Flash.error(request, "Could not refund this transaction: " + e.getMessage());
        }
        response.sendRedirect(request.getContextPath() + "/admin/transactions?id=" + transactionId);
    }

    /**
     * Loads the transaction detail, with everything the refund panel needs to decide
     * whether to offer itself.
     */
    private void showDetail(HttpServletRequest request, HttpServletResponse response, long transactionId)
            throws ServletException, IOException {
        try {
            Transaction transaction = app().transactionService().getTransaction(transactionId);
            if (transaction == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            // The detail view's "every transaction on order #N" table reads this
            // attribute and nothing was setting it, so the table only ever rendered
            // its own empty state. Those rows are also what tells an admin that this
            // payment has already been refunded.
            List<Transaction> orderTransactions =
                    app().transactionService().getTransactionsByOrder(transaction.getOrderId());
            request.setAttribute("transaction", transaction);
            request.setAttribute("orderTransactions", orderTransactions);
            Order order = loadOrderQuietly(transaction.getOrderId());
            request.setAttribute("order", order);
            Transaction settledRefund = findSettledRefund(orderTransactions);
            request.setAttribute("settledRefund", settledRefund);
            String blocked = refundBlockedBecause(transaction, order, settledRefund);
            request.setAttribute("refundBlockedReason", blocked);
            request.getRequestDispatcher("/WEB-INF/views/admin/transactions/detail.jsp")
                    .forward(request, response);
        } catch (SQLException e) {
            Flash.error(request, "Error loading transaction: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/transactions");
        }
    }

    /**
     * The order behind a transaction, or null when it cannot be loaded.
     *
     * <p>Best effort on purpose. A transaction whose order has gone missing should
     * still render its detail page; the order is needed for the refund decision,
     * not for describing the row, and {@link #refundBlockedBecause} refuses to
     * refund without one.
     */
    private Order loadOrderQuietly(int orderId) {
        try {
            return app().orderService().getOrder(orderId);
        } catch (NotFoundException e) {
            return null;
        }
    }

    /**
     * Why this transaction cannot be refunded right now, or null when it can.
     *
     * <p>One place, called from both the detail view and the refund POST, because a
     * button that appears only to be refused with a different message is worse than
     * no button. {@link TransactionService#refundPayment} re-checks the first three
     * conditions inside its own transaction -- this is the cheap pre-check that saves
     * the admin an error page, not the guard, and the rule is repeated here
     * deliberately so the two can be read against each other.
     *
     * <p>The last condition is the one that is not a ledger rule. Refunding is what
     * moves an order to REFUNDED and returns its products to stock, and the order
     * state machine only allows that from COMPLETED. An order that is still
     * processing has nothing to restock correctly, so it is refused rather than
     * half-refunded.
     */
    private String refundBlockedBecause(Transaction transaction, Order order,
                                        Transaction settledRefund) {
        if (transaction.getTransactionType() != Transaction.TransactionType.PAYMENT) {
            return "Only a payment can be refunded. Transaction #" + transaction.getTransactionId()
                    + " is a " + transaction.getTransactionType().name().toLowerCase() + " row.";
        }
        if (transaction.getStatus() != Transaction.TransactionStatus.COMPLETED) {
            return "Only a completed payment can be refunded. Transaction #"
                    + transaction.getTransactionId() + " is "
                    + transaction.getStatus().name().toLowerCase() + ".";
        }
        if (settledRefund != null) {
            return "Order #" + transaction.getOrderId() + " was already refunded by transaction #"
                    + settledRefund.getTransactionId() + ".";
        }
        if (order == null) {
            return "The order behind this payment could not be loaded, so it cannot be refunded safely.";
        }
        if (order.getStatus() != Order.Status.COMPLETED) {
            return "Order #" + order.getOrderId() + " is " + order.getStatus().name().toLowerCase()
                    + ". Only a completed order can be refunded, because refunding an order is what"
                    + " returns its products to stock.";
        }
        return null;
    }

    /**
     * The settled refund against an order, or null when no money has been returned.
     *
     * <p>PARTIAL_REFUND is matched as well as REFUND even though only whole refunds
     * are issued today, so an order that somehow carries a partial row is treated as
     * already refunded instead of being refunded a second time on top of it.
     */
    private Transaction findSettledRefund(List<Transaction> orderTransactions) {
        for (Transaction row : orderTransactions) {
            boolean isRefund = row.getTransactionType() == Transaction.TransactionType.REFUND
                    || row.getTransactionType() == Transaction.TransactionType.PARTIAL_REFUND;
            if (isRefund && row.getStatus() == Transaction.TransactionStatus.COMPLETED) {
                return row;
            }
        }
        return null;
    }

    private static int parsePage(String raw) {
        Integer page = ValidationUtil.parseInt(raw);
        return page == null || page < 1 ? 1 : page;
    }
}