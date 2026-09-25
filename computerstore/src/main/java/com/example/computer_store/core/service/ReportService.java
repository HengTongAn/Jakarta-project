package com.example.computer_store.core.service;

import com.example.computer_store.core.repository.InventoryRepository;
import com.example.computer_store.core.repository.OrderRepository;
import com.example.computer_store.core.repository.ProductRepository;
import com.example.computer_store.core.domain.entity.Order;
import com.example.computer_store.core.domain.entity.Product;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Basic reporting data for management.
 */
public class ReportService {

    private final OrderRepository orderDAO = new OrderRepository();
    private final ProductRepository productDAO = new ProductRepository();
    private final InventoryRepository inventoryDAO = new InventoryRepository();

    public Map<String, Object> getSalesSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", orderDAO.countAll());
        summary.put("totalRevenue", orderDAO.totalRevenue());
        summary.put("itemsSold", orderDAO.totalItemsSold());
        summary.put("pending", orderDAO.countByStatus(Order.Status.PENDING));
        summary.put("processing", orderDAO.countByStatus(Order.Status.PROCESSING));
        summary.put("shipped", orderDAO.countByStatus(Order.Status.SHIPPED));
        summary.put("completed", orderDAO.countByStatus(Order.Status.COMPLETED));
        summary.put("cancelled", orderDAO.countByStatus(Order.Status.CANCELLED));
        summary.put("refunded", orderDAO.countByStatus(Order.Status.REFUNDED));
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