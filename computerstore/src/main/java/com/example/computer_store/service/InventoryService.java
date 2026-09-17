package com.example.computer_store.service;

import com.example.computer_store.dao.InventoryDAO;
import com.example.computer_store.dao.ProductDAO;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.InventoryLog;
import com.example.computer_store.model.Product;
import com.example.computer_store.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class InventoryService {

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

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
     * recomputes the product status.
     */
    public void adjustStock(int productId, int newQuantity, int userId) {
        if (newQuantity < 0) {
            throw new ValidationException("Stock quantity cannot be negative.");
        }
        Product product = inventoryDAO.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product does not exist.");
        }

        try (Connection conn = DBConnection.getConnection()) {
            productDAO.setStock(conn, productId, newQuantity,
                    Product.computeStatus(newQuantity));

            InventoryLog log = new InventoryLog();
            log.setProductId(productId);
            log.setOldQuantity(product.getStockQuantity());
            log.setNewQuantity(newQuantity);
            log.setAction("MANUAL_ADJUST");
            log.setUserId(userId);
            inventoryDAO.addLog(conn, log);
        } catch (SQLException e) {
            throw new RuntimeException("Inventory update failed.", e);
        }
    }
}