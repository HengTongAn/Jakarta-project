package com.example.computer_store.service.impl;

import com.example.computer_store.service.ProductService;

import com.example.computer_store.dao.ProductDAO;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.Product;
import com.example.computer_store.util.ValidationUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class ProductServiceImpl implements ProductService {

    private final ProductDAO productDAO;

    public ProductServiceImpl() {
        this(new ProductDAO());
    }

    public ProductServiceImpl(ProductDAO productDAO) {
        this.productDAO = productDAO;
    }

    @Override
    public List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                                BigDecimal minPrice, BigDecimal maxPrice, String sort) {
        return productDAO.search(search, categoryIds, brandIds, minPrice, maxPrice, sort);
    }

    @Override
    public Map<Integer, Long> countByCategory(String search, List<Integer> brandIds,
                                              BigDecimal minPrice, BigDecimal maxPrice) {
        return productDAO.countByCategory(search, brandIds, minPrice, maxPrice);
    }

    @Override
    public Map<Integer, Long> countByBrand(String search, List<Integer> categoryIds,
                                           BigDecimal minPrice, BigDecimal maxPrice) {
        return productDAO.countByBrand(search, categoryIds, minPrice, maxPrice);
    }

    @Override
    public List<Product> findTrending(int limit) {
        return productDAO.findTrending(limit);
    }

    @Override
    public List<Product> getAll() {
        return productDAO.findAll();
    }

    @Override
    public Product get(int productId) {
        Product product = productDAO.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product does not exist.");
        }
        return product;
    }

    @Override
    public int create(Integer categoryId, Integer brandId, String name, String sku,
                      String description, BigDecimal price, int stockQuantity, String imageUrl) {
        Product product = validate(0, categoryId, brandId, name, sku, description, price, stockQuantity);
        product.setStockQuantity(stockQuantity);
        product.setImageUrl(imageUrl);
        product.setStatus(Product.computeStatus(stockQuantity));
        return productDAO.create(product);
    }

    // Backward compatibility method for calls without image URL
    @Override
    public int create(Integer categoryId, Integer brandId, String name, String sku,
                      String description, BigDecimal price, int stockQuantity) {
        return create(categoryId, brandId, name, sku, description, price, stockQuantity, null);
    }

    @Override
    public void update(int productId, Integer categoryId, Integer brandId, String name,
                       String sku, String description, BigDecimal price, int stockQuantity, String imageUrl) {
        Product current = get(productId);
        Product product = validate(productId, categoryId, brandId, name, sku, description, price, stockQuantity);
        product.setProductId(productId);
        product.setStockQuantity(stockQuantity);
        product.setImageUrl(imageUrl);
        // Preserve an explicit DISCONTINUED flag when stock itself is untouched,
        // otherwise recompute from stock quantity.
        if (current.getStatus() == Product.Status.DISCONTINUED
                && current.getStockQuantity() == stockQuantity) {
            product.setStatus(Product.Status.DISCONTINUED);
        } else {
            product.setStatus(Product.computeStatus(stockQuantity));
        }
        productDAO.update(product);
    }

    // Backward compatibility method for calls without image URL
    @Override
    public void update(int productId, Integer categoryId, Integer brandId, String name,
                       String sku, String description, BigDecimal price, int stockQuantity) {
        Product current = get(productId);
        update(productId, categoryId, brandId, name, sku, description, price, stockQuantity, current.getImageUrl());
    }

    @Override
    public void updateImage(int productId, String imageUrl) {
        Product product = get(productId);
        product.setImageUrl(imageUrl);
        productDAO.update(product);
    }

    /**
     * A product with order history cannot be deleted; it is marked as
     * DISCONTINUED instead. Returns true when fully deleted.
     */
    @Override
    public boolean delete(int productId) {
        Product product = get(productId);
        if (productDAO.delete(productId)) {
            return true;
        }
        // Referenced by historical orders -> discontinue instead of deleting.
        try (java.sql.Connection c = com.example.computer_store.util.DBConnection.getConnection()) {
            productDAO.markDiscontinued(c, productId);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Error discontinuing product", e);
        }
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