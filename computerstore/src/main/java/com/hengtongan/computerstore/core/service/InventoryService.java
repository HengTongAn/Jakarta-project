package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.repository.InventoryRepository;
import com.hengtongan.computerstore.core.repository.ProductRepository;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.InventoryLog;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.infrastructure.cache.CacheManager;
import com.hengtongan.computerstore.infrastructure.realtime.EventHub;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class InventoryService {

    private final InventoryRepository inventoryDAO = new InventoryRepository();
    private final ProductRepository productDAO = new ProductRepository();

    // Manual-adjustment reason codes. Every manual stock change must carry
    // one of these. They are stored on the audit log action as
    // MANUAL_ADJUST_<REASON> so the trail stays readable without adding a
    // schema column (action is VARCHAR(50)).
    public static final String REASON_RECEIVED = "RECEIVED";
    public static final String REASON_DAMAGED = "DAMAGED";
    public static final String REASON_COUNT = "COUNT";
    public static final String REASON_RETURNED = "RETURNED";
    public static final String REASON_CORRECTION = "CORRECTION";

    private static final Set<String> ADJUSTMENT_REASONS = Set.of(
            REASON_RECEIVED, REASON_DAMAGED, REASON_COUNT, REASON_RETURNED, REASON_CORRECTION);

    /**
     * Suggested restock level: low-stock alerts at the threshold, admins are
     * told to order back up to {@code LOW_STOCK_THRESHOLD * 4} units so a
     * delivery covers the next few sales cycles instead of barely clearing
     * the alert again.
     */
    public static final int REORDER_TARGET = Product.LOW_STOCK_THRESHOLD * 4;

    public static boolean isValidReason(String reason) {
        return reason != null && ADJUSTMENT_REASONS.contains(reason);
    }

    public List<Product> getLowStock() {
        return inventoryDAO.listLowStock(Product.LOW_STOCK_THRESHOLD);
    }

    public List<Product> getOutOfStock() {
        return inventoryDAO.listOutOfStock();
    }

    public List<InventoryLog> getRecentLogs(int limit) {
        return inventoryDAO.listRecent(limit);
    }

    /**
     * Manually adjusts the stock of a product by a signed delta, records the
     * change and its reason and recomputes the product status. The stock
     * write and the audit trail happen in one transaction so a failed log
     * write can never leave the stock changed without a trace.
     */
    public void adjustStock(int productId, int delta, String reason, int userId) {
        if (!isValidReason(reason)) {
            throw new ValidationException("Unknown adjustment reason.");
        }
        Product product = inventoryDAO.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product does not exist.");
        }
        if (product.isDeleted()) {
            throw new ValidationException("Cannot adjust stock for a deleted product.");
        }

        Connection conn = null;
        int newQuantity;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // The delta is applied by the UPDATE, not written as an absolute
            // value computed from the read above: that read came from a separate
            // connection, so two concurrent adjustments both saw the same starting
            // quantity and the second write silently discarded the first. The
            // audit log recorded a stale old_quantity that matched neither the
            // row before nor the row after.
            newQuantity = productDAO.applyStockDelta(conn, productId, delta);
            if (newQuantity < 0) {
                rollbackQuietly(conn);
                throw new ValidationException("Stock cannot go below zero.");
            }
            int oldQuantity = newQuantity - delta;

            InventoryLog log = new InventoryLog();
            log.setProductId(productId);
            log.setOldQuantity(oldQuantity);
            log.setNewQuantity(newQuantity);
            log.setAction("MANUAL_ADJUST_" + reason);
            log.setUserId(userId);
            inventoryDAO.addLog(conn, log);
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Inventory update failed.", e);
        } finally {
            closeQuietly(conn);
        }

        // Live update after the change is committed (best-effort). The status is
        // recomputed from the committed quantity rather than reused from the
        // stale read; DISCONTINUED is preserved by the UPDATE's CASE.
        Product.Status nextStatus = product.getStatus() == Product.Status.DISCONTINUED
                ? Product.Status.DISCONTINUED
                : Product.computeStatus(newQuantity);

        ProductRepository.invalidateProductCache(productId);
        CacheManager.invalidateAllCatalog();
        CacheManager.invalidateProductList();
        CacheManager.invalidateProductDetail(productId);
        EventHub.publishStock(productId, newQuantity, nextStatus.name());
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // nothing sensible to do
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // nothing sensible to do
            }
        }
    }
}