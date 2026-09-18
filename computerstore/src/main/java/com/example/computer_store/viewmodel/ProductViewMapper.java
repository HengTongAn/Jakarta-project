package com.example.computer_store.viewmodel;

import com.example.computer_store.model.Product;

import java.util.ArrayList;
import java.util.List;

public final class ProductViewMapper {

    private ProductViewMapper() {
    }

    public static ProductCardVM toCard(Product product) {
        return new ProductCardVM(
                product.getProductId(),
                product.getName(),
                product.getBrandName(),
                product.getCategoryName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStatus(),
                product.getImageUrl()
        );
    }

    public static List<ProductCardVM> toCards(List<Product> products) {
        List<ProductCardVM> cards = new ArrayList<>();
        if (products == null) {
            return cards;
        }
        for (Product product : products) {
            cards.add(toCard(product));
        }
        return cards;
    }

    public static ProductDetailVM toDetail(Product product) {
        return new ProductDetailVM(
                product.getProductId(),
                product.getCategoryId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getBrandName(),
                product.getCategoryName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStatus(),
                product.getImageUrl()
        );
    }
}
