package com.example.computer_store.dao;

import com.example.computer_store.model.Brand;
import com.example.computer_store.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class BrandDAO {

    private static final String COLUMNS =
            "b.brand_id, b.name, b.description, b.created_at, COUNT(p.product_id) AS product_count";

    private Brand mapRow(ResultSet rs) throws SQLException {
        Brand b = new Brand();
        b.setBrandId(rs.getInt("brand_id"));
        b.setName(rs.getString("name"));
        b.setDescription(rs.getString("description"));
        b.setCreatedAt(rs.getTimestamp("created_at"));
        b.setProductCount(rs.getInt("product_count"));
        return b;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    public List<Brand> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM brands b "
                + "LEFT JOIN products p ON p.brand_id = b.brand_id "
                + "GROUP BY b.brand_id, b.name, b.description, b.created_at "
                + "ORDER BY b.name";
        List<Brand> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error listing brands", e);
        }
        return list;
    }

    public Brand findById(int brandId) {
        String sql = "SELECT " + COLUMNS + " FROM brands b "
                + "LEFT JOIN products p ON p.brand_id = b.brand_id "
                + "WHERE b.brand_id = ? "
                + "GROUP BY b.brand_id, b.name, b.description, b.created_at";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, brandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding brand", e);
        }
        return null;
    }

    public Brand findByName(String name) {
        String sql = "SELECT " + COLUMNS + " FROM brands b "
                + "LEFT JOIN products p ON p.brand_id = b.brand_id "
                + "WHERE b.name = ? "
                + "GROUP BY b.brand_id, b.name, b.description, b.created_at";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error finding brand by name", e);
        }
        return null;
    }

    public int create(Brand brand) {
        String sql = "INSERT INTO brands (name, description) VALUES (?, ?)";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, brand.getName());
            ps.setString(2, brand.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Error creating brand", e);
        }
    }

    public void update(Brand brand) {
        String sql = "UPDATE brands SET name = ?, description = ? WHERE brand_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, brand.getName());
            ps.setString(2, brand.getDescription());
            ps.setInt(3, brand.getBrandId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating brand", e);
        }
    }

    public boolean delete(int brandId) {
        String sql = "DELETE FROM brands WHERE brand_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, brandId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting brand", e);
        }
    }

    public int countProducts(int brandId) {
        String sql = "SELECT COUNT(*) FROM products WHERE brand_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, brandId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting products in brand", e);
        }
        return 0;
    }
}