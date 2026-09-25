package com.example.computer_store.core.domain.entity;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Product {

    public enum Status {
        IN_STOCK, LOW_STOCK, OUT_OF_STOCK, DISCONTINUED
    }

    public static final int LOW_STOCK_THRESHOLD = 5;

    private int productId;
    private int categoryId;
    private int brandId;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private String imageUrl;
    private String highlights;
    private String boxContents;
    private String warrantyInfo;
    private String sourceUrl;
    private Status status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp deletedAt;
    private Integer deletedBy;
    private String deleteReason;

    // Denormalised fields for display (joined from categories / brands)
    private String categoryName;
    private String brandName;

    public Product() {
    }

    public static Status computeStatus(int stockQuantity) {
        if (stockQuantity <= 0) {
            return Status.OUT_OF_STOCK;
        }
        if (stockQuantity <= LOW_STOCK_THRESHOLD) {
            return Status.LOW_STOCK;
        }
        return Status.IN_STOCK;
    }

    public boolean isAvailable() {
        return status != Status.DISCONTINUED;
    }

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.trim().isEmpty();
    }

    public String getDisplayImageUrl() {
        return hasImage() ? imageUrl : null;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    public int getBrandId() {
        return brandId;
    }

    public void setBrandId(int brandId) {
        this.brandId = brandId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getHighlights() {
        return highlights;
    }

    public void setHighlights(String highlights) {
        this.highlights = highlights;
    }

    public String getBoxContents() {
        return boxContents;
    }

    public void setBoxContents(String boxContents) {
        this.boxContents = boxContents;
    }

    public String getWarrantyInfo() {
        return warrantyInfo;
    }

    public void setWarrantyInfo(String warrantyInfo) {
        this.warrantyInfo = warrantyInfo;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Timestamp getDeletedAt() {
	return deletedAt;
    }
    public void setDeletedAt(Timestamp deletedAt) {
	this.deletedAt = deletedAt; 
    }
    public Integer getDeletedBy() {
	return deletedBy; 
    }
    public void setDeletedBy(Integer deletedBy) {
	this.deletedBy = deletedBy; 
    }
    public String getDeleteReason() {
	return deleteReason; 
    }
    public void setDeleteReason(String deleteReason) {
	this.deleteReason = deleteReason; 
    }
    public boolean isDeleted() {
	return deletedAt != null; 
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }
}
