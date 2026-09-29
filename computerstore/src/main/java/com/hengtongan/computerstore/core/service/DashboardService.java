package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.infrastructure.cache.CacheManager;
import com.hengtongan.computerstore.core.repository.CartRepository;
import com.hengtongan.computerstore.core.repository.InventoryRepository;
import com.hengtongan.computerstore.core.repository.OrderRepository;
import com.hengtongan.computerstore.core.repository.ProductRepository;
import com.hengtongan.computerstore.core.domain.entity.Order;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.web.ErrorHandler;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates the statistics shown on the admin dashboard.
 *
 * <p>The summary is a single 8-row aggregate query and two short-list reads;
 * {@link #getAdminStats()} caches the assembled result in the dashboard cache
 * (see {@link CacheManager}) so the admin home responds without re-pulling the
 * same counts on every request. The cache is invalidated by
 * {@code OrderService} and {@code ProductService} whenever orders or
 * products change, so a stale dashboard resolves on the very next reload.</p>
 */
public class DashboardService {

    private static final String DASHBOARD_KEY = "dashboard:admin";

    private final ProductRepository productDAO = new ProductRepository();
    private final OrderRepository orderDAO = new OrderRepository();
    private final InventoryRepository inventoryDAO = new InventoryRepository();
    private final CartRepository cartDAO = new CartRepository();

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAdminStats() {
        if (CacheManager.isCacheEnabled()) {
            Map<String, Object> cached = (Map<String, Object>) CacheManager.getDashboard(DASHBOARD_KEY);
            if (cached != null) {
                return cached;
            }
            Map<String, Object> stats = buildAdminStats();
            CacheManager.putDashboard(DASHBOARD_KEY, stats);
            return stats;
        }
        return buildAdminStats();
    }

    private Map<String, Object> buildAdminStats() {
        Map<String, Object> stats = loadSummaryCounts();
        // Load recent orders with batch optimization
        List<Order> recentOrders = orderDAO.recentOrders(6);
        if (!recentOrders.isEmpty()) {
            // Batch load items and events for all recent orders in 2 queries instead of 12
            List<Integer> orderIds = recentOrders.stream().map(Order::getOrderId).toList();
            var itemsMap = com.hengtongan.computerstore.infrastructure.optimization.QueryBatchOptimizer.batchLoadOrderItems(orderIds);
            var eventsMap = com.hengtongan.computerstore.infrastructure.optimization.QueryBatchOptimizer.batchLoadStatusEvents(orderIds);

            for (Order order : recentOrders) {
                order.setItems(itemsMap.getOrDefault(order.getOrderId(), new ArrayList<>()));
                order.setStatusEvents(eventsMap.getOrDefault(order.getOrderId(), new ArrayList<>()));
            }
        }
        stats.put("recentOrders", recentOrders);
        stats.put("recentLogs", inventoryDAO.listRecent(6));
        return stats;
    }

    /**
     * Same assembled stats as {@link #getAdminStats()} but always recomputed
     * fresh, bypassing the dashboard cache. Used by the dashboard's realtime
     * JSON endpoint so live patches never serve a stale 5-minute snapshot.
     */
    public Map<String, Object> getFreshAdminStats() {
        return buildAdminStats();
    }

    /**
     * One round-trip for the seven scalar dashboard counters instead of seven
     * separate connection borrows.
     */
    private Map<String, Object> loadSummaryCounts() {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM products) AS total_products, "
                + "(SELECT COUNT(*) FROM users WHERE role = ?) AS total_customers, "
                + "(SELECT COUNT(*) FROM orders) AS total_orders, "
                + "(SELECT COALESCE(SUM(total_amount), 0) FROM orders "
                + "  WHERE status NOT IN ('CANCELLED', 'REFUNDED')) AS total_revenue, "
                + "(SELECT COUNT(*) FROM products WHERE status <> 'DISCONTINUED' "
                + "  AND stock_quantity > 0 AND stock_quantity <= ?) AS low_stock_count, "
                + "(SELECT COUNT(*) FROM products WHERE status <> 'DISCONTINUED' "
                + "  AND stock_quantity = 0) AS out_of_stock_count, "
                + "(SELECT COUNT(*) FROM orders WHERE status = ?) AS pending_orders, "
                + "(SELECT COUNT(*) FROM reviews WHERE status = 'PENDING') AS pending_reviews";

        Map<String, Object> stats = new LinkedHashMap<>();
        try (Connection c = DBConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, User.Role.CUSTOMER.name());
            ps.setInt(2, Product.LOW_STOCK_THRESHOLD);
            ps.setString(3, Order.Status.PENDING.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalProducts", rs.getLong("total_products"));
                    stats.put("totalCustomers", rs.getLong("total_customers"));
                    stats.put("totalOrders", rs.getLong("total_orders"));
                    stats.put("totalRevenue", rs.getBigDecimal("total_revenue"));
                    stats.put("lowStockCount", rs.getLong("low_stock_count"));
                    stats.put("outOfStockCount", rs.getLong("out_of_stock_count"));
                    stats.put("pendingOrders", rs.getLong("pending_orders"));
                    stats.put("pendingReviews", rs.getLong("pending_reviews"));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("loading dashboard stats", e);
        }
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
