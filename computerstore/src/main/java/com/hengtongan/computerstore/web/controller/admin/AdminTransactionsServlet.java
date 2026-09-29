package com.hengtongan.computerstore.web.controller.admin;

import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.core.repository.TransactionRepository;
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
 */
@WebServlet("/admin/transactions")
public class AdminTransactionsServlet extends BaseServlet {

    private static final int PAGE_SIZE = 25;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer transactionId = ValidationUtil.parseInt(request.getParameter("id"));
        if (transactionId != null) {
            try {
                Transaction transaction = app().transactionService().getTransaction(transactionId);
                if (transaction == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                request.setAttribute("transaction", transaction);
                request.getRequestDispatcher("/WEB-INF/views/admin/transactions/detail.jsp")
                        .forward(request, response);
            } catch (SQLException e) {
                Flash.error(request, "Error loading transaction: " + e.getMessage());
                response.sendRedirect(request.getContextPath() + "/admin/transactions");
            }
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
        // Currently no POST operations needed for transactions
        // This can be extended for refund processing or manual status updates
        Flash.warning(request, "Transaction management operations are read-only.");
        response.sendRedirect(request.getContextPath() + "/admin/transactions");
    }

    private static int parsePage(String raw) {
        Integer page = ValidationUtil.parseInt(raw);
        return page == null || page < 1 ? 1 : page;
    }
}