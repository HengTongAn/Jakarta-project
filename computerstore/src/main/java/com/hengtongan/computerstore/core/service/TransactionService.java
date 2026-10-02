package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.domain.entity.Transaction;
import com.hengtongan.computerstore.core.exception.ValidationException;
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
     * Refunds a settled payment, in full, and returns the new refund row's id.
     *
     * <h2>Whole refunds only</h2>
     * There is no {@code refunded_amount} column, so "how much of this payment has
     * already gone back" can only be answered by summing the refund rows against
     * the order. That is enough to refuse a second full refund, which is the case
     * that actually costs money, and not enough to support arbitrary amounts
     * without a half-written guard. Rather than add a partial refund that cannot
     * be proven correct here, this refunds exactly what was paid and the button
     * offers nothing else.
     *
     * <h2>The payment row is left COMPLETED</h2>
     * A refund is a second movement, not a correction of the first. Money came in;
     * money went out; the ledger shows both rows and the net is zero. Overwriting
     * the payment to REFUNDED instead would erase the fact that the customer ever
     * paid, and would take the receipt with it -- {@code findSettledPaymentForUser}
     * looks for a COMPLETED payment, so a refunded order would stop being able to
     * produce the document a chargeback is argued from. The order carries the
     * business state and is what {@code updateStatus} moves to REFUNDED.
     *
     * <h2>No gateway is called</h2>
     * {@code AbaPaywayClient} exposes precreate and queryStatus and nothing else,
     * and the card flow is simulated, so there is no provider endpoint to hand the
     * money back through. This records that a refund is owed and has been decided.
     * Until a real refund call is wired in, the gateway columns are filled in to
     * say so explicitly rather than left to imply a provider confirmation that
     * never arrived.
     *
     * <p>This deliberately does not touch the order. {@code OrderService.updateStatus}
     * owns that transition, and owns restocking and the status timeline along with
     * it; the caller runs it after this returns. Doing it in that order means a
     * failure between the two leaves a refund recorded against a still-delivered
     * order, which an admin can see and finish, rather than stock returned against
     * a payment that was never given back.
     *
     * @param transactionId the {@code PAYMENT} row to refund
     * @param actor the admin deciding the refund, for the audit trail
     * @return the id of the created refund row
     * @throws ValidationException if the row is not a settled, unrefunded payment
     */
    public long refundPayment(long transactionId, String actor) throws SQLException {
        // Read outside the write transaction, like createPaymentTransaction and
        // updateTransactionStatus already do. Everything copied off this row below
        // is settled at payment time and never edited afterwards.
        Transaction payment = transactionRepository.findById(transactionId);
        if (payment == null) {
            throw new ValidationException("That transaction no longer exists.");
        }
        // Refunding a refund row would mint money rather than move it, and it is
        // reachable through the same admin URL as a payment.
        if (payment.getTransactionType() != Transaction.TransactionType.PAYMENT) {
            throw new ValidationException("Transaction #" + transactionId + " is a "
                    + payment.getTransactionType().name().toLowerCase()
                    + " row, not a payment. Only a payment can be refunded.");
        }
        // A failed or pending payment never took the money, so there is nothing to
        // return and a refund would invent a movement that did not happen.
        if (payment.getStatus() != Transaction.TransactionStatus.COMPLETED) {
            throw new ValidationException("Transaction #" + transactionId + " is "
                    + payment.getStatus().name().toLowerCase()
                    + ", not completed. Only a completed payment can be refunded.");
        }

        String refundActor = (actor == null || actor.isBlank()) ? "UNKNOWN" : actor;
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Inside the write transaction, so this sees its own insert. The
                // check and the insert have to be one unit of work or two admins in
                // two tabs both get past it.
                if (transactionRepository.hasSettledRefund(c, payment.getOrderId())) {
                    throw new ValidationException("Order #" + payment.getOrderId()
                            + " has already been refunded. Reload the transaction to see it.");
                }

                Transaction refund = new Transaction();
                refund.setOrderId(payment.getOrderId());
                // The customer's own id, read off the payment, never taken from the
                // caller: the column is NOT NULL and a supplied id would file this
                // refund against somebody else's account.
                refund.setUserId(payment.getUserId());
                refund.setTransactionType(Transaction.TransactionType.REFUND);
                // COMPLETED, not PENDING. Nothing is going to call back and settle
                // this row, and a refund stuck at PENDING forever reads as a payment
                // that failed to go out rather than one that was handed back.
                refund.setStatus(Transaction.TransactionStatus.COMPLETED);
                refund.setAmount(payment.getAmount());
                // Copied off the payment rather than defaulted: a payment taken in
                // another currency must not be handed back labelled as USD.
                refund.setCurrency(payment.getCurrency());
                refund.setPaymentMethod(payment.getPaymentMethod());
                // gatewayTransactionId stays null. There is no provider refund
                // reference to store, and inventing one shaped like a real gateway
                // id would be the single most misleading value in the table.
                refund.setGatewayResponseCode("LEDGER");
                refund.setGatewayResponseMessage(
                        "Refunded in full by " + refundActor + ". No payment provider was called.");

                long refundId = transactionRepository.create(c, refund);
                c.commit();

                AuditLogger.logDataModification(
                    "REFUND_TRANSACTION_CREATED",
                    String.valueOf(payment.getUserId()),
                    "Transaction",
                    String.valueOf(refundId),
                    "Full refund of transaction " + transactionId + " for order "
                            + payment.getOrderId() + " by " + refundActor
                );
                return refundId;
            } catch (SQLException | RuntimeException e) {
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
     * Gets a single transaction, constrained on the customer who owns it.
     *
     * <p>This is the only transaction getter a customer page may call.
     * {@link #getTransaction(long)} is keyed on the id alone and would render
     * any customer's card payment for an id typed into the URL.</p>
     */
    public Transaction getTransactionForUser(long transactionId, int userId) throws SQLException {
        return transactionRepository.findByIdForUser(transactionId, userId);
    }

    /**
     * Gets an order's transactions, constrained on the customer who owns them.
     * The order id in a customer URL is as user-supplied as a transaction id.
     */
    public List<Transaction> getTransactionsByOrderForUser(int orderId, int userId) throws SQLException {
        return transactionRepository.findByOrderIdForUser(orderId, userId);
    }

    /**
     * The settled payment for an order, constrained on the customer who owns it.
     *
     * <p>What the post-payment flow redirects to. Naming is load-bearing: a customer
     * controller that wants "the receipt for this order" should reach it through
     * a method whose name says the row is theirs, the same reason
     * {@link #getTransactionForUser(long, int)} is spelled the way it is.</p>
     *
     * <p>Returns null rather than a fallback row. An order with no completed
     * payment has nothing to receipt, and the caller shows the order page
     * instead; substituting the most recent attempt would print a declined card
     * as though it had been paid.</p>
     *
     * @param orderId the order the customer just paid for
     * @param userId the signed-in customer
     * @return the completed payment, or null when there is not one
     */
    public Transaction getSettledPaymentForUser(int orderId, int userId) throws SQLException {
        return transactionRepository.findSettledPaymentForUser(orderId, userId);
    }

    /**
     * Gets transaction statistics for one customer, for their own dashboard.
     * {@link #getTransactionStats()} totals every customer's rows.
     */
    public TransactionRepository.TransactionStats getTransactionStatsForUser(int userId) throws SQLException {
        return transactionRepository.getStatsForUser(userId);
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