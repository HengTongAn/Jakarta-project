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
    /**
     * Decimal places every money figure is reported at.
     *
     * <p>Matches the {@code transactions.amount} column, which is {@code decimal(10,2)}.
     * Pinning it in one place keeps an empty ledger and a zero-sum ledger rendering
     * identically -- see {@link #zeroIfNull(java.math.BigDecimal)}.</p>
     */
    private static final int MONEY_SCALE = 2;

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

    /**
     * Gets a transaction by ID, but only when it belongs to this user.
     *
     * <p>The customer-facing pages use this and never {@link #findById(long)}:
     * an id in the URL is chosen by whoever is browsing, so a lookup that is
     * keyed on the id alone shows one customer another's card payment.</p>
     *
     * @param transactionId The transaction to fetch
     * @param userId The signed-in customer
     * @return The transaction, or null when it is not this customer's
     */
    public Transaction findByIdForUser(long transactionId, int userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + "WHERE t.transaction_id = ? AND t.user_id = ?";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, transactionId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    /**
     * Gets all transactions for an order, but only when the order belongs to
     * this user. Same reasoning as {@link #findByIdForUser(long, int)}.
     */
    public List<Transaction> findByOrderIdForUser(int orderId, int userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + "WHERE t.order_id = ? AND t.user_id = ? ORDER BY t.created_at DESC";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>();
                while (rs.next()) {
                    transactions.add(mapRow(rs));
                }
                return transactions;
            }
        }
    }

    /**
     * The payment that actually settled an order, for this customer only.
     * <p>
     * Narrower than {@link #findByOrderIdForUser(int, int)} on purpose. An order accumulates a
     * row per <em>attempt</em>, so the list it returns is the wrong input for a receipt: it is
     * ordered newest first, which means a retried card leaves the declined attempt at the top and
     * a receipt built from it would report money that never arrived. This asks the database for
     * the one row a receipt is allowed to be built from, and returns nothing rather than
     * something approximate when there is not one.
     * <p>
     * The {@code user_id} constraint is the same one the customer-facing getters carry, and for
     * the same reason: the order number is a guessable integer, and without it this would be a
     * way to read another customer's payment record.
     *
     * @param orderId the order whose settled payment is wanted
     * @param userId the signed-in customer
     * @return the completed payment, or null when the order is unpaid, only failed, or not this
     *         customer's
     */
    public Transaction findSettledPaymentForUser(int orderId, int userId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + "WHERE t.order_id = ? AND t.user_id = ? "
                + "AND t.transaction_type = 'PAYMENT' AND t.status = 'COMPLETED' "
                + "ORDER BY t.created_at DESC LIMIT 1";
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * Whether an order already has money back against it.
     *
     * <p>The guard against refunding the same payment twice. It takes the caller's
     * {@link Connection} rather than opening its own, because a check that cannot
     * see the insert it is guarding is not a check. Two admins opening this order
     * in two tabs and clicking Refund at the same moment each commit their own
     * insert, and the ledger ends up carrying two refunds against one payment with
     * nothing in the schema to stop it. This is the same reason the order-side
     * status change claims its transition inside the caller's transaction rather
     * than re-reading first.
     *
     * <p>Counts {@code PARTIAL_REFUND} too even though only whole refunds are
     * issued today, so that the guard is already correct if partial refunds are
     * ever added rather than becoming a second bug at that point.
     *
     * @param c the connection the refund insert will use, so both see one snapshot
     * @param orderId the order being refunded
     * @return true when a settled refund already exists for the order
     */
    public boolean hasSettledRefund(Connection c, int orderId) throws SQLException {
        String sql = "SELECT 1 FROM transactions "
                + "WHERE order_id = ? AND transaction_type IN ('REFUND', 'PARTIAL_REFUND') "
                + "AND status = 'COMPLETED' LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
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
        return loadStats(null);
    }

    /**
     * Gets transaction statistics for one customer.
     *
     * <p>Same numbers as {@link #getStats()}, restricted to one user's rows. The
     * customer dashboard has no argument to narrow by otherwise, so this
     * variant carries the user id itself.</p>
     */
    public TransactionStats getStatsForUser(int userId) throws SQLException {
        return loadStats(userId);
    }

    /**
     * The one query behind both stats calls, optionally narrowed to one customer.
     *
     * <p>Null {@code userId} means every row. The two callers used to carry
     * byte-identical SQL, so the money columns could be fixed in one place and
     * forgotten in the other -- which is exactly what happened when the views grew
     * refund-aware totals this class did not have.</p>
     *
     * <h2>Why refunds need their own columns</h2>
     * A refund row is {@code COMPLETED} with a {@code REFUND} type, because it is
     * money that genuinely moved and nothing will call back to settle it later. So
     * the old {@code status = 'COMPLETED'} sum swept refunds in with payments and
     * called the result "completed amount". A store with one $100 order and a $100
     * refund would report $200 of completed payments, which is the opposite of what
     * happened.
     *
     * <p>Hence three figures rather than one: what came in, what went back out, and
     * the difference. {@code transaction_type} is the discriminator, not
     * {@code status}, and direction is read as "is this a PAYMENT" rather than "is
     * this one of the refund types" -- so a new non-payment type is counted as
     * money out instead of being silently mistaken for revenue.</p>
     *
     * <h2>Known boundary: chargebacks are in none of the three</h2>
     * The {@code transaction_type} enum also carries {@code CHARGEBACK}, and the
     * admin filter and badges render it, but nothing in the application writes it --
     * there is no provider call that produces one. A chargeback row would carry
     * {@code status = 'CHARGEBACK'}, not {@code COMPLETED}, so it fails the settled
     * gate in all three sums and would appear only in {@code totalTransactions}.
     *
     * <p>That is recorded here rather than fixed because guessing is worse: treating
     * chargeback as money out requires deciding what a chargeback means for the
     * revenue figure, and {@code AbaPaywayClient} has no refund or chargeback call
     * to define it from. If chargeback support is added, this query must be revisited
     * in the same change -- a chargeback understating nothing here would, once such
     * rows exist, leave net revenue overstated by the amount taken back.</p>
     */
    private TransactionStats loadStats(Integer userId) throws SQLException {
        String sql = "SELECT "
                + "COUNT(*) as total, "
                + "SUM(CASE WHEN status = 'COMPLETED' THEN amount ELSE 0 END) as total_completed, "
                + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_count, "
                + "SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending_count, "
                // Money in: settled rows of type PAYMENT. Written as an equality on
                // PAYMENT rather than as a list of the types that are NOT payments, so
                // that a type added later defaults to the cautious side. Listing them
                // meant a CHARGEBACK row would satisfy "not a refund" and be counted as
                // revenue; charging a customer back is the opposite of taking money.
                + "SUM(CASE WHEN status = 'COMPLETED' AND transaction_type = 'PAYMENT' "
                + "         THEN amount ELSE 0 END) as gross_payments, "
                // Money out: every other settled type, at the amount its row actually
                // carries. A partial refund returns less than it took, so it cannot be
                // assumed to equal the original and has to be read off the row.
                + "SUM(CASE WHEN status = 'COMPLETED' AND transaction_type <> 'PAYMENT' "
                + "         THEN amount ELSE 0 END) as gross_refunds "
                + "FROM transactions"
                + (userId == null ? "" : " WHERE user_id = ?");
        try (Connection c = conn();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                TransactionStats stats = new TransactionStats();
                if (rs.next()) {
                    stats.setTotalTransactions(rs.getInt("total"));
                    stats.setTotalCompletedAmount(rs.getBigDecimal("total_completed"));
                    stats.setFailedCount(rs.getInt("failed_count"));
                    stats.setPendingCount(rs.getInt("pending_count"));
                    stats.setGrossPayments(zeroIfNull(rs.getBigDecimal("gross_payments")));
                    stats.setGrossRefunds(zeroIfNull(rs.getBigDecimal("gross_refunds")));
                }
                stats.setNetAmount(stats.getGrossPayments().subtract(stats.getGrossRefunds()));
                return stats;
            }
        }
    }

    /**
     * Turns the aggregate's {@code NULL} into a currency zero, and pins the scale.
     *
     * <p>Two separate defects live here, and only the first is the obvious one.</p>
     *
     * <p>{@code SUM} over no matching rows is SQL {@code NULL}, which EL renders as
     * an empty string -- so a brand new store would show a blank figure exactly
     * where it should show a zero. Verified against the development database: the
     * exact aggregate over an empty table returns {@code NULL}, and the same
     * expression in {@code COALESCE(...,0)} returns {@code 0.00}. Only the
     * empty-table case yields {@code NULL}; rows that match nothing hit the
     * {@code CASE}'s {@code ELSE 0} and total to a real zero.</p>
     *
     * <p>The scale is the one that is easy to miss. {@code BigDecimal.ZERO} has
     * scale 0 while a {@code decimal(10,2)} column yields scale 2, and
     * {@code DecimalFormat} renders the former as {@code 0} and the latter as
     * {@code 0.00}. Confirmed by rendering the real page: a store whose ledger is
     * empty showed {@code $0} next to one with a genuine zero-amount row showing
     * {@code $0.00}. Substituting the scale-0 constant would have made a currency
     * figure change format depending on whether the store had any rows at all, so
     * both cases now render alike.</p>
     */
    private static java.math.BigDecimal zeroIfNull(java.math.BigDecimal value) {
        java.math.BigDecimal safe = value == null ? java.math.BigDecimal.ZERO : value;
        // MONEY_SCALE matches the transactions.amount column. Rounding is safe here
        // because a stored amount is already at this scale; the branch only ever
        // adds trailing zeros.
        return safe.setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Simple stats holder class.
     *
     * <p>The gross/net trio exists because a refund row is {@code COMPLETED} like a
     * payment is. Any single "completed amount" therefore cannot distinguish a sale
     * from money going back out, and reporting their sum as revenue would overstate
     * it by however much has been refunded.</p>
     */
    public static class TransactionStats {
        private int totalTransactions;
        private java.math.BigDecimal totalCompletedAmount;
        private int failedCount;
        private int pendingCount;
        private java.math.BigDecimal grossPayments = java.math.BigDecimal.ZERO;
        private java.math.BigDecimal grossRefunds = java.math.BigDecimal.ZERO;
        private java.math.BigDecimal netAmount = java.math.BigDecimal.ZERO;

        public int getTotalTransactions() { return totalTransactions; }
        public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }
        public java.math.BigDecimal getTotalCompletedAmount() { return totalCompletedAmount; }
        public void setTotalCompletedAmount(java.math.BigDecimal totalCompletedAmount) { this.totalCompletedAmount = totalCompletedAmount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

        /** Money in: settled payments, excluding refunds. */
        public java.math.BigDecimal getGrossPayments() { return grossPayments; }
        public void setGrossPayments(java.math.BigDecimal grossPayments) { this.grossPayments = grossPayments; }

        /** Money out: settled refunds and partial refunds, at the amount each returned. */
        public java.math.BigDecimal getGrossRefunds() { return grossRefunds; }
        public void setGrossRefunds(java.math.BigDecimal grossRefunds) { this.grossRefunds = grossRefunds; }

        /** What the store actually kept: payments less refunds. */
        public java.math.BigDecimal getNetAmount() { return netAmount; }
        public void setNetAmount(java.math.BigDecimal netAmount) { this.netAmount = netAmount; }
    }
}