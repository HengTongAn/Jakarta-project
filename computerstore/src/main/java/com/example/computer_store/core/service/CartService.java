package com.example.computer_store.core.service;

import com.example.computer_store.core.domain.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;

public interface CartService {

    List<CartItem> getCartItems(int userId);

    int countItems(int userId);

    BigDecimal getTotal(List<CartItem> items);

    void add(int userId, int productId, int quantity);

    void updateQuantity(int userId, int cartItemId, int quantity);

    void remove(int userId, int cartItemId);

    void clear(int userId);
}
