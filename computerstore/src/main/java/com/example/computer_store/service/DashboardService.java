package com.example.computer_store.service;

import com.example.computer_store.dao.CartDAO;
import com.example.computer_store.dao.InventoryDAO;
import com.example.computer_store.dao.OrderDAO;
import com.example.computer_store.dao.ProductDAO;
import com.example.computer_store.dao.UserDAO;
import com.example.computer_store.model.Order;
import com.example.computer_store.model.Product;
import com.example.computer_store.model.User;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates the statistics shown on the admin dashboard.
 */
public class DashboardService {

    private final ProductDAO productDAO = new ProductDAO();
    private final UserDAO userDAO = new UserDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final CartDAO cartDAO = new CartDAO();

    public Map<String, Object> getAdminStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProducts", productDAO.countAll());
        stats.put("totalCustomers", userDAO.countByRole(User.Role.CUSTOMER));
        stats.put("totalOrders", orderDAO.countAll());
        stats.put("totalRevenue", orderDAO.totalRevenue());
        stats.put("lowStockCount", productDAO.countStockThreshold(false, Product.LOW_STOCK_THRESHOLD));
        stats.put("outOfStockCount", productDAO.countStockThreshold(true, Product.LOW_STOCK_THRESHOLD));
        stats.put("pendingOrders", orderDAO.countByStatus(Order.Status.PENDING));
        stats.put("recentOrders", orderDAO.recentOrders(6));
        stats.put("recentLogs", inventoryDAO.listRecent(6));
        return stats;
    }

    public int getCartCount(int userId) {
        return cartDAO.countItems(userId);
    }

    public List<Order> getRecentOrdersForUser(int userId, int limit) {
        return orderDAO.findByUserId(userId).stream().limit(limit).toList();
    }

    public Map<String, Object> getCustomerStats(int userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("recentOrders", getRecentOrdersForUser(userId, 5));
        stats.put("cartCount", getCartCount(userId));
        return stats;
    }

    public BigDecimal getTotalRevenue() {
        return orderDAO.totalRevenue();
    }
}