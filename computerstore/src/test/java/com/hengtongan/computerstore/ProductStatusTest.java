package com.hengtongan.computerstore;

import com.hengtongan.computerstore.core.domain.entity.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductStatusTest {

    @Test
    void zeroStockMeansOutOfStock() {
        assertEquals(Product.Status.OUT_OF_STOCK, Product.computeStatus(0));
    }

    @Test
    void lowStockBoundaryIsFive() {
        assertEquals(Product.Status.LOW_STOCK, Product.computeStatus(5));
        assertEquals(Product.Status.IN_STOCK, Product.computeStatus(6));
    }

    @Test
    void healthyStockMeansInStock() {
        assertEquals(Product.Status.IN_STOCK, Product.computeStatus(100));
    }
}