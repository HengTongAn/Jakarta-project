package com.example.computer_store.core.repository;

import com.example.computer_store.core.domain.entity.CartItem;
import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.infrastructure.persistence.DBConnection;
import com.example.computer_store.infrastructure.persistence.SchemaUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class CartRepository {

    private static final String COLUMNS_WITHOUT_IMAGE =
            "ci.cart_item_id, ci.user_id, ci.product_id, ci.quantity, ci.added_at, "
            + "p.name, p.sku, p.price, p.stock_quantity, p.status, "
            + "c.name AS category_name, b.name AS brand_name";

    private static final String COLUMNS_WITH_IMAGE =
            "ci.cart_item_id, ci.user_id, ci.product_id, ci.quantity, ci.added_at, "
            + "p.name, p.sku, p.price, p.stock_quantity, p.image_url, p.status, "
            + "c.name AS category_name, b.name AS brand_name";

    private static final AtomicReference<String> COLUMNS = new AtomicReference<>();
    private static final AtomicReference<Boolean> SCHEMA_DETECTED = new AtomicReference<>(false);

    private void ensureSchemaDetected() {
        if (SCHEMA_DETECTED.get()) return;
        synchronized (CartRepository.class) {
            if (SCHEMA_DETECTED.get()) return;
            // True when the current schema has the optional image_url column;
            // a failed probe degrades to the pre-migration column list.
            COLUMNS.set(SchemaUtil.hasColumn("products", "image_url")
                    ? COLUMNS_WITH_IMAGE : COLUMNS_WITHOUT_IMAGE);
            SCHEMA_DETECTED.set(true);
        }
    }

    private String getColumns() {
        ensureSchemaDetected();
        return COLUMNS.get();
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    private CartItem mapRow(ResultSet rs) throws SQLException {
        CartItem item = new CartItem();
        item.setCartItemId(rs.getInt("cart_item_id"));
        item.setUserId(rs.getInt("user_id"));
        item.setProductId(rs.getInt("product_id"));
        item.setQuantity(rs.getInt("quantity"));
        item.setAddedAt(rs.getTimestamp("added_at"));

        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setName(rs.getString("name"));
        p.setSku(rs.getString("sku"));
        p.setPrice(rs.getBigDecimal("price"));
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setStatus(Product.Status.valueOf(rs.getString("status")));
        p.setCategoryName(rs.getString("category_name"));
        p.setBrandName(rs.getString("brand_name"));
        // Safely handle image_url column that may not exist in older schemas
        try {
            p.setImageUrl(rs.getString("image_url"));
        } catch (SQLException e) {
            p.setImageUrl(null); // Column doesn't exist
        }
        item.setProduct(p);
        return item;
    }

    public List<CartItem> findItemsByUser(int userId) {
        String sql = "SELECT " + getColumns() + " "
                + "FROM cart_items ci "
                + "JOIN products p ON p.product_id = ci.product_id "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id "
                + "WHERE ci.user_id = ? "
                + "ORDER BY ci.added_at";
        List<CartItem> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading cart items", e);
        }
        return list;
    }

    public CartItem findItem(int userId, int productId) {
        String sql = "SELECT " + getColumns() + " "
                + "FROM cart_items ci "
                + "JOIN products p ON p.product_id = ci.product_id "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id "
                + "WHERE ci.user_id = ? AND ci.product_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error loading cart item", e);
        }
        return null;
    }

    public void saveItem(int userId, int productId, int quantity) {
        String sql = "INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE quantity = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.setInt(4, quantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error saving cart item", e);
        }
    }

    public void updateQuantity(int cartItemId, int userId, int quantity) {
        String sql = "UPDATE cart_items SET quantity = ? WHERE cart_item_id = ? AND user_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, cartItemId);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error updating cart item", e);
        }
    }

    public void removeItem(int cartItemId, int userId) {
        String sql = "DELETE FROM cart_items WHERE cart_item_id = ? AND user_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, cartItemId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error removing cart item", e);
        }
    }

    public void clear(int userId) {
        try (Connection c = conn()) {
            clear(c, userId);
        } catch (SQLException e) {
            throw new RuntimeException("Error clearing cart", e);
        }
    }

    /**
     * Clears a cart using a caller-managed transaction. Checkout uses this so
     * the cart, order, and inventory updates either all commit or all roll
     * back together.
     */
    public void clear(Connection c, int userId) throws SQLException {
        String sql = "DELETE FROM cart_items WHERE user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public int countItems(int userId) {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM cart_items WHERE user_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error counting cart items", e);
        }
        return 0;
    }

    /**
     * Atomically adds an item to cart with stock validation in a single query.
     * Returns 1 if successful, 0 if insufficient stock or product unavailable.
     */
    public int addItemAtomic(int userId, int productId, int quantity) {
        String sql = """
            INSERT INTO cart_items (user_id, product_id, quantity)
            SELECT ?, ?, ?
            FROM products
            WHERE product_id = ? 
              AND status <> 'DISCONTINUED'
              AND stock_quantity >= ?
              AND deleted_at IS NULL
            ON DUPLICATE KEY UPDATE quantity = LEAST(
                cart_items.quantity + VALUES(quantity),
                (SELECT stock_quantity FROM products WHERE product_id = cart_items.product_id)
            )
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            ps.setInt(4, productId);
            ps.setInt(5, quantity);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Error adding cart item atomically", e);
        }
    }
}
