package com.hengtongan.computerstore.infrastructure.persistence;

import com.hengtongan.computerstore.core.domain.entity.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Utility class for batch database operations to improve performance
 * for bulk admin operations and data processing.
 */
public final class BatchOperationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationUtil.class);

    private BatchOperationUtil() {
    }

    /**
     * Batch update order statuses for multiple orders.
     * Significantly faster than individual updates for bulk operations.
     *
     * @param connection Database connection
     * @param orderIds List of order IDs to update
     * @param status New status to set
     * @return Number of rows updated
     * @throws SQLException if database operation fails
     */
    public static int batchUpdateOrderStatus(Connection connection, List<Integer> orderIds, Order.Status status)
            throws SQLException {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Integer orderId : orderIds) {
                ps.setString(1, status.name());
                ps.setInt(2, orderId);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            return results.length;
        }
    }

    /**
     * Batch delete cart items for a user.
     * Faster than individual deletes for cart clearing operations.
     *
     * @param connection Database connection
     * @param userId User ID whose cart items should be deleted
     * @param cartItemIds List of cart item IDs to delete
     * @return Number of rows deleted
     * @throws SQLException if database operation fails
     */
    public static int batchDeleteCartItems(Connection connection, int userId, List<Integer> cartItemIds)
            throws SQLException {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return 0;
        }

        String sql = "DELETE FROM cart_items WHERE cart_item_id = ? AND user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Integer cartItemId : cartItemIds) {
                ps.setInt(1, cartItemId);
                ps.setInt(2, userId);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            return results.length;
        }
    }

    /**
     * Batch update stock quantities for multiple products.
     * Useful for bulk inventory updates from admin operations.
     *
     * @param connection Database connection
     * @param stockUpdates Map of product ID to new stock quantity
     * @return Number of rows updated
     * @throws SQLException if database operation fails
     */
    public static int batchUpdateStock(Connection connection, java.util.Map<Integer, Integer> stockUpdates)
            throws SQLException {
        if (stockUpdates == null || stockUpdates.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE products SET stock_quantity = ? WHERE product_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (java.util.Map.Entry<Integer, Integer> entry : stockUpdates.entrySet()) {
                ps.setInt(1, entry.getValue());
                ps.setInt(2, entry.getKey());
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            return results.length;
        }
    }

    /**
     * Batch insert product specifications for multiple products.
     * Faster for bulk product creation operations.
     *
     * @param connection Database connection
     * @param specifications List of specification data to insert
     * @return Number of rows inserted
     * @throws SQLException if database operation fails
     */
    public static int batchInsertProductSpecs(Connection connection, List<Object[]> specifications)
            throws SQLException {
        if (specifications == null || specifications.isEmpty()) {
            return 0;
        }

        String sql = "INSERT INTO product_specs (product_id, spec_key, spec_value) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (Object[] spec : specifications) {
                ps.setInt(1, (Integer) spec[0]);
                ps.setString(2, (String) spec[1]);
                ps.setString(3, (String) spec[2]);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            return results.length;
        }
    }

    /**
     * Executes a batch operation with proper error handling and logging.
     *
     * @param connection Database connection
     * @param operationName Name of the operation for logging
     * @param batchOperation The batch operation to execute
     * @return Number of affected rows
     */
    public static int executeBatchWithLogging(Connection connection, String operationName,
                                             BatchOperation batchOperation) {
        try {
            long startTime = System.currentTimeMillis();
            int result = batchOperation.execute(connection);
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Batch operation '{}' completed in {}ms, affected {} rows",
                    operationName, duration, result);
            return result;
        } catch (SQLException e) {
            LOGGER.error("Batch operation '{}' failed", operationName, e);
            throw new RuntimeException("Batch operation failed: " + operationName, e);
        }
    }

    @FunctionalInterface
    public interface BatchOperation {
        int execute(Connection connection) throws SQLException;
    }
}