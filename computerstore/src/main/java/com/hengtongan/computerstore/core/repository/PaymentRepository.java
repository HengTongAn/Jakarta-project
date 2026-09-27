package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.Payment;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for payment attempts.
 * <p>
 * An order may have several attempts; {@link #findLatestForOrder(int)} is the one
 * that reflects reality, and the rest are history.
 */
public class PaymentRepository {

    private static final String COLUMNS =
            "payment_id, order_id, provider, transaction_id, amount, currency, "
            + "status, message, qr_image, aba_phone, card_brand, card_last4, "
            + "created_at, updated_at";

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getInt("payment_id"));
        p.setOrderId(rs.getInt("order_id"));
        p.setProvider(rs.getString("provider"));
        p.setTransactionId(rs.getString("transaction_id"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setCurrency(rs.getString("currency"));
        p.setStatus(Payment.Status.valueOf(rs.getString("status")));
        p.setMessage(rs.getString("message"));
        p.setQrImage(rs.getString("qr_image"));
        p.setAbaPhone(rs.getString("aba_phone"));
        p.setCardBrand(rs.getString("card_brand"));
        p.setCardLast4(rs.getString("card_last4"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        return p;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    public Payment insert(Payment payment) {
        String sql = "INSERT INTO payments (order_id, provider, transaction_id, amount, currency, "
                + "status, message, qr_image, aba_phone, card_brand, card_last4) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, payment.getOrderId());
            ps.setString(2, payment.getProvider());
            ps.setString(3, payment.getTransactionId());
            ps.setBigDecimal(4, payment.getAmount());
            ps.setString(5, payment.getCurrency());
            ps.setString(6, payment.getStatus().name());
            ps.setString(7, payment.getMessage());
            ps.setString(8, payment.getQrImage());
            ps.setString(9, payment.getAbaPhone());
            ps.setString(10, payment.getCardBrand());
            ps.setString(11, payment.getCardLast4());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    payment.setPaymentId(keys.getInt(1));
                }
            }
            return payment;
        } catch (SQLException e) {
            throw new RuntimeException("Error recording payment attempt", e);
        }
    }

    public List<Payment> findByOrder(int orderId) {
        String sql = "SELECT " + COLUMNS + " FROM payments WHERE order_id = ? ORDER BY payment_id DESC";
        List<Payment> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Error listing payments for order " + orderId, e);
        }
    }

    /** Newest attempt for an order, or {@code null} if it was never started. */
    public Payment findLatestForOrder(int orderId) {
        String sql = "SELECT " + COLUMNS + " FROM payments WHERE order_id = ? ORDER BY payment_id DESC LIMIT 1";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading the latest payment for order " + orderId, e);
        }
    }

    public void updateStatus(int orderId, String transactionId, Payment.Status status, String message) {
        String sql = "UPDATE payments SET status = ?, message = ? "
                + "WHERE order_id = ? AND transaction_id = ? AND status <> 'PAID'";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, message);
            ps.setInt(3, orderId);
            ps.setString(4, transactionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating payment status for order " + orderId, e);
        }
    }

    /**
     * Marks the matching attempt paid inside the caller's transaction.
     * <p>
     * The {@code status <> 'PAID'} guard is what makes a replayed gateway
     * callback idempotent: the second one matches no rows and changes nothing.
     */
    public void markAttemptPaid(Connection c, int orderId, String transactionId, Timestamp paidAt)
            throws SQLException {
        String sql = "UPDATE payments SET status = 'PAID', message = ?, updated_at = ? "
                + "WHERE order_id = ? AND transaction_id = ? AND status <> 'PAID'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "Confirmed " + paidAt);
            ps.setTimestamp(2, paidAt);
            ps.setInt(3, orderId);
            ps.setString(4, transactionId);
            ps.executeUpdate();
        }
    }

    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM payments WHERE status = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting payments by status", e);
        }
    }
}
