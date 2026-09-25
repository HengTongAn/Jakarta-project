package com.hengtongan.computerstore;

import com.hengtongan.computerstore.core.domain.entity.CartItem;
import com.hengtongan.computerstore.core.domain.entity.Product;
import com.hengtongan.computerstore.core.service.CartService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CartServiceTest {

    private final CartService cartService = new CartService();

    @Test
    void getTotalSumsLineItems() {
        Product a = new Product();
        a.setPrice(new BigDecimal("10.00"));
        Product b = new Product();
        b.setPrice(new BigDecimal("5.50"));

        CartItem itemA = new CartItem();
        itemA.setProduct(a);
        itemA.setQuantity(2);

        CartItem itemB = new CartItem();
        itemB.setProduct(b);
        itemB.setQuantity(1);

        assertEquals(new BigDecimal("25.50"), cartService.getTotal(List.of(itemA, itemB)));
    }

    @Test
    void getTotalHandlesNullList() {
        assertEquals(BigDecimal.ZERO, cartService.getTotal(null));
    }
}
