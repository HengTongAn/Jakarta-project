package com.example.computer_store.dao;

import com.example.computer_store.model.Order;
import com.example.computer_store.model.OrderItem;
import com.example.computer_store.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    private static final String COLUMNS =
            "o.order_id, o.user_id, o.order_date, o.total_amount, o.status, "
            + "u.full_name AS customer_name, u.username AS customer_username, "
            + "(SELECT COUNT(*) FROM order_items oi WHERE oi.order_id = o.order_id) AS item_count";

    private static final String FROM_JOINS =
            "FROM orders o JOIN users u ON u.user_id = o.user_id ";

    private Order mapRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setOrderId(rs.getInt("order_id"));
        o.setUserId(rs.getInt("user_id"));
        o.setOrderDate(rs.getTimestamp("order_date"));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setStatus(Order.Status.valueOf(rs.getString("status")));
        o.setCustomerName(rs.getString("customer_name"));
        o.setCustomerUsername(rs.getString("customer_username"));
        o.setItemCount(rs.getInt("item_count"));
        return o;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    /** Creates an order inside the given transaction and returns the new id. */
    public int createOrder(Connection c, Order order) throws SQLException {
        String sql = "INSERT INTO orders (user_id, total_amount, status) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getUserId());
            ps.setBigDecimal(2, order.getTotalAmount());
            ps.setString(3, order.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new SQLException("No generated key returned for order");
        }
    }

    /** Creates an order item inside the given transaction. */
    public void createOrderItem(Connection c, OrderItem item) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price, subtotal) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, item.getOrderId());
            ps.setInt(2, item.getProductId());
            ps.setInt(3, item.getQuantity());
            ps.setBigDecimal(4, item.getUnitPrice());
            ps.setBigDecimal(5, item.getSubtotal());
            ps.executeUpdate();
        }
    }

    public List<Order> findAll() {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + " ORDER BY o.order_date DESC";
        return queryList(sql);
    }

    public List<Order> findByStatus(Order.Status status) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + " WHERE o.status = ? ORDER BY o.order_date DESC";
        List<Order> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing orders by status", e);
        }
        return list;
    }

    public List<Order> findByUserId(int userId) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + " WHERE o.user_id = ? ORDER BY o.order_date DESC";
        List<Order> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing orders", e);
        }
        return list;
    }

    public Order findById(int orderId) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + " WHERE o.order_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order o = mapRow(rs);
                    o.setItems(findItemsByOrderId(orderId));
                    return o;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding order", e);
        }
        return null;
    }

    /** Loads an order only when it belongs to the given customer. */
    public Order findByIdForUser(int orderId, int userId) {
        Order o = findById(orderId);
        if (o != null && o.getUserId() == userId) {
            return o;
        }
        return null;
    }

    public void updateStatus(int orderId, Order.Status status) {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating order status", e);
        }
    }

    public void updateStatus(Connection c, int orderId, Order.Status status) {
        String sql = "UPDATE orders SET status = ? WHERE order_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setInt(2, orderId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating order status", e);
        }
    }

    public List<OrderItem> findItemsByOrderId(int orderId) {
        String sql = "SELECT oi.order_item_id, oi.order_id, oi.product_id, oi.quantity, "
                + "oi.unit_price, oi.subtotal, p.name AS product_name "
                + "FROM order_items oi JOIN products p ON p.product_id = oi.product_id "
                + "WHERE oi.order_id = ? ORDER BY oi.order_item_id";
        List<OrderItem> items = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setOrderItemId(rs.getInt("order_item_id"));
                    item.setOrderId(rs.getInt("order_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setSubtotal(rs.getBigDecimal("subtotal"));
                    item.setProductName(rs.getString("product_name"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing order items", e);
        }
        return items;
    }

    public long countAll() {
        String sql = "SELECT COUNT(*) FROM orders";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting orders", e);
        }
        return 0;
    }

    public long countByStatus(Order.Status status) {
        String sql = "SELECT COUNT(*) FROM orders WHERE status = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting orders by status", e);
        }
        return 0;
    }

    /** Revenue generated by non-cancelled orders. */
    public java.math.BigDecimal totalRevenue() {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE status <> 'CANCELLED'";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error summing revenue", e);
        }
        return java.math.BigDecimal.ZERO;
    }

    /** Total number of items sold across non-cancelled orders. */
    public long totalItemsSold() {
        String sql = "SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi "
                + "JOIN orders o ON o.order_id = oi.order_id WHERE o.status <> 'CANCELLED'";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error summing items sold", e);
        }
        return 0;
    }

    public List<Order> recentOrders(int limit) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + " ORDER BY o.order_date DESC LIMIT ?";
        List<Order> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing recent orders", e);
        }
        return list;
    }

    private List<Order> queryList(String sql) {
        List<Order> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error executing order query", e);
        }
        return list;
    }
}