package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.service.ProductService;

import com.hengtongan.computerstore.infrastructure.cache.CacheManager;
import com.hengtongan.computerstore.core.repository.ProductRepository;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.ProductSpec;
import com.hengtongan.computerstore.infrastructure.realtime.EventHub;
import com.hengtongan.computerstore.infrastructure.persistence.DBConnection;
import com.hengtongan.computerstore.util.web.ErrorHandler;
import com.hengtongan.computerstore.util.validation.ValidationUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductService {

    private final ProductRepository productDAO;

    public ProductService() {
        this(new ProductRepository());
    }

    public ProductService(ProductRepository productDAO) {
        this.productDAO = productDAO;
    }

    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        return productDAO.search(search, categoryIds, brandIds, minPrice, maxPrice, sort);
    }

    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort, int limit) {
        return productDAO.search(search, categoryIds, brandIds, minPrice, maxPrice, sort, limit);
    }

    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort,
                                int limit, int offset) {
        return productDAO.search(search, categoryIds, brandIds, minPrice, maxPrice, sort, limit, offset);
    }

    public long countSearch(String search, List<Integer> categoryIds, List<Integer> brandIds,
                            BigDecimal minPrice, BigDecimal maxPrice) {
        return productDAO.countSearch(search, categoryIds, brandIds, minPrice, maxPrice);
    }

    public Map<Integer, Long> countByCategory(String search, List<Integer> brandIds,
                                              BigDecimal minPrice, BigDecimal maxPrice) {
        return productDAO.countByCategory(search, brandIds, minPrice, maxPrice);
    }

    public Map<Integer, Long> countByBrand(String search, List<Integer> categoryIds,
                                           BigDecimal minPrice, BigDecimal maxPrice) {
        return productDAO.countByBrand(search, categoryIds, minPrice, maxPrice);
    }

    public List<Product> findTrending(int limit) {
        return productDAO.findTrending(limit);
    }

    public List<Product> getAll() {
        if (CacheManager.isCacheEnabled()) {
            Object memo = CacheManager.getOrLoadProduct(CacheManager.PRODUCTS_ALL_KEY,
                    k -> productDAO.findAll());
            @SuppressWarnings("unchecked")
            List<Product> products = (List<Product>) memo;
            return products;
        }
        return productDAO.findAll();
    }

    public Product get(int productId) {
        Product product = productDAO.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product does not exist.");
        }
        return product;
    }

    public int create(Integer categoryId, Integer brandId, String name, String sku,
                      String description, BigDecimal price, int stockQuantity, String imageUrl) {
        return create(categoryId, brandId, name, sku, description, price, stockQuantity,
                imageUrl, null, null, null, null, List.of());
    }

    // Backward compatibility method for calls without image URL
    public int create(Integer categoryId, Integer brandId, String name, String sku,
                      String description, BigDecimal price, int stockQuantity) {
        return create(categoryId, brandId, name, sku, description, price, stockQuantity, null);
    }

    public int create(Integer categoryId, Integer brandId, String name, String sku,
                      String description, BigDecimal price, int stockQuantity, String imageUrl,
                      String highlights, String boxContents, String warrantyInfo, String sourceUrl,
                      List<ProductSpec> specs) {
        Product product = validate(0, categoryId, brandId, name, sku, description, price, stockQuantity);
        product.setStockQuantity(stockQuantity);
        product.setImageUrl(imageUrl);
        applyDetails(product, highlights, boxContents, warrantyInfo, sourceUrl);
        product.setStatus(Product.computeStatus(stockQuantity));

        // Product row + its specification list commit atomically: a failure in
        // the specs must not leave a stranded product (or vice versa).
        int productId;
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                productId = productDAO.create(c, product);
                productDAO.saveSpecs(c, productId, validateSpecs(specs, productId));
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("creating product", e);
        }

        EventHub.publishStock(productId, stockQuantity, product.getStatus().name());
        CacheManager.invalidateAllCategories(); // product counts in sidebar
        CacheManager.invalidateAllDashboard();  // totals / stock counters changed
        CacheManager.invalidateAllCatalog();    // list pages read fresh after the write
        CacheManager.invalidateProductList();
        CacheManager.invalidateProductDetail(productId);
        return productId;
    }

    public void update(int productId, Integer categoryId, Integer brandId, String name,
                       String sku, String description, BigDecimal price, int stockQuantity, String imageUrl) {
        update(productId, categoryId, brandId, name, sku, description, price, stockQuantity,
                imageUrl, null, null, null, null, List.of());
    }

    // Backward compatibility method for calls without image URL
    public void update(int productId, Integer categoryId, Integer brandId, String name,
                       String sku, String description, BigDecimal price, int stockQuantity) {
        Product current = get(productId);
        update(productId, categoryId, brandId, name, sku, description, price, stockQuantity,
                current.getImageUrl(), null, null, null, null, List.of());
    }

    public void update(int productId, Integer categoryId, Integer brandId, String name,
                       String sku, String description, BigDecimal price, int stockQuantity, String imageUrl,
                       String highlights, String boxContents, String warrantyInfo, String sourceUrl,
                       List<ProductSpec> specs) {
        Product current = get(productId);
        Product product = validate(productId, categoryId, brandId, name, sku, description, price, stockQuantity);
        product.setProductId(productId);
        product.setStockQuantity(stockQuantity);
        product.setImageUrl(imageUrl);
        applyDetails(product, highlights, boxContents, warrantyInfo, sourceUrl);
        // Preserve an explicit DISCONTINUED flag when stock itself is untouched,
        // otherwise recompute from stock quantity.
        if (current.getStatus() == Product.Status.DISCONTINUED
                && current.getStockQuantity() == stockQuantity) {
            product.setStatus(Product.Status.DISCONTINUED);
        } else {
            product.setStatus(Product.computeStatus(stockQuantity));
        }

        // Row update + spec replacement commit atomically (see create()).
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                productDAO.update(c, product);
                productDAO.saveSpecs(c, productId, validateSpecs(specs, productId));
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw ErrorHandler.handleDatabaseError("updating product", e);
        }

        EventHub.publishStock(productId, stockQuantity, product.getStatus().name());
        CacheManager.invalidateAllCategories(); // product counts in sidebar
        CacheManager.invalidateAllDashboard();  // stock counters may have changed
        CacheManager.invalidateAllCatalog();    // list pages read fresh after the write
        CacheManager.invalidateProductList();
        CacheManager.invalidateProductDetail(productId);
    }

    public void updateImage(int productId, String imageUrl) {
        Product product = get(productId);
        product.setImageUrl(imageUrl);
        productDAO.update(product);
        CacheManager.invalidateAllCatalog(); // card images on list pages
        CacheManager.invalidateProductList();
        CacheManager.invalidateProductDetail(productId);
    }

    public List<ProductSpec> getSpecs(int productId) {
        return productDAO.findSpecs(productId);
    }

    private static void applyDetails(Product product, String highlights, String boxContents,
                                     String warrantyInfo, String sourceUrl) {
        String h = trimToNull(highlights);
        String b = trimToNull(boxContents);
        String w = trimToNull(warrantyInfo);
        String s = trimToNull(sourceUrl);
        if (!ValidationUtil.isValidMaxLength(h, 2000)) {
            throw new ValidationException("Highlights must not exceed 2000 characters.");
        }
        if (!ValidationUtil.isValidMaxLength(b, 500)) {
            throw new ValidationException("Box contents must not exceed 500 characters.");
        }
        if (!ValidationUtil.isValidMaxLength(w, 255)) {
            throw new ValidationException("Warranty info must not exceed 255 characters.");
        }
        if (!ValidationUtil.isValidMaxLength(s, 500)) {
            throw new ValidationException("Source URL must not exceed 500 characters.");
        }
        if (s != null && !s.startsWith("http://") && !s.startsWith("https://")) {
            throw new ValidationException("Source URL must start with http:// or https://.");
        }
        product.setHighlights(h);
        product.setBoxContents(b);
        product.setWarrantyInfo(w);
        product.setSourceUrl(s);
    }

    /**
     * Cleans and validates the specification list from the form: blank rows are
     * dropped, duplicate names are de-duplicated (first value wins), and every
     * kept row is within the schema size limits. The returned list is what gets
     * persisted.
     */
    private static List<ProductSpec> validateSpecs(List<ProductSpec> specs, int productId) {
        List<ProductSpec> cleaned = new ArrayList<>();
        if (specs == null || specs.isEmpty()) {
            return cleaned;
        }
        if (specs.size() > 100) {
            throw new ValidationException("A product cannot have more than 100 specifications.");
        }
        Map<String, String> seen = new LinkedHashMap<>();
        int order = 0;
        for (ProductSpec spec : specs) {
            String key = spec.getSpecKey() == null ? "" : spec.getSpecKey().trim();
            String value = spec.getSpecValue() == null ? "" : spec.getSpecValue().trim();
            if (key.isEmpty() && value.isEmpty()) {
                continue;
            }
            if (key.isEmpty()) {
                throw new ValidationException("Every specification needs a name.");
            }
            if (key.length() > 100) {
                throw new ValidationException("Specification names must not exceed 100 characters.");
            }
            if (value.length() > 500) {
                throw new ValidationException("Specification values must not exceed 500 characters.");
            }
            if (seen.putIfAbsent(key.toLowerCase(), key) == null) {
                cleaned.add(new ProductSpec(0, productId, key, value, order++));
            }
        }
        return cleaned;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * A product with order history cannot be deleted; it is marked as
     * DISCONTINUED instead. Returns true when fully deleted.
     */
    public boolean delete(int productId) {
        Product product = get(productId);
        if (productDAO.delete(productId)) {
            EventHub.publishStock(productId, product.getStockQuantity(),
                    Product.Status.DISCONTINUED.name());
            CacheManager.invalidateAllCategories(); // product counts in sidebar
            CacheManager.invalidateAllDashboard();  // product total changed
            CacheManager.invalidateAllCatalog();    // list pages read fresh
            CacheManager.invalidateProductList();
            CacheManager.invalidateProductDetail(productId);
            return true;
        }
        // Referenced by historical orders -> discontinue instead of deleting.
        try (java.sql.Connection c = com.hengtongan.computerstore.infrastructure.persistence.DBConnection.getConnection()) {
            productDAO.markDiscontinued(c, productId);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error discontinuing product", e);
        }
        EventHub.publishStock(productId, product.getStockQuantity(),
                Product.Status.DISCONTINUED.name());
        CacheManager.invalidateAllCategories(); // product counts in sidebar
        CacheManager.invalidateAllDashboard();  // stock counters changed
        CacheManager.invalidateAllCatalog();    // list pages read fresh
        CacheManager.invalidateProductList();
        CacheManager.invalidateProductDetail(productId);
        return false;
    }

    private Product validate(int productId, Integer categoryId, Integer brandId, String name, String sku,
                             String description, BigDecimal price, int stockQuantity) {
        if (ValidationUtil.isBlank(name)) {
            throw new ValidationException("Product name is required.");
        }
        if (ValidationUtil.isBlank(sku)) {
            throw new ValidationException("SKU is required.");
        }
        
        // Add length validation
        if (!ValidationUtil.isValidMaxLength(name, 200)) {
            throw new ValidationException("Product name must not exceed 200 characters.");
        }
        if (!ValidationUtil.isValidMaxLength(sku, 50)) {
            throw new ValidationException("SKU must not exceed 50 characters.");
        }
        if (!ValidationUtil.isValidMaxLength(description, 2000)) {
            throw new ValidationException("Description must not exceed 2000 characters.");
        }
        
        if (categoryId == null) {
            throw new ValidationException("Category is required.");
        }
        if (brandId == null) {
            throw new ValidationException("Brand is required.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Price must be greater than zero.");
        }
        if (stockQuantity < 0) {
            throw new ValidationException("Stock quantity cannot be negative.");
        }
        Product existing = productDAO.findBySku(sku.trim());
        if (existing != null && existing.getProductId() != productId) {
            throw new ValidationException("A product with this SKU already exists.");
        }

        Product product = new Product();
        product.setCategoryId(categoryId);
        product.setBrandId(brandId);
        product.setName(name.trim());
        product.setSku(sku.trim());
        product.setDescription(ValidationUtil.isBlank(description) ? null : description.trim());
        product.setPrice(price);
        return product;
    }
}