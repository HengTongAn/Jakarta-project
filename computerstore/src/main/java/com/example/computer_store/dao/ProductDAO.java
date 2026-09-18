package com.example.computer_store.dao;

import com.example.computer_store.model.Product;
import com.example.computer_store.util.DBConnection;
import com.example.computer_store.util.ErrorHandler;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ProductDAO {

    private static final String COLUMNS_WITHOUT_IMAGE =
            "p.product_id, p.category_id, p.brand_id, p.name, p.sku, p.description, "
            + "p.price, p.stock_quantity, p.status, p.created_at, p.updated_at, p.deleted_at, p.deleted_by, p.delete_reason, "
            + "c.name AS category_name, b.name AS brand_name";

    private static final String COLUMNS_WITH_IMAGE =
            "p.product_id, p.category_id, p.brand_id, p.name, p.sku, p.description, "
            + "p.price, p.stock_quantity, p.image_url, p.status, p.created_at, p.updated_at, p.deleted_at, p.deleted_by, p.delete_reason, "
            + "c.name AS category_name, b.name AS brand_name";

    private static String COLUMNS;
    private static boolean schemaHasImageColumn = false;

    private static final String FROM_JOINS =
            "FROM products p "
            + "JOIN categories c ON c.category_id = p.category_id "
            + "JOIN brands b ON b.brand_id = p.brand_id ";

    static {
        // Detect if image_url column exists in database
        Connection c = null;
        try {
            c = DBConnection.getConnection();
            DatabaseMetaData meta = c.getMetaData();
            ResultSet columns = meta.getColumns(null, null, "products", "image_url");
            if (columns.next()) {
                COLUMNS = COLUMNS_WITH_IMAGE;
                schemaHasImageColumn = true;
            } else {
                COLUMNS = COLUMNS_WITHOUT_IMAGE;
                schemaHasImageColumn = false;
            }
            columns.close();
        } catch (SQLException e) {
            // Default to old schema if detection fails
            COLUMNS = COLUMNS_WITHOUT_IMAGE;
            schemaHasImageColumn = false;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException e) {
                    // Ignore close error
                }
            }
        }
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setProductId(rs.getInt("product_id"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setBrandId(rs.getInt("brand_id"));
        p.setName(rs.getString("name"));
        p.setSku(rs.getString("sku"));
        p.setDescription(rs.getString("description"));
        p.setPrice(rs.getBigDecimal("price"));
        p.setStockQuantity(rs.getInt("stock_quantity"));
        // Safely handle image_url column that may not exist in older schemas
        try {
            p.setImageUrl(rs.getString("image_url"));
        } catch (SQLException e) {
            p.setImageUrl(null); // Column doesn't exist
        }
        p.setStatus(Product.Status.valueOf(rs.getString("status")));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        p.setDeletedAt(rs.getTimestamp("deleted_at"));
        p.setDeletedBy((Integer) rs.getObject("deleted_by"));
        p.setDeleteReason(rs.getString("delete_reason"));
        p.setCategoryName(rs.getString("category_name"));
        p.setBrandName(rs.getString("brand_name"));
        return p;
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    /**
     * Customer-facing search with optional filters. Only non-discontinued
     * products are returned. Multiple categories/brands are supported via IN
     * clauses, and results can be sorted.
     */
    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " " + FROM_JOINS + " WHERE p.deleted_at IS NULL AND p.status <> 'DISCONTINUED' ");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, search, categoryIds, brandIds, minPrice, maxPrice);
        sql.append(orderBy(sort));

        return queryList(sql.toString(), params);
    }

    /**
     * Products per category, but only the ones that also match the current
     * brand / search / price filters. This way the numbers change as the user
     * filters by brand etc.
     */
    public Map<Integer, Long> countByCategory(String search, List<Integer> brandIds,
                                              BigDecimal minPrice, BigDecimal maxPrice) {
        StringBuilder sql = new StringBuilder("SELECT p.category_id, COUNT(*) FROM products p "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id WHERE p.deleted_at IS NULL AND p.status <> 'DISCONTINUED' ");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, search, null, brandIds, minPrice, maxPrice);
        sql.append("GROUP BY p.category_id");
        return countMap(sql.toString(), params);
    }

    /**
     * Products per brand, but only the ones that also match the current
     * category / search / price filters.
     */
    public Map<Integer, Long> countByBrand(String search, List<Integer> categoryIds,
                                           BigDecimal minPrice, BigDecimal maxPrice) {
        StringBuilder sql = new StringBuilder("SELECT p.brand_id, COUNT(*) FROM products p "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id WHERE p.deleted_at IS NULL AND p.status <> 'DISCONTINUED' ");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, search, categoryIds, null, minPrice, maxPrice);
        sql.append("GROUP BY p.brand_id");
        return countMap(sql.toString(), params);
    }

    // adds one WHERE/AND condition for every active filter
    private void applyFilters(StringBuilder sql, List<Object> params, String search,
                              List<Integer> categoryIds, List<Integer> brandIds,
                              BigDecimal minPrice, BigDecimal maxPrice) {
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (p.name LIKE ? ESCAPE '\\\\' OR p.sku LIKE ? ESCAPE '\\\\' OR b.name LIKE ? ESCAPE '\\\\') ");
            String like = "%" + escapeLike(search.trim()) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            sql.append("AND p.category_id IN (").append(placeholders(categoryIds.size())).append(") ");
            params.addAll(categoryIds);
        }
        if (brandIds != null && !brandIds.isEmpty()) {
            sql.append("AND p.brand_id IN (").append(placeholders(brandIds.size())).append(") ");
            params.addAll(brandIds);
        }
        if (minPrice != null) {
            sql.append("AND p.price >= ? ");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append("AND p.price <= ? ");
            params.add(maxPrice);
        }
    }

    // runs a "SELECT id, COUNT(*) ... GROUP BY id" query and returns the map
    private Map<Integer, Long> countMap(String sql, List<Object> params) {
        Map<Integer, Long> counts = new HashMap<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getInt(1), rs.getLong(2));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("loading product counts", e);
        }
        return counts;
    }

    // escapes % and _ so user input is matched literally, not as wildcards
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    // makes "?,?,?..." so we can put a whole list into an IN (...)
    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    private static String orderBy(String sort) {
        if ("price_asc".equals(sort)) {
            return "ORDER BY p.price ASC, p.name";
        }
        if ("price_desc".equals(sort)) {
            return "ORDER BY p.price DESC, p.name";
        }
        if ("newest".equals(sort)) {
            return "ORDER BY p.created_at DESC, p.name";
        }
        return "ORDER BY p.name";
    }

    public List<Product> findAll() {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + " WHERE p.deleted_at IS NULL ORDER BY p.name";
        return queryList(sql, List.of());
    }

    /**
     * Trending products ranked by units sold (from order history), then most
     * recently added, so the strip always has content even without sales.
     */
    public List<Product> findTrending(int limit) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS
                + "LEFT JOIN (SELECT product_id, SUM(quantity) AS units_sold FROM order_items GROUP BY product_id) o "
                + "ON o.product_id = p.product_id "
                + "WHERE p.deleted_at IS NULL AND p.status <> 'DISCONTINUED' "
                + "ORDER BY COALESCE(o.units_sold, 0) DESC, p.product_id DESC LIMIT ?";
        return queryList(sql, List.of(Math.max(1, limit)));
    }

    public Product findById(int productId) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + " WHERE p.product_id = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding product", e);
        }
        return null;
    }

    public Product findBySku(String sku) {
        String sql = "SELECT " + COLUMNS + " " + FROM_JOINS + " WHERE p.sku = ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding product by sku", e);
        }
        return null;
    }

    public int create(Product product) {
        String sql;
        if (schemaHasImageColumn) {
            sql = "INSERT INTO products (category_id, brand_id, name, sku, description, price, stock_quantity, image_url, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            sql = "INSERT INTO products (category_id, brand_id, name, sku, description, price, stock_quantity, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        }
        
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, product.getCategoryId());
            ps.setInt(2, product.getBrandId());
            ps.setString(3, product.getName());
            ps.setString(4, product.getSku());
            ps.setString(5, product.getDescription());
            ps.setBigDecimal(6, product.getPrice());
            ps.setInt(7, product.getStockQuantity());
            
            if (schemaHasImageColumn) {
                ps.setString(8, product.getImageUrl());
                ps.setString(9, product.getStatus().name());
            } else {
                ps.setString(8, product.getStatus().name());
            }
            
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("creating product", e);
        }
    }

    public void update(Product product) {
        String sql;
        if (schemaHasImageColumn) {
            sql = "UPDATE products SET category_id = ?, brand_id = ?, name = ?, sku = ?, "
                    + "description = ?, price = ?, stock_quantity = ?, image_url = ?, status = ? WHERE product_id = ?";
        } else {
            sql = "UPDATE products SET category_id = ?, brand_id = ?, name = ?, sku = ?, "
                    + "description = ?, price = ?, stock_quantity = ?, status = ? WHERE product_id = ?";
        }
        
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, product.getCategoryId());
            ps.setInt(2, product.getBrandId());
            ps.setString(3, product.getName());
            ps.setString(4, product.getSku());
            ps.setString(5, product.getDescription());
            ps.setBigDecimal(6, product.getPrice());
            ps.setInt(7, product.getStockQuantity());
            
            if (schemaHasImageColumn) {
                ps.setString(8, product.getImageUrl());
                ps.setString(9, product.getStatus().name());
                ps.setInt(10, product.getProductId());
            } else {
                ps.setString(8, product.getStatus().name());
                ps.setInt(9, product.getProductId());
            }
            
            ps.executeUpdate();
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating product", e);
        }
    }

    public boolean delete(int productId) { return softDelete(productId, null, null); }

    public boolean softDelete(int productId, Integer actorId, String reason) {
        String sql = "UPDATE products SET deleted_at = NOW(), deleted_by = ?, delete_reason = ?, status = 'DISCONTINUED' WHERE product_id = ? AND deleted_at IS NULL";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, actorId); ps.setString(2, reason); ps.setInt(3, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("soft deleting product", e); }
    }

    /**
     * Atomically decreases stock when the available quantity is sufficient.
     * Returns false when requested quantity exceeds the current stock.
     */
    public boolean reduceStock(Connection c, int productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = stock_quantity - ? "
                + "WHERE product_id = ? AND stock_quantity >= ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() > 0;
        }
    }

    public void setStock(Connection c, int productId, int newQuantity,
                         Product.Status status) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = ?, status = ? WHERE product_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newQuantity);
            ps.setString(2, status.name());
            ps.setInt(3, productId);
            ps.executeUpdate();
        }
    }

    public void markDiscontinued(Connection c, int productId) throws SQLException {
        String sql = "UPDATE products SET status = 'DISCONTINUED' WHERE product_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.executeUpdate();
        }
    }

    public long countAll() {
        String sql = "SELECT COUNT(*) FROM products";
        return countScalar(sql);
    }

    public long countStockThreshold(boolean outOfStock, int lowStockThreshold) {
        String sql = outOfStock
                ? "SELECT COUNT(*) FROM products WHERE status <> 'DISCONTINUED' AND stock_quantity = 0"
                : "SELECT COUNT(*) FROM products WHERE status <> 'DISCONTINUED' AND stock_quantity > 0 AND stock_quantity <= ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (!outOfStock) {
                ps.setInt(1, lowStockThreshold);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("counting stock", e);
        }
        return 0;
    }

    public java.math.BigDecimal totalStockValue() {
        String sql = "SELECT COALESCE(SUM(price * stock_quantity), 0) FROM products "
                + "WHERE status <> 'DISCONTINUED'";
        return scalarDecimal(sql);
    }

    public java.math.BigDecimal lowStockValue(int lowStockThreshold) {
        String sql = "SELECT COALESCE(SUM(price * stock_quantity), 0) FROM products "
                + "WHERE status <> 'DISCONTINUED' AND stock_quantity > 0 AND stock_quantity <= ?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, lowStockThreshold);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("computing low stock value", e);
        }
        return java.math.BigDecimal.ZERO;
    }

    private java.math.BigDecimal scalarDecimal(String sql) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("executing aggregate query", e);
        }
        return java.math.BigDecimal.ZERO;
    }

    private long countScalar(String sql) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("executing count query", e);
        }
        return 0;
    }

    private List<Product> queryList(String sql, List<Object> params) {
        List<Product> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("executing product query", e);
        }
        return list;
    }
}
