package com.example.computer_store.core.repository;

import com.example.computer_store.infrastructure.cache.CacheManager;
import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.core.domain.entity.ProductSpec;
import com.example.computer_store.infrastructure.monitoring.MetricsCollector;
import com.example.computer_store.infrastructure.monitoring.QueryMonitor;
import com.example.computer_store.infrastructure.persistence.DBConnection;
import com.example.computer_store.util.web.ErrorHandler;
import com.example.computer_store.infrastructure.persistence.SchemaUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Access Object for products with schema-aware column handling.
 * Detects if image_url column exists on first access, not at class load.
 */
public class ProductRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductRepository.class);

    private static final String BASE_COLUMNS =
            "p.product_id, p.category_id, p.brand_id, p.name, p.sku, p.description, "
            + "p.price, p.stock_quantity, p.status, p.created_at, p.updated_at, p.deleted_at, p.deleted_by, p.delete_reason, "
            + "c.name AS category_name, b.name AS brand_name";

    /** Ordered list of optional columns that may be missing in legacy schemas (added via migrations). */
    private static final List<String> OPTIONAL_COLUMNS =
            List.of("image_url", "highlights", "box_contents", "warranty_info", "source_url");

    private static final String FROM_JOINS =
            "FROM products p "
            + "JOIN categories c ON c.category_id = p.category_id "
            + "JOIN brands b ON b.brand_id = p.brand_id ";

    // Lazy, schema-aware detection of optional product columns (first access)
    private static final AtomicReference<List<String>> PRESENT_OPTIONAL = new AtomicReference<>();

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
        // Optional columns may not exist in older schemas - read defensively.
        p.setHighlights(safeString(rs, "highlights"));
        p.setImageUrl(safeString(rs, "image_url"));
        p.setBoxContents(safeString(rs, "box_contents"));
        p.setWarrantyInfo(safeString(rs, "warranty_info"));
        p.setSourceUrl(safeString(rs, "source_url"));
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

    private static String safeString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null; // Column not present in this schema
        }
    }

    private Connection conn() throws SQLException {
        return DBConnection.getConnection();
    }

    private static boolean present(String column) {
        return presentOptional().contains(column);
    }

    /**
     * Detects which optional {@link #OPTIONAL_COLUMNS} exist in the database.
     * Called lazily on first access; cached afterwards (new columns only appear
     * through migrations, which run before the app starts serving).
     */
    private static List<String> presentOptional() {
        List<String> cached = PRESENT_OPTIONAL.get();
        if (cached != null) {
            return cached;
        }
        synchronized (ProductRepository.class) {
            cached = PRESENT_OPTIONAL.get();
            if (cached != null) {
                return cached;
            }
            List<String> present = new ArrayList<>();
            for (String column : OPTIONAL_COLUMNS) {
                if (SchemaUtil.hasColumn("products", column)) {
                    present.add(column);
                }
            }
            PRESENT_OPTIONAL.set(present);
            LOGGER.info("Product schema detection: optional columns present = {}", present);
            return present;
        }
    }

    /** SELECT column list including every optional column present in this schema. */
    private static String selectColumns() {
        StringBuilder sb = new StringBuilder(BASE_COLUMNS);
        for (String column : presentOptional()) {
            sb.append(", p.").append(column);
        }
        return sb.toString();
    }

    /**
     * Customer-facing search with optional filters. Only non-discontinued
     * products are returned. Multiple categories/brands are supported via IN
     * clauses, and results can be sorted.
     */
    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        return search(search, categoryIds, brandIds, minPrice, maxPrice, sort, 0, 0);
    }

    /**
     * Same as {@link #search(String, List, List, BigDecimal, BigDecimal, String)}
     * but caps the result set when {@code limit > 0}.
     */
    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort, int limit) {
        return search(search, categoryIds, brandIds, minPrice, maxPrice, sort, limit, 0);
    }

    /**
     * Search with pagination support.
     * @param limit max results per page (0 = no limit)
     * @param offset offset for pagination (0 = first page)
     */
    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort,
                                int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT " + selectColumns() + " " + FROM_JOINS + " WHERE p.deleted_at IS NULL AND p.status <> 'DISCONTINUED' ");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, search, categoryIds, brandIds, minPrice, maxPrice);
        sql.append(orderBy(sort));
        if (limit > 0) {
            sql.append(" LIMIT ?");
            params.add(limit);
            if (offset > 0) {
                sql.append(" OFFSET ?");
                params.add(offset);
            }
        }

        return queryList(sql.toString(), params);
    }

    /**
     * Count total products matching search criteria for pagination.
     */
    public long countSearch(String search, List<Integer> categoryIds, List<Integer> brandIds,
                            BigDecimal minPrice, BigDecimal maxPrice) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM products p "
                + "JOIN categories c ON c.category_id = p.category_id "
                + "JOIN brands b ON b.brand_id = p.brand_id "
                + "WHERE p.deleted_at IS NULL AND p.status <> 'DISCONTINUED' ");
        List<Object> params = new ArrayList<>();
        applyFilters(sql, params, search, categoryIds, brandIds, minPrice, maxPrice);
        long start = System.nanoTime();

        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("counting search results", e);
        } finally {
            recordQuery("product", sql.toString(), start);
        }
        return 0;
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
            sql.append("AND (p.name LIKE ? ESCAPE '\\\\' OR p.sku LIKE ? ESCAPE '\\\\' OR b.name LIKE ? ESCAPE '\\\\' OR p.description LIKE ? ESCAPE '\\\\') ");
            String like = "%" + escapeLike(search.trim()) + "%";
            params.add(like);
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
        long start = System.nanoTime();
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
        } finally {
            recordQuery("product", sql, start);
        }
        return counts;
    }

    /**
     * Feeds the live /admin/performance query table. Only the hot storefront
     * read paths are timed here (search rows, counts, trending, detail) -- the
     * write/transaction paths are covered by the pool and dashboard metrics.
     */
    private static void recordQuery(String queryType, String sql, long startNanos) {
        long elapsedMs = Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
        QueryMonitor.monitorQuery(queryType, QueryMonitor.extractSignature(sql), elapsedMs);
        MetricsCollector.recordDatabaseQuery(elapsedMs);
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
        String sql = "SELECT " + selectColumns() + " " + FROM_JOINS + " WHERE p.deleted_at IS NULL ORDER BY p.name";
        return queryList(sql, List.of());
    }

    /**
     * Trending products ranked by units sold (from order history), then most
     * recently added, so the strip always has content even without sales.
     */
    public List<Product> findTrending(int limit) {
        String sql = "SELECT " + selectColumns() + " " + FROM_JOINS
                + "LEFT JOIN ("
                + "  SELECT oi.product_id, SUM(oi.quantity) AS units_sold "
                + "  FROM order_items oi "
                + "  INNER JOIN orders o ON o.order_id = oi.order_id "
                + "  WHERE o.status NOT IN ('CANCELLED', 'REFUNDED') "
                + "  GROUP BY oi.product_id"
                + ") o ON o.product_id = p.product_id "
                + "WHERE p.deleted_at IS NULL AND p.status <> 'DISCONTINUED' "
                + "ORDER BY COALESCE(o.units_sold, 0) DESC, p.product_id DESC LIMIT ?";
        return queryList(sql, List.of(Math.max(1, limit)));
    }

    public Product findById(int productId) {
        String cacheKey = "product_" + productId;

        if (CacheManager.isCacheEnabled()) {
            // Single-flight: concurrent lookups of the same id share one
            // database load instead of each querying it (see CacheManager).
            return (Product) CacheManager.getOrLoadProduct(cacheKey, k -> loadById(productId));
        }
        return loadById(productId);
    }

    private Product loadById(int productId) {
        String sql = "SELECT " + selectColumns() + " " + FROM_JOINS + " WHERE p.product_id = ?";
        long start = System.nanoTime();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("finding product", e);
        } finally {
            recordQuery("product", sql, start);
        }
        return null;
    }

    public Product findBySku(String sku) {
        String sql = "SELECT " + selectColumns() + " " + FROM_JOINS + " WHERE p.sku = ?";
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
        try (Connection c = conn()) {
            return create(c, product);
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("creating product", e);
        }
    }

    /** Creates the product on the given connection (caller owns the transaction). */
    public int create(Connection c, Product product) throws SQLException {
        List<String> present = presentOptional();
        StringBuilder cols = new StringBuilder(
                "INSERT INTO products (category_id, brand_id, name, sku, description, price, stock_quantity");
        for (String column : present) {
            cols.append(", ").append(column);
        }
        cols.append(", status) VALUES (?, ?, ?, ?, ?, ?, ?");
        for (int i = 0; i < present.size(); i++) {
            cols.append(", ?");
        }
        cols.append(")");

        try (PreparedStatement ps = c.prepareStatement(cols.toString(), Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            ps.setInt(idx++, product.getCategoryId());
            ps.setInt(idx++, product.getBrandId());
            ps.setString(idx++, product.getName());
            ps.setString(idx++, product.getSku());
            ps.setString(idx++, product.getDescription());
            ps.setBigDecimal(idx++, product.getPrice());
            ps.setInt(idx++, product.getStockQuantity());
            for (String column : present) {
                ps.setString(idx++, valueFor(column, product));
            }
            ps.setString(idx, product.getStatus().name());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    // Callers own the transaction and the post-commit cache
                    // invalidation (ProductServiceImpl.create/update invalidate
                    // after commit). Invalidating here would run BEFORE the
                    // commit and could let a concurrent list rebuild cache the
                    // pre-commit state for the whole catalog TTL.
                    return keys.getInt(1);
                }
            }
            return -1;
        }
    }

    public void update(Product product) {
        try (Connection c = conn()) {
            update(c, product);
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating product", e);
        }
    }

    /** Updates the product on the given connection (caller owns the transaction). */
    public void update(Connection c, Product product) throws SQLException {
        List<String> present = presentOptional();
        StringBuilder sql = new StringBuilder("UPDATE products SET category_id = ?, brand_id = ?, name = ?, sku = ?, "
                + "description = ?, price = ?, stock_quantity = ?");
        for (String column : present) {
            sql.append(", ").append(column).append(" = ?");
        }
        sql.append(", status = ? WHERE product_id = ?");

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setInt(idx++, product.getCategoryId());
            ps.setInt(idx++, product.getBrandId());
            ps.setString(idx++, product.getName());
            ps.setString(idx++, product.getSku());
            ps.setString(idx++, product.getDescription());
            ps.setBigDecimal(idx++, product.getPrice());
            ps.setInt(idx++, product.getStockQuantity());
            for (String column : present) {
                ps.setString(idx++, valueFor(column, product));
            }
            ps.setString(idx++, product.getStatus().name());
            ps.setInt(idx, product.getProductId());

            ps.executeUpdate();
            // Invalidate cache for updated product
            if (CacheManager.isCacheEnabled()) {
                CacheManager.invalidateProduct("product_" + product.getProductId());
            }
        }
    }

    private static String valueFor(String column, Product product) {
        return switch (column) {
            case "image_url" -> product.getImageUrl();
            case "highlights" -> product.getHighlights();
            case "box_contents" -> product.getBoxContents();
            case "warranty_info" -> product.getWarrantyInfo();
            case "source_url" -> product.getSourceUrl();
            default -> null;
        };
    }

    /**
     * All key/value specifications for a product, in display order. Returns an
     * empty list when the product has no specs (or when the product_specs table
     * has not been migrated in).
     */
    public List<ProductSpec> findSpecs(int productId) {
        List<ProductSpec> specs = new ArrayList<>();
        String sql = "SELECT spec_id, product_id, spec_key, spec_value, sort_order "
                + "FROM product_specs WHERE product_id = ? ORDER BY sort_order, spec_id";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    specs.add(new ProductSpec(
                            rs.getInt("spec_id"),
                            rs.getInt("product_id"),
                            rs.getString("spec_key"),
                            rs.getString("spec_value"),
                            rs.getInt("sort_order")));
                }
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("loading product specifications", e);
        }
        return specs;
    }

    /**
     * Replaces the full specification list of a product (delete + insert inside
     * one transaction, so a failed save can never leave a half-written list).
     */
    public void saveSpecs(int productId, List<ProductSpec> specs) {
        boolean previousAutoCommit;
        try (Connection c = conn()) {
            previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                replaceSpecs(c, productId, specs);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("saving product specifications", e);
        }
        ProductRepository.invalidateProductCache(productId);
    }

    /**
     * Replaces the full specification list on the given connection. The caller
     * owns the transaction (used by the product save path so the product row
     * and its specs commit together).
     */
    public void saveSpecs(Connection c, int productId, List<ProductSpec> specs) throws SQLException {
        replaceSpecs(c, productId, specs);
    }

    private static void replaceSpecs(Connection c, int productId, List<ProductSpec> specs) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM product_specs WHERE product_id = ?")) {
            del.setInt(1, productId);
            del.executeUpdate();
        }
        if (specs != null && !specs.isEmpty()) {
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO product_specs (product_id, spec_key, spec_value, sort_order) VALUES (?, ?, ?, ?)")) {
                int order = 0;
                for (ProductSpec spec : specs) {
                    ins.setInt(1, productId);
                    ins.setString(2, spec.getSpecKey());
                    ins.setString(3, spec.getSpecValue());
                    ins.setInt(4, order++);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    public boolean delete(int productId) { return softDelete(productId, null, null); }

    public boolean softDelete(int productId, Integer actorId, String reason) {
        String sql = "UPDATE products SET deleted_at = NOW(), deleted_by = ?, delete_reason = ?, status = 'DISCONTINUED' WHERE product_id = ? AND deleted_at IS NULL";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, actorId); ps.setString(2, reason); ps.setInt(3, productId);
            boolean deleted = ps.executeUpdate() > 0;
            // Invalidate cache for deleted product
            if (deleted && CacheManager.isCacheEnabled()) {
                CacheManager.invalidateProduct("product_" + productId);
            }
            return deleted;
        } catch (SQLException e) { throw ErrorHandler.handleDatabaseError("soft deleting product", e); }
    }

    /**
     * Atomically decreases stock when the available quantity is sufficient.
     * Returns false when requested quantity exceeds the current stock.
     */
    public boolean reduceStock(Connection c, int productId, int quantity) throws SQLException {
        // Decrement stock and recompute status in one statement. Discontinued
        // products keep DISCONTINUED so checkout cannot silently re-list them.
        String sql = """
            UPDATE products
            SET stock_quantity = stock_quantity - ?,
                status = CASE
                    WHEN status = 'DISCONTINUED' THEN 'DISCONTINUED'
                    WHEN stock_quantity - ? <= 0 THEN 'OUT_OF_STOCK'
                    WHEN stock_quantity - ? <= 5 THEN 'LOW_STOCK'
                    ELSE 'IN_STOCK'
                END
            WHERE product_id = ? AND stock_quantity >= ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, quantity);
            ps.setInt(3, quantity);
            ps.setInt(4, productId);
            ps.setInt(5, quantity);
            boolean reduced = ps.executeUpdate() > 0;
            // No cache invalidation here: this method runs inside the caller's
            // transaction. Invalidating on a connection that may roll back would
            // evict a cache entry the DB never changed; a concurrent reader could
            // even re-cache pre-commit data. Callers invalidate after commit.
            return reduced;
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
            // Caller invalidates the product cache after its transaction commits.
        }
    }

    /**
     * Readings of the current stock and status of a product, done on the
     * caller's (transaction) connection so the numbers are consistent with
     * the surrounding transaction.
     */
    public static final class StockSnapshot {
        public final int stockQuantity;
        public final Product.Status status;

        public StockSnapshot(int stockQuantity, Product.Status status) {
            this.stockQuantity = stockQuantity;
            this.status = status;
        }
    }

    public StockSnapshot stock(Connection c, int productId) throws SQLException {
        String sql = "SELECT stock_quantity, status FROM products WHERE product_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new StockSnapshot(rs.getInt("stock_quantity"),
                            Product.Status.valueOf(rs.getString("status")));
                }
            }
        }
        return null;
    }

    /**
     * Returns cancelled/refunded units to stock atomically (never via a
     * read-modify-write across separate connections). The product's status is
     * recomputed from the new stock, but an explicitly discontinued product
     * stays discontinued so abandoned stock can never silently be re-listed.
     * Returns the stock snapshot after the increment, or null when the
     * product no longer exists.
     */
    public StockSnapshot restoreStock(Connection c, int productId, int quantity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, productId);
            if (ps.executeUpdate() == 0) {
                return null;
            }
            // Caller invalidates the product cache after its transaction commits.
        }
        StockSnapshot restored = stock(c, productId);
        if (restored == null) {
            return null;
        }
        if (restored.status != Product.Status.DISCONTINUED) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE products SET status = ? WHERE product_id = ?")) {
                ps.setString(1, Product.computeStatus(restored.stockQuantity).name());
                ps.setInt(2, productId);
                ps.executeUpdate();
            }
        }
        return restored;
    }

    public void markDiscontinued(Connection c, int productId) throws SQLException {
        String sql = "UPDATE products SET status = 'DISCONTINUED' WHERE product_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.executeUpdate();
            // Caller (ProductServiceImpl.delete) invalidates caches after this
            // write is durable.
        }
    }

    /**
     * Drops the cached product entry after any write that changes stock or
     * status. {@link #findById(int)} caches product rows, so without this the
     * storefront would keep showing stale availability until the TTL expires.
     * Public because transaction-owning services also evict after commit.
     */
    public static void invalidateProductCache(int productId) {
        if (CacheManager.isCacheEnabled()) {
            CacheManager.invalidateProduct("product_" + productId);
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
        long start = System.nanoTime();
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
        } finally {
            recordQuery("product", sql, start);
        }
        return list;
    }
}
