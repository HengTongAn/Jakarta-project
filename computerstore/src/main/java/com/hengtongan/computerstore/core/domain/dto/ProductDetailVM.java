package com.hengtongan.computerstore.core.domain.dto;

import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.domain.entity.ProductSpec;

import java.math.BigDecimal;
import java.util.List;

/**
 * View data for the customer product detail page.
 */
public class ProductDetailVM {

    private final int productId;
    private final int categoryId;
    private final String name;
    private final String sku;
    private final String description;
    private final String brandName;
    /**
     * Kept alongside {@link #brandName} because the detail page links the brand
     * to {@code /products?brand=<id>}: EL resolves {@code product.brandId} off
     * this view model, and a missing property is a runtime
     * {@code PropertyNotFoundException}, not a compile error.
     */
    private final int brandId;
    private final String categoryName;
    private final BigDecimal price;
    private final int stockQuantity;
    private final Product.Status status;
    private final String imageUrl;
    private final List<String> highlights;
    private final List<ProductSpec> specs;
    private final String boxContents;
    private final String warrantyInfo;
    private final String sourceUrl;

    public ProductDetailVM(int productId, int categoryId, String name, String sku, String description,
                           int brandId, String brandName, String categoryName, BigDecimal price, int stockQuantity,
                           Product.Status status, String imageUrl) {
        this(productId, categoryId, name, sku, description, brandId, brandName, categoryName, price,
                stockQuantity, status, imageUrl, List.of(), List.of(), null, null, null);
    }

    public ProductDetailVM(int productId, int categoryId, String name, String sku, String description,
                           int brandId, String brandName, String categoryName, BigDecimal price, int stockQuantity,
                           Product.Status status, String imageUrl, List<String> highlights,
                           List<ProductSpec> specs, String boxContents, String warrantyInfo, String sourceUrl) {
        this.productId = productId;
        this.categoryId = categoryId;
        this.name = name;
        this.sku = sku;
        this.description = description;
        this.brandId = brandId;
        this.brandName = brandName;
        this.categoryName = categoryName;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.imageUrl = imageUrl;
        this.highlights = highlights == null ? List.of() : highlights;
        this.specs = specs == null ? List.of() : specs;
        this.boxContents = boxContents;
        this.warrantyInfo = warrantyInfo;
        this.sourceUrl = sourceUrl;
    }

    public int getProductId() {
        return productId;
    }

    public int getCategoryId() {
        return categoryId;
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

    public String getBrandName() {
        return brandName;
    }

    public int getBrandId() {
        return brandId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public Product.Status getStatus() {
        return status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.trim().isEmpty();
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public List<ProductSpec> getSpecs() {
        return specs;
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
}
