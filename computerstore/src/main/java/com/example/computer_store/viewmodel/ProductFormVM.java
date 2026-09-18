package com.example.computer_store.viewmodel;

import com.example.computer_store.model.Product;

import java.math.BigDecimal;

/**
 * View data for the admin product create/edit form.
 */
public class ProductFormVM {

    private final Integer productId;
    private final int categoryId;
    private final int brandId;
    private final String name;
    private final String sku;
    private final String description;
    private final BigDecimal price;
    private final int stockQuantity;
    private final String imageUrl;
    private final String currentImageUrl;

    public ProductFormVM(Integer productId, int categoryId, int brandId, String name, String sku,
                         String description, BigDecimal price, int stockQuantity,
                         String imageUrl, String currentImageUrl) {
        this.productId = productId;
        this.categoryId = categoryId;
        this.brandId = brandId;
        this.name = name;
        this.sku = sku;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.imageUrl = imageUrl;
        this.currentImageUrl = currentImageUrl;
    }

    public static ProductFormVM fromProduct(Product product, String contextPath) {
        String current = null;
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            current = contextPath + "/" + product.getImageUrl();
        }
        return new ProductFormVM(
                product.getProductId() > 0 ? product.getProductId() : null,
                product.getCategoryId(),
                product.getBrandId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getImageUrl(),
                current
        );
    }

    public Integer getProductId() {
        return productId;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public int getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCurrentImageUrl() {
        return currentImageUrl;
    }
}
