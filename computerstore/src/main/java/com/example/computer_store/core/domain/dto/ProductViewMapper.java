package com.example.computer_store.core.domain.dto;

import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.core.domain.entity.ProductSpec;
import com.example.computer_store.core.domain.entity.RatingSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProductViewMapper {

    private ProductViewMapper() {
    }

    public static ProductCardVM toCard(Product product) {
        return toCard(product, null);
    }

    public static ProductCardVM toCard(Product product, RatingSummary summary) {
        return new ProductCardVM(
                product.getProductId(),
                product.getName(),
                product.getBrandName(),
                product.getCategoryName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStatus(),
                product.getImageUrl(),
                summary == null ? 0.0 : summary.average(),
                summary == null ? 0 : summary.count()
        );
    }

    public static List<ProductCardVM> toCards(List<Product> products) {
        return toCards(products, Map.of());
    }

    /** Maps products to cards, attaching each product's rating summary (avg + count). */
    public static List<ProductCardVM> toCards(List<Product> products, Map<Integer, RatingSummary> summaries) {
        List<ProductCardVM> cards = new ArrayList<>();
        if (products == null) {
            return cards;
        }
        for (Product product : products) {
            cards.add(toCard(product, summaries.get(product.getProductId())));
        }
        return cards;
    }

    public static ProductDetailVM toDetail(Product product) {
        return toDetail(product, List.of());
    }

    public static ProductDetailVM toDetail(Product product, List<ProductSpec> specs) {
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
                product.getImageUrl(),
                toHighlightList(product.getHighlights()),
                specs,
                product.getBoxContents(),
                product.getWarrantyInfo(),
                product.getSourceUrl()
        );
    }

    /** "One highlight per line" (TEXT column) -> bullet list for the page. */
    private static List<String> toHighlightList(String highlights) {
        List<String> result = new ArrayList<>();
        if (highlights == null) {
            return result;
        }
        for (String line : highlights.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
