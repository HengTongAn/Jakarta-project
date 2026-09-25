package com.example.computer_store.core.exception;

/**
 * Thrown when an order cannot be completed because the requested quantity
 * exceeds the available stock.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}