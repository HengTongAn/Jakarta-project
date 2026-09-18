package com.example.computer_store.exception;

/**
 * Thrown when a requested resource (product, order, category, ...) does not
 * exist or is not accessible to the current user.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}