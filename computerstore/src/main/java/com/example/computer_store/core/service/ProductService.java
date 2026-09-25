package com.example.computer_store.core.service;

import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.core.domain.entity.ProductSpec;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ProductService {

    List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                         BigDecimal minPrice, BigDecimal maxPrice, String sort);

    /**
     * Search with limit.
     */
    List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                         BigDecimal minPrice, BigDecimal maxPrice, String sort, int limit);

    /**
     * Search with pagination.
     */
    List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                         BigDecimal minPrice, BigDecimal maxPrice, String sort,
                         int limit, int offset);

    /**
     * Count total matching products for pagination.
     */
    long countSearch(String search, List<Integer> categoryIds, List<Integer> brandIds,
                     BigDecimal minPrice, BigDecimal maxPrice);

    Map<Integer, Long> countByCategory(String search, List<Integer> brandIds,
                                       BigDecimal minPrice, BigDecimal maxPrice);

    Map<Integer, Long> countByBrand(String search, List<Integer> categoryIds,
                                    BigDecimal minPrice, BigDecimal maxPrice);

    List<Product> findTrending(int limit);

    List<Product> getAll();

    Product get(int productId);

    int create(Integer categoryId, Integer brandId, String name, String sku,
               String description, BigDecimal price, int stockQuantity, String imageUrl);

    int create(Integer categoryId, Integer brandId, String name, String sku,
               String description, BigDecimal price, int stockQuantity);

    /**
     * Create a product including its rich details and key/value specifications.
     */
    int create(Integer categoryId, Integer brandId, String name, String sku,
               String description, BigDecimal price, int stockQuantity, String imageUrl,
               String highlights, String boxContents, String warrantyInfo, String sourceUrl,
               List<ProductSpec> specs);

    void update(int productId, Integer categoryId, Integer brandId, String name,
                String sku, String description, BigDecimal price, int stockQuantity, String imageUrl);

    void update(int productId, Integer categoryId, Integer brandId, String name,
                String sku, String description, BigDecimal price, int stockQuantity);

    /**
     * Update a product including its rich details and key/value specifications.
     */
    void update(int productId, Integer categoryId, Integer brandId, String name,
                String sku, String description, BigDecimal price, int stockQuantity, String imageUrl,
                String highlights, String boxContents, String warrantyInfo, String sourceUrl,
                List<ProductSpec> specs);

    void updateImage(int productId, String imageUrl);

    /** Key/value specifications for the given product, in display order. */
    List<ProductSpec> getSpecs(int productId);

    boolean delete(int productId);
}
