package com.example.computer_store.dao;

import com.example.computer_store.model.Brand;
import com.example.computer_store.model.Category;
import com.example.computer_store.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    private static final String COLUMNS =
            "c.category_id, c.name, c.description, c.created_at, COUNT(p.product_id) AS product_count";

    private Category mapRow(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setCategoryId(rs.getInt("category_id"));
        c.setName(rs.getString("name"));
        c.setDescription(rs.getString("description"));
        c.setCreatedAt(rs.getTimestamp("created_at"));
        c.setProductCount(rs.getInt("product_count"));
        return c;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    public List<Category> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM categories c "
                + "LEFT JOIN products p ON p.category_id = c.category_id "
                + "GROUP BY c.category_id, c.name, c.description, c.created_at "
                + "ORDER BY c.name";
        List<Category> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing categories", e);
        }
        return list;
    }

    public Category findById(int categoryId) {
        String sql = "SELECT " + COLUMNS + " FROM categories c "
                + "LEFT JOIN products p ON p.category_id = c.category_id "
                + "WHERE c.category_id = ? "
                + "GROUP BY c.category_id, c.name, c.description, c.created_at";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding category", e);
        }
        return null;
    }

    public Category findByName(String name) {
        String sql = "SELECT " + COLUMNS + " FROM categories c "
                + "LEFT JOIN products p ON p.category_id = c.category_id "
                + "WHERE c.name = ? "
                + "GROUP BY c.category_id, c.name, c.description, c.created_at";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding category by name", e);
        }
        return null;
    }

    public int create(Category category) {
        String sql = "INSERT INTO categories (name, description) VALUES (?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Error creating category", e);
        }
    }

    public void update(Category category) {
        String sql = "UPDATE categories SET name = ?, description = ? WHERE category_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getCategoryId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating category", e);
        }
    }

    public boolean delete(int categoryId) {
        String sql = "DELETE FROM categories WHERE category_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting category", e);
        }
    }

    public int countProducts(int categoryId) {
        String sql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting products in category", e);
        }
        return 0;
    }
}