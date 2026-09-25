package com.example.computer_store.core.service.impl;

import com.example.computer_store.core.service.CartService;

import com.example.computer_store.core.repository.CartRepository;
import com.example.computer_store.core.repository.ProductRepository;
import com.example.computer_store.core.exception.NotFoundException;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.CartItem;
import com.example.computer_store.core.domain.entity.Product;
import com.example.computer_store.infrastructure.realtime.EventHub;

import java.math.BigDecimal;
import java.util.List;

public class CartServiceImpl implements CartService {

    private final CartRepository cartDAO;
    private final ProductRepository productDAO;

    public CartServiceImpl() {
        this(new CartRepository(), new ProductRepository());
    }

    public CartServiceImpl(CartRepository cartDAO, ProductRepository productDAO) {
        this.cartDAO = cartDAO;
        this.productDAO = productDAO;
    }

    @Override
    public List<CartItem> getCartItems(int userId) {
        return cartDAO.findItemsByUser(userId);
    }

    @Override
    public int countItems(int userId) {
        if (userId <= 0) {
            return 0;
        }
        return cartDAO.countItems(userId);
    }

    @Override
    public BigDecimal getTotal(List<CartItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        if (items == null) {
            return total;
        }
        for (CartItem item : items) {
            if (item.getProduct() != null && item.getProduct().getPrice() != null) {
                total = total.add(item.getProduct().getPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return total;
    }

    @Override
    public void add(int userId, int productId, int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Quantity must be at least 1.");
        }
        Product product = productDAO.findById(productId);
        if (product == null) {
            throw new NotFoundException("Product does not exist.");
        }
        if (product.getStatus() == Product.Status.DISCONTINUED) {
            throw new ValidationException("This product is no longer available.");
        }
        if (product.getStockQuantity() <= 0) {
            throw new ValidationException("This product is out of stock.");
        }

        // Atomic add with stock validation in database
        int result = cartDAO.addItemAtomic(userId, productId, quantity);
        if (result == 0) {
            // Re-check for better error message
            Product p = productDAO.findById(productId);
            if (p == null || p.getStatus() == Product.Status.DISCONTINUED) {
                throw new NotFoundException("Product does not exist or is unavailable.");
            }
            throw new ValidationException("Insufficient stock. Only " + p.getStockQuantity() + " units available.");
        }
        publishCartCount(userId);
    }

    @Override
    public void updateQuantity(int userId, int cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Quantity must be at least 1.");
        }
        CartItem item = cartDAO.findItemsByUser(userId).stream()
                .filter(i -> i.getCartItemId() == cartItemId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cart item does not exist."));
        if (quantity > item.getProduct().getStockQuantity()) {
            throw new ValidationException("Only " + item.getProduct().getStockQuantity()
                    + " units of this product are available.");
        }
        cartDAO.updateQuantity(cartItemId, userId, quantity);
        publishCartCount(userId);
    }

    @Override
    public void remove(int userId, int cartItemId) {
        cartDAO.removeItem(cartItemId, userId);
        publishCartCount(userId);
    }

    @Override
    public void clear(int userId) {
        cartDAO.clear(userId);
        publishCartCount(userId);
    }

    /**
     * Best-effort live badge update. Runs strictly after the database change
     * has been committed, so a temporary database/network hiccup here must
     * never make a successfully persisted cart operation look like a failure.
     */
    private void publishCartCount(int userId) {
        int count;
        try {
            count = countItems(userId);
        } catch (RuntimeException e) {
            return;
        }
        EventHub.publishCart(userId, count);
    }
}
