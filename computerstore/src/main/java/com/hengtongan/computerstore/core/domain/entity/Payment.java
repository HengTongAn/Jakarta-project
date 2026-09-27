package com.hengtongan.computerstore.core.domain.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * One attempt to pay for an order through a gateway.
 * <p>
 * An order can have several of these: a customer whose first attempt timed out
 * tries again, and the newest attempt is the one that matters. That is why
 * {@code transactionId} is unique across the table rather than unique per order.
 */
public class Payment {

    public enum Status {
        /** Handed to the gateway, no answer yet. */
        PENDING,
        /** Gateway confirmed the money arrived. Terminal. */
        PAID,
        /** Customer backed out at the gateway. */
        CANCELLED,
        /** Gateway rejected it. */
        FAILED,
        /** Gateway window elapsed before payment. */
        EXPIRED;

        public static Status fromWire(String raw) {
            if (raw == null || raw.isBlank()) {
                return PENDING;
            }
            String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
            return switch (value) {
                case "1", "SUCCESS", "PAID", "APPROVED" -> PAID;
                case "2", "CANCELLED", "CANCELED", "USER_CANCELLED" -> CANCELLED;
                case "3", "FAILED", "FAIL" -> FAILED;
                case "4", "EXPIRED", "EXPIRE" -> EXPIRED;
                default -> PENDING;
            };
        }
    }

    private int paymentId;
    private int orderId;
    private String provider;
    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private Status status;
    private String message;
    private String qrImage;
    private String abaPhone;
    private String cardBrand;
    private String cardLast4;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** Creates a not-yet-persisted attempt. */
    public Payment(int orderId, String provider, String transactionId, BigDecimal amount,
                   String currency, Status status, String message, String qrImage, String abaPhone) {
        this(orderId, provider, transactionId, amount, currency, status, message, qrImage, abaPhone, null, null);
    }

    /**
     * Creates a not-yet-persisted attempt, optionally carrying a masked card.
     * <p>
     * There is no parameter for a card number and no field to hold one, so the
     * narrowest safe shape is enforced by the type rather than by discipline.
     */
    public Payment(int orderId, String provider, String transactionId, BigDecimal amount,
                   String currency, Status status, String message, String qrImage, String abaPhone,
                   String cardBrand, String cardLast4) {
        this.orderId = orderId;
        this.provider = provider;
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.message = message;
        this.qrImage = qrImage;
        this.abaPhone = abaPhone;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
    }

    public Payment() {
    }

    public int getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(int paymentId) {
        this.paymentId = paymentId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getQrImage() {
        return qrImage;
    }

    public void setQrImage(String qrImage) {
        this.qrImage = qrImage;
    }

    public String getAbaPhone() {
        return abaPhone;
    }

    public void setAbaPhone(String abaPhone) {
        this.abaPhone = abaPhone;
    }

    /** Card scheme, e.g. "Visa". Null for non-card providers. */
    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    /**
     * Last four digits of the card, or null. Four digits is the most this class
     * can represent, and the database enforces the same limit with a CHECK
     * constraint.
     */
    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
