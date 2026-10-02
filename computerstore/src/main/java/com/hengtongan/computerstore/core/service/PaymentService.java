package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.config.PaymentConfig;
import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.OrderStatusEvent;
import com.hengtongan.computerstore.core.domain.entity.Payment;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.repository.AppSettingsRepository;
import com.hengtongan.computerstore.core.repository.OrderRepository;
import com.hengtongan.computerstore.core.repository.PaymentRepository;
import com.hengtongan.computerstore.infrastructure.payment.AbaPaywayClient;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.validation.CardValidator;
import com.hengtongan.computerstore.util.web.AuditLogger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Order payment: which methods are offered, starting a charge, and -- the part
 * that matters -- deciding whether an order has actually been paid.
 *
 * <h2>Why confirmation re-queries the gateway</h2>
 * The customer is redirected back with a status in the query string. That URL is
 * customer-controlled: anyone can visit
 * {@code /payment/aba/return?order=12&status=1} by hand and skip the payment
 * entirely. So the status arriving in the browser is treated as a hint only.
 * {@link #confirm(int, String)} always asks the gateway, and only a gateway
 * answer of {@code SUCCESS} moves an order to paid.
 *
 * <h2>Idempotency</h2>
 * Confirming an already-paid order is a no-op, not a second credit: the status
 * transition is guarded and the unique index on
 * {@code payments.transaction_id} stops a duplicate landing twice.
 */
public class PaymentService {

    public static final String METHOD_COD = "COD";
    public static final String METHOD_ABA = "ABA_PAYWAY";
    public static final String METHOD_CARD = "VISA_CARD";
    public static final String PROVIDER_ABA = "ABA";
    public static final String PROVIDER_CARD = "VISA";

    public static final String STATUS_UNPAID = "UNPAID";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private final PaymentRepository paymentRepository = new PaymentRepository();
    private final OrderRepository orderRepository = new OrderRepository();
    private final AbaPaywayClient gateway = new AbaPaywayClient();
    private final TransactionService transactionService = new TransactionService();

    /** What the checkout page needs to render the payment options. */
    public boolean isAbaAvailable() {
        return PaymentConfig.isEnabled();
    }

    public boolean isSimulated() {
        return PaymentConfig.isSimulate();
    }

    /** Whether the Visa card option is offered at checkout. */
    public boolean isCardAvailable() {
        return PaymentConfig.isCardAvailable();
    }

    public boolean isCardSimulated() {
        return PaymentConfig.isCardSimulated();
    }

    /**
     * Maps a submitted form value to a stored method, rejecting anything
     * unrecognised. An unknown value must never silently become cash on
     * delivery, or a tampered form could downgrade a real payment.
     */
    public String normaliseMethod(String submitted) {
        String value = submitted == null ? "" : submitted.trim().toLowerCase();
        return switch (value) {
            case "aba", "aba_payway", "abapay", "aba payway" -> METHOD_ABA;
            case "visa", "card", "visa_card", "credit_card", "creditcard" -> METHOD_CARD;
            case "cash", "cod", "cash_on_delivery" -> METHOD_COD;
            default -> throw new ValidationException("Choose a valid payment method.");
        };
    }

    /**
     * Opens a charge with the gateway and records the attempt.
     * <p>
     * The QR comes back from the gateway exactly once, so it is persisted onto
     * the attempt rather than handed to a view: a customer who refreshes the
     * payment page must still have something to scan.
     *
     * @return the recorded attempt
     * @throws ValidationException if the gateway refuses to start the charge
     */
    public Payment startAbaPayment(int orderId, String baseUrl) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new NotFoundException("Order not found.");
        }
        if (STATUS_PAID.equals(order.getPaymentStatus())) {
            throw new ValidationException("This order is already paid.");
        }

        // Create transaction record for payment tracking
        long transactionId = 0;
        try {
            transactionId = transactionService.createPaymentTransaction(
                orderId, order.getUserId(), order.getTotalAmount(), METHOD_ABA);
        } catch (SQLException e) {
            // Log but don't fail payment if transaction tracking fails
            System.err.println("Failed to create transaction record: " + e.getMessage());
        }

        AbaPaywayClient.PrecreateResult result = gateway.precreate(
                orderId, order.getTotalAmount(), "Order #" + orderId + " - " + PaymentConfig.shopName(), baseUrl);
        if (!result.ok()) {
            recordAttempt(orderId, null, Payment.Status.FAILED, result.message(), null, null);
            markOrder(orderId, STATUS_FAILED, null, PROVIDER_ABA);
            
            // Update transaction status to failed
            if (transactionId > 0) {
                try {
                    transactionService.recordFailedPayment(transactionId, null, "GATEWAY_ERROR", result.message());
                } catch (SQLException e) {
                    System.err.println("Failed to update transaction status: " + e.getMessage());
                }
            }
            
            throw new ValidationException("Could not start the payment: " + result.message());
        }

        Payment payment = recordAttempt(orderId, result.transactionId(), Payment.Status.PENDING,
                result.message(), result.qrImage(), result.abaPhone());
        markOrder(orderId, STATUS_PENDING, result.transactionId(), PROVIDER_ABA);
        
        // Update transaction status with gateway transaction ID
        if (transactionId > 0) {
            try {
                transactionService.updateTransactionStatus(transactionId, 
                    com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus.PROCESSING,
                    result.transactionId(), "INITIATED", result.message());
            } catch (SQLException e) {
                System.err.println("Failed to update transaction status: " + e.getMessage());
            }
        }
        
        audit("PAYMENT_STARTED", orderId, "ABA Payway transaction " + result.transactionId());
        return payment;
    }

    /**
     * Authorises a card payment and settles the order in one step.
     * <p>
     * Cards differ from ABA in shape: there is no "go to the bank and come back"
     * step, so authorisation happens here and the answer is known immediately.
     * The order is still left {@code PENDING} on a decline rather than cancelled,
     * for the same reason ABA failures are -- a wrong "failed" is recoverable
     * (the customer tries another card), a wrong "paid" is not.
     *
     * @param card already reduced to a brand and last four by
     *             {@link CardValidator}. The card number is not a parameter and
     *             is not retained: by the time this is called it is unreachable.
     * @return the recorded attempt, PAID or FAILED
     * @throws ValidationException if the card cannot be taken at all
     */
    public Payment startCardPayment(int orderId, CardValidator.CardDetails card) {
        if (!PaymentConfig.isCardAvailable()) {
            throw new ValidationException(reasonCardUnavailable());
        }
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new NotFoundException("Order not found.");
        }
        if (STATUS_PAID.equals(order.getPaymentStatus())) {
            throw new ValidationException("This order is already paid.");
        }
        if (!PaymentConfig.isCardSimulated()) {
            // Unreachable while isCardAvailable() holds, but stated rather than
            // assumed: if an acquirer is ever wired in, this is the branch that
            // must become a real authorisation call, and it should fail loudly
            // until it has.
            throw new ValidationException("Card payments are not available in this configuration.");
        }

        // Create transaction record for payment tracking
        long transactionId = 0;
        try {
            transactionId = transactionService.createPaymentTransaction(
                orderId, order.getUserId(), order.getTotalAmount(), METHOD_CARD);
        } catch (SQLException e) {
            // Log but don't fail payment if transaction tracking fails
            System.err.println("Failed to create transaction record: " + e.getMessage());
        }

        String reference = simulatedReference(orderId, card);
        if (card.approved()) {
            // Recorded PENDING first, then promoted by markPaid inside the same
            // transaction that settles the order. Inserting it as PAID up front
            // would leave a PAID payment attached to an unpaid order if the
            // settlement then failed -- the one inconsistency that would let an
            // order look paid in history and unpaid in the storefront.
            recordCardAttempt(orderId, reference, Payment.Status.PENDING, card, card.message());
            markOrder(orderId, STATUS_PENDING, reference, PROVIDER_CARD);
            // The CAS only succeeds while the order is PENDING and not already
            // paid. It can fail if the customer cancelled the order between
            // checkout and entering their card, in which case the authorisation
            // must not be written up as a completed payment against a cancelled
            // order. See the same handling in confirm(int, String).
            boolean settled = markPaid(orderId, reference, "card " + card.masked());
            Order current = orderRepository.findById(orderId);
            boolean alreadyPaid = current != null && STATUS_PAID.equals(current.getPaymentStatus());

            if (settled || alreadyPaid) {
                if (transactionId > 0) {
                    try {
                        transactionService.recordSuccessfulPayment(transactionId, reference, "APPROVED", card.message());
                    } catch (SQLException e) {
                        System.err.println("Failed to update transaction status: " + e.getMessage());
                    }
                }
                audit("PAYMENT_CONFIRMED", orderId, "Card " + card.masked() + " (demo authorisation)");
            } else {
                String actualStatus = current == null ? "missing" : current.getStatus().name();
                audit("PAYMENT_UNRECONCILED", orderId,
                        "Card " + card.masked() + " authorised (demo) against a " + actualStatus
                                + " order; the order was left unchanged and needs manual reconciliation.");
                updateAttempt(orderId, reference, Payment.Status.PAID,
                        "Card authorised but the order is " + actualStatus
                                + "; the order was left unchanged and needs manual reconciliation.");
                if (transactionId > 0) {
                    try {
                        transactionService.updateTransactionStatus(transactionId,
                                com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus.PENDING,
                                reference, "APPROVED",
                                "Card authorised against a " + actualStatus
                                        + " order - awaiting reconciliation");
                    } catch (SQLException e) {
                        System.err.println("Failed to record unreconciled payment: " + e.getMessage());
                    }
                }
            }
            // Re-read rather than returning the pre-settlement object: the caller
            // branches on the status, and the row just promoted to PAID is the
            // newest for this order, so this is the same row.
            return latestFor(orderId);
        }

        // Left PENDING so the customer can retry with another card. Marking the
        // order failed here would strand stock behind an order nobody can pay for.
        Payment payment = recordCardAttempt(orderId, reference, Payment.Status.FAILED, card, card.message());
        markOrder(orderId, STATUS_PENDING, reference, PROVIDER_CARD);
        
        // Update transaction status to failed
        if (transactionId > 0) {
            try {
                transactionService.recordFailedPayment(transactionId, reference, "DECLINED", card.message());
            } catch (SQLException e) {
                System.err.println("Failed to update transaction status: " + e.getMessage());
            }
        }
        
        audit("PAYMENT_DECLINED", orderId, "Card " + card.masked() + " declined (demo)");
        return payment;
    }

    /**
     * A demo authorisation reference. Shaped like a real one so the receipt and
     * the admin list read correctly, and namespaced so it can never collide with
     * a real gateway reference in the unique index on transaction_id.
     */
    private String simulatedReference(int orderId, CardValidator.CardDetails card) {
        int mixed = (int) (orderId * 2654435761L & 0xFFFFFFL);
        return "DEMO-" + card.last4() + "-" + Integer.toHexString(mixed).toUpperCase();
    }

    private String reasonCardUnavailable() {
        String reason = PaymentConfig.cardBlockingReason();
        return reason == null ? "Card payments are not available right now." : reason;
    }

    private Payment recordCardAttempt(int orderId, String transactionId, Payment.Status status,
                                      CardValidator.CardDetails card, String message) {
        return paymentRepository.insert(new Payment(orderId, PROVIDER_CARD, transactionId,
                currentAmount(orderId), PaymentConfig.currency(), status, message,
                null, null, card.brand(), card.last4()));
    }

    /**
     * Settles an order against the gateway's own answer.
     *
     * @param browserHint the status from the redirect, used only to choose the
     *                    wording on screen. Never used to decide that money
     *                    arrived.
     * @return the confirmed status
     */
    public Payment.Status confirm(int orderId, String browserHint) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            throw new NotFoundException("Order not found.");
        }
        if (STATUS_PAID.equals(order.getPaymentStatus())) {
            return Payment.Status.PAID;
        }

        String transactionId = order.getPaymentTransaction();
        if (transactionId == null || transactionId.isBlank()) {
            throw new ValidationException("This order has no payment in progress.");
        }

        AbaPaywayClient.StatusResult result = gateway.queryStatus(orderId, transactionId);
        if (!result.ok()) {
            // Could not reach the gateway. Leave the order pending rather than
            // guessing: a wrong "failed" is recoverable, a wrong "paid" is not.
            throw new ValidationException("Could not confirm the payment with the gateway. "
                    + "It has been left pending - please refresh in a moment.");
        }

        Payment.Status confirmed = Payment.Status.fromWire(result.status());
        
        // Find and update the corresponding transaction
        com.hengtongan.computerstore.core.domain.entity.Transaction transaction = null;
        try {
            List<com.hengtongan.computerstore.core.domain.entity.Transaction> transactions = 
                transactionService.getTransactionsByOrder(orderId);
            transaction = transactions.stream().filter(t -> t.getGatewayTransactionId() != null && 
                t.getGatewayTransactionId().equals(transactionId)).findFirst().orElse(null);
        } catch (SQLException e) {
            System.err.println("Failed to fetch transactions: " + e.getMessage());
        }
        
        if (confirmed == Payment.Status.PAID) {
            // markPaid is a compare-and-set on (status = PENDING, payment_status <>
            // PAID). It can fail for two very different reasons, and this method
            // used to treat both as success: it ignored the return, then audited
            // PAYMENT_CONFIRMED and completed the transaction. A replayed
            // callback really is a success, but an order the customer had already
            // cancelled is not -- the gateway said the money arrived after
            // cancellation returned the stock, so the order stayed CANCELLED
            // while the ledger recorded a completed payment against it and
            // nothing anywhere flagged that a refund was owed.
            boolean marked = markPaid(orderId, transactionId, "ABA Payway (" + transactionId + ")");
            Order current = orderRepository.findById(orderId);
            boolean alreadyPaid = current != null && STATUS_PAID.equals(current.getPaymentStatus());

            if (marked || alreadyPaid) {
                audit("PAYMENT_CONFIRMED", orderId, "ABA Payway transaction " + transactionId);
                if (transaction != null) {
                    try {
                        transactionService.recordSuccessfulPayment(transaction.getTransactionId(),
                            transactionId, result.status(), result.message());
                    } catch (SQLException e) {
                        System.err.println("Failed to update transaction status: " + e.getMessage());
                    }
                }
            } else {
                // Money in, order not PENDING. Record what the gateway said so it
                // is visible on the order and in the ledger, but do not pretend
                // the order is paid: it may need a refund or a manual review.
                String actualStatus = current == null ? "missing" : current.getStatus().name();
                audit("PAYMENT_UNRECONCILED", orderId,
                        "gateway reported PAID for transaction " + transactionId
                                + " but the order is " + actualStatus
                                + " and was not transitioned to PROCESSING; manual refund or review required");
                updateAttempt(orderId, transactionId, Payment.Status.PAID,
                        "Gateway reported PAID but the order is " + actualStatus
                                + "; the order was left unchanged and needs manual reconciliation.");
                if (transaction != null) {
                    try {
                        transactionService.updateTransactionStatus(
                                transaction.getTransactionId(),
                                com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus.PENDING,
                                transactionId, result.status(),
                                "Payment received against a " + actualStatus
                                        + " order - awaiting reconciliation");
                    } catch (SQLException e) {
                        System.err.println("Failed to record unreconciled payment: " + e.getMessage());
                    }
                }
            }
        } else {
            String orderStatus = switch (confirmed) {
                case CANCELLED -> STATUS_CANCELLED;
                case FAILED -> STATUS_FAILED;
                case EXPIRED -> STATUS_EXPIRED;
                default -> STATUS_PENDING;
            };
            updateAttempt(orderId, transactionId, confirmed, result.message());
            markOrder(orderId, orderStatus, transactionId, PROVIDER_ABA);
            audit("PAYMENT_" + confirmed, orderId, "transaction " + transactionId);
            
            // Update transaction status to failed/cancelled
            if (transaction != null) {
                try {
                    com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus txStatus = 
                        switch (confirmed) {
                            case CANCELLED -> com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus.REFUNDED;
                            case FAILED -> com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus.FAILED;
                            case EXPIRED -> com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus.FAILED;
                            default -> com.hengtongan.computerstore.core.domain.entity.Transaction.TransactionStatus.PENDING;
                        };
                    transactionService.updateTransactionStatus(transaction.getTransactionId(), 
                        txStatus, transactionId, result.status(), result.message());
                } catch (SQLException e) {
                    System.err.println("Failed to update transaction status: " + e.getMessage());
                }
            }
        }
        return confirmed;
    }

    /**
     * Demo affordance: lets a showcase decide which answer the simulated gateway
     * reports, so both the paid and abandoned branches can be demonstrated.
     * Refuses to act the moment simulate mode is off.
     * <p>
     * The cache is dropped on the way out. Without it the next
     * {@link #confirm} would read the pre-click value out of
     * {@link PaymentConfig}'s 30s cache and report the old outcome, so pressing
     * "Customer cancelled" and then "Check payment status" would still say paid.
     */
    public void setSimulatedResult(int orderId, String wireStatus) {
        if (!PaymentConfig.isSimulate()) {
            throw new ValidationException("Simulated results are only available in simulate mode.");
        }
        new AppSettingsRepository().save("payment.aba.simulated_result_" + orderId, wireStatus);
        PaymentConfig.invalidate();
    }

    public List<Payment> findByOrder(int orderId) {
        return paymentRepository.findByOrder(orderId);
    }

    public Payment latestFor(int orderId) {
        return paymentRepository.findLatestForOrder(orderId);
    }

    // ------------------------------------------------------------- internals

    /**
     * Marks an order paid and moves it PENDING -> PROCESSING in one transaction,
     * guarded so a double callback cannot produce a second transition.
     *
     * @param note what to record in the status event, e.g.
     *             {@code "card Visa ending 4242"} or an ABA transaction id. The
     *             text is caller-supplied because the two providers describe a
     *             payment differently, and a card note must never be able to
     *             contain anything but a brand and four digits.
     */
    private boolean markPaid(int orderId, String transactionId, String note) {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!orderRepository.markPaid(c, orderId, transactionId)) {
                    c.rollback();
                    return false;
                }
                OrderStatusEvent paidEvent = new OrderStatusEvent();
                paidEvent.setOrderId(orderId);
                paidEvent.setFromStatus(Order.Status.PENDING);
                paidEvent.setToStatus(Order.Status.PROCESSING);
                paidEvent.setChangedBy("SYSTEM");
                paidEvent.setNote("Payment confirmed via " + note);
                orderRepository.insertStatusEvent(c, paidEvent);
                paymentRepository.markAttemptPaid(c, orderId, transactionId, new Timestamp(System.currentTimeMillis()));
                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not record the payment", e);
        }
    }

    private void markOrder(int orderId, String paymentStatus, String transactionId, String provider) {
        orderRepository.updatePayment(orderId, paymentStatus, transactionId, provider);
    }

    private Payment recordAttempt(int orderId, String transactionId, Payment.Status status,
                                 String message, String qrImage, String abaPhone) {
        return paymentRepository.insert(new Payment(orderId, PROVIDER_ABA, transactionId,
                currentAmount(orderId), PaymentConfig.currency(), status, message, qrImage, abaPhone));
    }

    private void updateAttempt(int orderId, String transactionId, Payment.Status status, String message) {
        paymentRepository.updateStatus(orderId, transactionId, status, message);
    }

    private BigDecimal currentAmount(int orderId) {
        Order order = orderRepository.findById(orderId);
        return order == null || order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
    }

    private void audit(String action, int orderId, String details) {
        AuditLogger.logAdminAction(action, "SYSTEM", "order #" + orderId, details);
    }
}
