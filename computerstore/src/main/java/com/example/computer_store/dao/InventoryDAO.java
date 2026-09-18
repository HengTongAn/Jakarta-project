package com.example.computer_store.dao;

import com.example.computer_store.model.InventoryLog;
import com.example.computer_store.model.Product;
import com.example.computer_store.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InventoryDAO {

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    public void addLog(Connection c, InventoryLog log) throws SQLException {
        String sql = "INSERT INTO inventory_logs (product_id, old_quantity, new_quantity, action, user_id) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, log.getProductId());
            ps.setInt(2, log.getOldQuantity());
            ps.setInt(3, log.getNewQuantity());
            ps.setString(4, log.getAction());
            if (log.getUserId() != null) {
                ps.setInt(5, log.getUserId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.executeUpdate();
        }
    }

    public List<InventoryLog> listRecent(int limit) {
        String sql = "SELECT l.log_id, l.product_id, l.old_quantity, l.new_quantity, l.action, "
                + "l.user_id, l.created_at, p.name AS product_name "
                + "FROM inventory_logs l JOIN products p ON p.product_id = l.product_id "
                + "ORDER BY l.created_at DESC LIMIT ?";
        List<InventoryLog> logs = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapLog(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing inventory logs", e);
        }
        return logs;
    }

    private InventoryLog mapLog(ResultSet rs) throws SQLException {
        InventoryLog log = new InventoryLog();
        log.setLogId(rs.getInt("log_id"));
        log.setProductId(rs.getInt("product_id"));
        log.setOldQuantity(rs.getInt("old_quantity"));
        log.setNewQuantity(rs.getInt("new_quantity"));
        log.setAction(rs.getString("action"));
        int userId = rs.getInt("user_id");
        log.setUserId(rs.wasNull() ? null : userId);
        log.setCreatedAt(rs.getTimestamp("created_at"));
        log.setProductName(rs.getString("product_name"));
        return log;
    }

    public List<Product> listLowStock(int threshold) {
        String sql = "SELECT p.product_id, p.category_id, p.brand_id, p.name, p.sku, p.description, "
                + "p.price, p.stock_quantity, p.status, p.created_at, p.updated_at, "
                + "c.name AS category_name, b.name AS brand_name "
                + "FROM products p "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id "
                + "WHERE p.status <> 'DISCONTINUED' AND p.stock_quantity > 0 AND p.stock_quantity <= ? "
                + "ORDER BY p.stock_quantity";
        List<Product> products = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    products.add(mapProduct(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing low stock products", e);
        }
        return products;
    }

    public List<Product> listOutOfStock() {
        String sql = "SELECT p.product_id, p.category_id, p.brand_id, p.name, p.sku, p.description, "
                + "p.price, p.stock_quantity, p.status, p.created_at, p.updated_at, "
                + "c.name AS category_name, b.name AS brand_name "
                + "FROM products p "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id "
                + "WHERE p.status <> 'DISCONTINUED' AND p.stock_quantity = 0 "
                + "ORDER BY p.name";
        List<Product> products = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                products.add(mapProduct(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing out of stock products", e);
        }
        return products;
    }

    public Product findById(int productId) {
        String sql = "SELECT p.product_id, p.category_id, p.brand_id, p.name, p.sku, p.description, "
                + "p.price, p.stock_quantity, p.status, p.created_at, p.updated_at, "
                + "c.name AS category_name, b.name AS brand_name "
                + "FROM products p "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id "
                + "WHERE p.product_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapProduct(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding product for inventory", e);
        }
        return null;
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setBrandId(rs.getInt("brand_id"));
        p.setName(rs.getString("name"));
        p.setSku(rs.getString("sku"));
        p.setDescription(rs.getString("description"));
        p.setPrice(rs.getBigDecimal("price"));
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setStatus(Product.Status.valueOf(rs.getString("status")));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        p.setCategoryName(rs.getString("category_name"));
        p.setBrandName(rs.getString("brand_name"));
        return p;
    }
}