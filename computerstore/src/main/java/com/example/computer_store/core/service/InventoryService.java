package com.example.computer_store.core.service;

import com.example.computer_store.core.repository.InventoryRepository;
import com.example.computer_store.core.repository.ProductRepository;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.InventoryLog;
import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.infrastructure.realtime.EventHub;
import com.example.computer_store.infrastructure.persistence.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class InventoryService {

    private final InventoryRepository inventoryDAO = new InventoryRepository();
    private final ProductRepository productDAO = new ProductRepository();

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
     * Manually adjusts the stock of a product, records the change and
     * recomputes the product status. The stock write and the audit trail
     * happen in one transaction so a failed log write can never leave the
     * stock changed without a trace.
     */
    public void adjustStock(int productId, int newQuantity, int userId) {
        if (newQuantity < 0) {
            throw new ValidationException("Stock quantity cannot be negative.");
        }
        Product product = inventoryDAO.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product does not exist.");
        }
        if (product.isDeleted()) {
            throw new ValidationException("Cannot adjust stock for a deleted product.");
        }
        // Keep DISCONTINUED products discontinued; only update quantity.
        Product.Status nextStatus = product.getStatus() == Product.Status.DISCONTINUED
                ? Product.Status.DISCONTINUED
                : Product.computeStatus(newQuantity);

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);
            productDAO.setStock(conn, productId, newQuantity, nextStatus);

            InventoryLog log = new InventoryLog();
            log.setProductId(productId);
            log.setOldQuantity(product.getStockQuantity());
            log.setNewQuantity(newQuantity);
            log.setAction("MANUAL_ADJUST");
            log.setUserId(userId);
            inventoryDAO.addLog(conn, log);
            conn.commit();
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Inventory update failed.", e);
        } finally {
            closeQuietly(conn);
        }

        // Live update after the change is committed (best-effort).
        ProductRepository.invalidateProductCache(productId);
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