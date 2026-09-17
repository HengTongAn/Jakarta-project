package com.example.computer_store.service;

import com.example.computer_store.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ProductService {

    List<Product> search(String search, List<Integer> categoryIds, List<Integer> brandIds,
                         BigDecimal minPrice, BigDecimal maxPrice, String sort);

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

    void update(int productId, Integer categoryId, Integer brandId, String name,
                String sku, String description, BigDecimal price, int stockQuantity, String imageUrl);

    void update(int productId, Integer categoryId, Integer brandId, String name,
                String sku, String description, BigDecimal price, int stockQuantity);

    void updateImage(int productId, String imageUrl);

    boolean delete(int productId);
}
