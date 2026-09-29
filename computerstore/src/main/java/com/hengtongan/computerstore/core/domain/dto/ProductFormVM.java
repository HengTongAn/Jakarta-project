package com.hengtongan.computerstore.core.domain.dto;

import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.ProductSpec;

import java.math.BigDecimal;
import java.util.List;

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
    private final String highlights;
    private final String boxContents;
    private final String warrantyInfo;
    private final String sourceUrl;
    private final List<ProductSpec> specs;

    public ProductFormVM(Integer productId, int categoryId, int brandId, String name, String sku,
                         String description, BigDecimal price, int stockQuantity,
                         String imageUrl, String currentImageUrl) {
        this(productId, categoryId, brandId, name, sku, description, price, stockQuantity,
                imageUrl, currentImageUrl, null, null, null, null, List.of());
    }

    public ProductFormVM(Integer productId, int categoryId, int brandId, String name, String sku,
                         String description, BigDecimal price, int stockQuantity,
                         String imageUrl, String currentImageUrl,
                         String highlights, String boxContents, String warrantyInfo, String sourceUrl,
                         List<ProductSpec> specs) {
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
        this.highlights = highlights;
        this.boxContents = boxContents;
        this.warrantyInfo = warrantyInfo;
        this.sourceUrl = sourceUrl;
        this.specs = specs == null ? List.of() : specs;
    }

    public static ProductFormVM fromProduct(Product product, String contextPath) {
        return fromProduct(product, contextPath, List.of());
    }

    public static ProductFormVM fromProduct(Product product, String contextPath, List<ProductSpec> specs) {
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
                current,
                product.getHighlights(),
                product.getBoxContents(),
                product.getWarrantyInfo(),
                product.getSourceUrl(),
                specs
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

    public String getHighlights() {
        return highlights;
    }

    public String getBoxContents() {
        return boxContents;
    }

    public String getWarrantyInfo() {
        return warrantyInfo;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public List<ProductSpec> getSpecs() {
        return specs;
    }
}
