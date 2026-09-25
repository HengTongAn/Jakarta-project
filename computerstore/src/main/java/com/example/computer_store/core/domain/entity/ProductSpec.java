package com.example.computer_store.core.domain.entity;

/**
 * One key/value specification row for a product (e.g. "Display" -> "15.6\" QHD 165Hz").
 * Stored in the {@code product_specs} table and rendered as a table on the
 * customer product page.
 */
public class ProductSpec {

    private int specId;
    private int productId;
    private String specKey;
    private String specValue;
    private int sortOrder;

    public ProductSpec() {
    }

    public ProductSpec(int specId, int productId, String specKey, String specValue, int sortOrder) {
        this.specId = specId;
        this.productId = productId;
        this.specKey = specKey;
        this.specValue = specValue;
        this.sortOrder = sortOrder;
    }

    public int getSpecId() {
        return specId;
    }

    public void setSpecId(int specId) {
        this.specId = specId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getSpecKey() {
        return specKey;
    }

    public void setSpecKey(String specKey) {
        this.specKey = specKey;
    }

    public String getSpecValue() {
        return specValue;
    }

    public void setSpecValue(String specValue) {
        this.specValue = specValue;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}