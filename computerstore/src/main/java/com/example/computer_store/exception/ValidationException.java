package com.example.computer_store.exception;

/**
 * Thrown when user-supplied input fails business validation.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}