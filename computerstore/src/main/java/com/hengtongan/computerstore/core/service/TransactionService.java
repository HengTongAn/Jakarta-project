package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.repository.TransactionRepository;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.web.AuditLogger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Transaction tracking service for comprehensive payment monitoring.
 * This service tracks all financial transactions separately from orders,
 * enabling proper reconciliation, refund management, and audit trails.
 */
public class TransactionService {

    private final TransactionRepository transactionRepository = new TransactionRepository();

    /**
     * Creates a new payment transaction record when payment is initiated.
     * This should be called before processing payment with the gateway.
     *
     * @param orderId The order ID this transaction belongs to
     * @param userId The user ID making the payment
     * @param amount The transaction amount
     * @param paymentMethod The payment method (card, aba, etc.)
     * @return The created transaction ID
     */
    public long createPaymentTransaction(int orderId, int userId, BigDecimal amount, String paymentMethod) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Transaction transaction = new Transaction();
                transaction.setOrderId(orderId);
                transaction.setUserId(userId);
                transaction.setTransactionType(Transaction.TransactionType.PAYMENT);
                transaction.setStatus(Transaction.TransactionStatus.PENDING);
                transaction.setAmount(amount);
                transaction.setCurrency("USD");
                transaction.setPaymentMethod(paymentMethod);

                long transactionId = transactionRepository.create(c, transaction);

                // Log the transaction creation
                AuditLogger.logDataModification(
                    "TRANSACTION_CREATED",
                    String.valueOf(userId),
                    "Transaction",
                    String.valueOf(transactionId),
                    "Payment transaction created for order " + orderId + " amount " + amount
                );

                c.commit();
                return transactionId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Updates a transaction status after payment processing.
     * Called after receiving response from payment gateway.
     *
     * @param transactionId The transaction ID to update
     * @param status The new transaction status
     * @param gatewayTransactionId The gateway's transaction ID
     * @param responseCode The gateway response code
     * @param responseMessage The gateway response message
     */
    public void updateTransactionStatus(long transactionId, Transaction.TransactionStatus status,
                                       String gatewayTransactionId, String responseCode, String responseMessage) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                transactionRepository.updateStatus(c, transactionId, status, 
                    gatewayTransactionId, responseCode, responseMessage);

                // Log the status update
                Transaction transaction = transactionRepository.findById(transactionId);
                if (transaction != null) {
                    AuditLogger.logDataModification(
                        "TRANSACTION_STATUS_UPDATED",
                        String.valueOf(transaction.getUserId()),
                        "Transaction",
                        String.valueOf(transactionId),
                        "Status updated to " + status + " for order " + transaction.getOrderId()
                    );
                }

                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Creates a refund transaction for an existing payment.
     *
     * @param orderId The original order ID
     * @param userId The user ID requesting refund
     * @param amount The refund amount
     * @param paymentMethod The original payment method
     * @param originalTransactionId The original payment transaction ID
     * @return The created refund transaction ID
     */
    public long createRefundTransaction(int orderId, int userId, BigDecimal amount, 
                                       String paymentMethod, long originalTransactionId) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                Transaction transaction = new Transaction();
                transaction.setOrderId(orderId);
                transaction.setUserId(userId);
                transaction.setTransactionType(Transaction.TransactionType.REFUND);
                transaction.setStatus(Transaction.TransactionStatus.PENDING);
                transaction.setAmount(amount);
                transaction.setCurrency("USD");
                transaction.setPaymentMethod(paymentMethod);
                transaction.setGatewayTransactionId("REFUND-" + originalTransactionId);

                long transactionId = transactionRepository.create(c, transaction);

                // Log the refund creation
                AuditLogger.logDataModification(
                    "REFUND_TRANSACTION_CREATED",
                    String.valueOf(userId),
                    "Transaction",
                    String.valueOf(transactionId),
                    "Refund transaction created for order " + orderId + " amount " + amount
                );

                c.commit();
                return transactionId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Gets a transaction by ID.
     */
    public Transaction getTransaction(long transactionId) throws SQLException {
        return transactionRepository.findById(transactionId);
    }

    /**
     * Gets all transactions for a specific order.
     */
    public List<Transaction> getTransactionsByOrder(int orderId) throws SQLException {
        return transactionRepository.findByOrderId(orderId);
    }

    /**
     * Gets all transactions for a specific user.
     */
    public List<Transaction> getTransactionsByUser(int userId) throws SQLException {
        return transactionRepository.findByUserId(userId);
    }

    /**
     * Gets all transactions with optional filtering for admin view.
     */
    public List<Transaction> getAllTransactions(Transaction.TransactionStatus statusFilter, 
                                               String paymentMethodFilter, int limit, int offset) throws SQLException {
        return transactionRepository.findAll(statusFilter, paymentMethodFilter, limit, offset);
    }

    /**
     * Counts total transactions with optional filtering.
     */
    public int countTransactions(Transaction.TransactionStatus statusFilter, 
                                String paymentMethodFilter) throws SQLException {
        return transactionRepository.count(statusFilter, paymentMethodFilter);
    }

    /**
     * Gets transaction statistics for admin dashboard.
     */
    public TransactionRepository.TransactionStats getTransactionStats() throws SQLException {
        return transactionRepository.getStats();
    }

    /**
     * Finds a transaction by gateway transaction ID for reconciliation.
     */
    public Transaction findByGatewayTransactionId(String gatewayTransactionId) throws SQLException {
        return transactionRepository.findByGatewayTransactionId(gatewayTransactionId);
    }

    /**
     * Records a failed payment attempt.
     */
    public void recordFailedPayment(long transactionId, String gatewayTransactionId, 
                                   String responseCode, String responseMessage) throws SQLException {
        updateTransactionStatus(transactionId, Transaction.TransactionStatus.FAILED,
            gatewayTransactionId, responseCode, responseMessage);
    }

    /**
     * Records a successful payment.
     */
    public void recordSuccessfulPayment(long transactionId, String gatewayTransactionId, 
                                      String responseCode, String responseMessage) throws SQLException {
        updateTransactionStatus(transactionId, Transaction.TransactionStatus.COMPLETED,
            gatewayTransactionId, responseCode, responseMessage);
    }
}