package com.example.computer_store.viewmodel;

import com.example.computer_store.model.Product;

import java.math.BigDecimal;

/**
 * View data for catalogue cards (list + trending).
 */
public class ProductCardVM {

    private final int productId;
    private final String name;
    private final String brandName;
    private final String categoryName;
    private final BigDecimal price;
    private final int stockQuantity;
    private final Product.Status status;
    private final String imageUrl;

    public ProductCardVM(int productId, String name, String brandName, String categoryName,
                         BigDecimal price, int stockQuantity, Product.Status status, String imageUrl) {
        this.productId = productId;
        this.name = name;
        this.brandName = brandName;
        this.categoryName = categoryName;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.imageUrl = imageUrl;
    }

    public int getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public String getBrandName() {
        return brandName;
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
}
