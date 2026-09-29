package com.hengtongan.computerstore.core.repository;

import com.hengtongan.computerstore.core.domain.entity.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TransactionRepository {

    private static final String COLUMNS =
            "t.transaction_id, t.order_id, t.transaction_type, t.status, "
            + "t.amount, t.currency, t.payment_method, t.gateway_transaction_id, "
            + "t.gateway_response_code, t.gateway_response_message, t.user_id, "
            + "t.created_at, t.updated_at, "
            + "u.full_name AS customer_name, u.username AS customer_username, "
            + "o.total_amount AS order_total";

    private static final String FROM_JOINS =
            "FROM transactions t "
            + "JOIN users u ON u.user_id = t.user_id "
            + "JOIN orders o ON o.order_id = t.order_id ";

    private Transaction mapRow(ResultSet rs) throws SQLException {
        Transaction t = new Transaction();
        t.setTransactionId(rs.getLong("transaction_id"));
        t.setOrderId(rs.getInt("order_id"));
        t.setTransactionType(Transaction.TransactionType.valueOf(rs.getString("transaction_type")));
        t.setStatus(Transaction.TransactionStatus.valueOf(rs.getString("status")));
        t.setAmount(rs.getBigDecimal("amount"));
        t.setCurrency(rs.getString("currency"));
        t.setPaymentMethod(rs.getString("payment_method"));
        t.setGatewayTransactionId(rs.getString("gateway_transaction_id"));
        t.setGatewayResponseCode(rs.getString("gateway_response_code"));
        t.setGatewayResponseMessage(rs.getString("gateway_response_message"));
        t.setUserId(rs.getInt("user_id"));
        t.setCreatedAt(rs.getTimestamp("created_at"));
        t.setUpdatedAt(rs.getTimestamp("updated_at"));
        t.setCustomerName(rs.getString("customer_name"));
        t.setCustomerUsername(rs.getString("customer_username"));
        t.setOrderTotal(rs.getBigDecimal("order_total"));
        return t;
    }

    private Connection conn() throws SQLException {
        return com.hengtongan.computerstore.infrastructure.persistence.DBConnection.getConnection();
    }

    /** Creates a transaction inside the given transaction and returns the new id. */
    public long create(Connection c, Transaction transaction) throws SQLException {
        String sql = "INSERT INTO transactions (order_id, transaction_type, status, amount, currency, "
                + "payment_method, gateway_transaction_id, gateway_response_code, gateway_response_message, user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, transaction.getOrderId());
            ps.setString(2, transaction.getTransactionType().name());
            ps.setString(3, transaction.getStatus().name());
            ps.setBigDecimal(4, transaction.getAmount());
            ps.setString(5, transaction.getCurrency());
            ps.setString(6, transaction.getPaymentMethod());
            ps.setString(7, transaction.getGatewayTransactionId());
            ps.setString(8, transaction.getGatewayResponseCode());
            ps.setString(9, transaction.getGatewayResponseMessage());
            ps.setInt(10, transaction.getUserId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("No generated key returned for transaction");
        }
    }

    /** Updates transaction status and gateway response. */
    public void updateStatus(Connection c, long transactionId, Transaction.TransactionStatus status,
                            String gatewayTransactionId, String responseCode, String responseMessage) throws SQLException {
        String sql = "UPDATE transactions SET status = ?, gateway_transaction_id = ?, "
                + "gateway_response_code = ?, gateway_response_message = ?, updated_at = NOW() "
                + "WHERE transaction_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, gatewayTransactionId);
            ps.setString(3, responseCode);
            ps.setString(4, responseMessage);
            ps.setLong(5, transactionId);
            ps.executeUpdate();
        }
    }

    /** Gets a transaction by ID. */
    public Transaction findById(long transactionId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + "WHERE t.transaction_id = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    /** Gets all transactions for a specific order. */
    public List<Transaction> findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + "WHERE t.order_id = ? ORDER BY t.created_at DESC";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>();
                while (rs.next()) {
                    transactions.add(mapRow(rs));
                }
                return transactions;
            }
        }
    }

    /** Gets all transactions for a specific user. */
    public List<Transaction> findByUserId(int userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + "WHERE t.user_id = ? ORDER BY t.created_at DESC";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>();
                while (rs.next()) {
                    transactions.add(mapRow(rs));
                }
                return transactions;
            }
        }
    }

    /** Gets all transactions with optional filtering. */
    public List<Transaction> findAll(Transaction.TransactionStatus statusFilter, String paymentMethodFilter,
                                     int limit, int offset) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " " + FROM_JOINS + "WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (statusFilter != null) {
            sql.append(" AND t.status = ?");
            params.add(statusFilter.name());
        }

        if (paymentMethodFilter != null && !paymentMethodFilter.isEmpty()) {
            sql.append(" AND t.payment_method = ?");
            params.add(paymentMethodFilter);
        }

        sql.append(" ORDER BY t.created_at DESC");

        if (limit > 0) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }

        if (offset > 0) {
            sql.append(" OFFSET ?");
            params.add(offset);
        }

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>();
                while (rs.next()) {
                    transactions.add(mapRow(rs));
                }
                return transactions;
            }
        }
    }

    /** Counts total transactions with optional filtering. */
    public int count(Transaction.TransactionStatus statusFilter, String paymentMethodFilter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM transactions t WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (statusFilter != null) {
            sql.append(" AND t.status = ?");
            params.add(statusFilter.name());
        }

        if (paymentMethodFilter != null && !paymentMethodFilter.isEmpty()) {
            sql.append(" AND t.payment_method = ?");
            params.add(paymentMethodFilter);
        }

        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    /** Gets transactions by gateway transaction ID (for reconciliation). */
    public Transaction findByGatewayTransactionId(String gatewayTransactionId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + "WHERE t.gateway_transaction_id = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, gatewayTransactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    /** Gets transaction statistics for admin dashboard. */
    public TransactionStats getStats() throws SQLException {
        String sql = "SELECT "
                + "COUNT(*) as total, "
                + "SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END) as total_completed, "
                + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count, "
                + "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending_count "
                + "FROM transactions";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    TransactionStats stats = new TransactionStats();
                    stats.setTotalTransactions(rs.getInt("total"));
                    stats.setTotalCompletedAmount(rs.getBigDecimal("total_completed"));
                    stats.setFailedCount(rs.getInt("failed_count"));
                    stats.setPendingCount(rs.getInt("pending_count"));
                    return stats;
                }
                return new TransactionStats();
            }
        }
    }

    /** Simple stats holder class. */
    public static class TransactionStats {
        private int totalTransactions;
        private java.math.BigDecimal totalCompletedAmount;
        private int failedCount;
        private int pendingCount;

        public int getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }
        public java.math.BigDecimal getTotalCompletedAmount() { return totalCompletedAmount; }
        public void setTotalCompletedAmount(java.math.BigDecimal totalCompletedAmount) { this.totalCompletedAmount = totalCompletedAmount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
    }
}