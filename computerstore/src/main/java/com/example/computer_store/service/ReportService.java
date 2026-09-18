package com.example.computer_store.service;

import com.example.computer_store.dao.InventoryDAO;
import com.example.computer_store.dao.OrderDAO;
import com.example.computer_store.dao.ProductDAO;
import com.example.computer_store.model.Order;
import com.example.computer_store.model.Product;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic reporting data for management.
 */
public class ReportService {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    public Map<String, Object> getSalesSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", orderDAO.countAll());
        summary.put("totalRevenue", orderDAO.totalRevenue());
        summary.put("itemsSold", orderDAO.totalItemsSold());
        summary.put("pending", orderDAO.countByStatus(Order.Status.PENDING));
        summary.put("processing", orderDAO.countByStatus(Order.Status.PROCESSING));
        summary.put("completed", orderDAO.countByStatus(Order.Status.COMPLETED));
        summary.put("cancelled", orderDAO.countByStatus(Order.Status.CANCELLED));
        summary.put("productCount", productDAO.countAll());
        return summary;
    }

    public List<Product> getLowStock() {
        return inventoryDAO.listLowStock(Product.LOW_STOCK_THRESHOLD);
    }

    public List<Product> getOutOfStock() {
        return inventoryDAO.listOutOfStock();
    }

    public Map<String, BigDecimal> getLowStockValues() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("totalStockValue", productDAO.totalStockValue());
        values.put("lowStockValue", productDAO.lowStockValue(Product.LOW_STOCK_THRESHOLD));
        return values;
    }
}