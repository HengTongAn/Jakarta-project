package com.example.computer_store.service.impl;

import com.example.computer_store.service.CartService;

import com.example.computer_store.dao.CartDAO;
import com.example.computer_store.dao.ProductDAO;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.CartItem;
import com.example.computer_store.model.Product;

import java.math.BigDecimal;
import java.util.List;

public class CartServiceImpl implements CartService {

    private final CartDAO cartDAO;
    private final ProductDAO productDAO;

    public CartServiceImpl() {
        this(new CartDAO(), new ProductDAO());
    }

    public CartServiceImpl(CartDAO cartDAO, ProductDAO productDAO) {
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

        CartItem existing = cartDAO.findItem(userId, productId);
        int target = (existing == null ? 0 : existing.getQuantity()) + quantity;
        if (target > product.getStockQuantity()) {
            throw new ValidationException("Only " + product.getStockQuantity()
                    + " units of this product are available.");
        }
        cartDAO.saveItem(userId, productId, target);
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
    }

    @Override
    public void remove(int userId, int cartItemId) {
        cartDAO.removeItem(cartItemId, userId);
    }

    @Override
    public void clear(int userId) {
        cartDAO.clear(userId);
    }
}