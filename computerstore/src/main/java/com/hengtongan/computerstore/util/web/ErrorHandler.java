package com.hengtongan.computerstore.util.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized error handling utility.
 * Provides generic error messages for users while logging detailed errors for administrators.
 */
public final class ErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {
    }

    /**
     * Handles database errors by logging details and returning a generic message.
     *
     * @param operation The operation that failed (e.g., "finding user", "creating product")
     * @param e The exception that occurred
     * @return RuntimeException with generic message
     */
    public static RuntimeException handleDatabaseError(String operation, Exception e) {
        LOGGER.error("Database error during {}", operation, e);
        return new RuntimeException("An error occurred while processing your request. Please try again later.");
    }

    /**
     * Handles validation errors with specific user-facing messages.
     *
     * @param message The user-facing validation message
     * @return RuntimeException with the validation message
     */
    public static RuntimeException handleValidationError(String message) {
        LOGGER.warn("Validation error: {}", message);
        return new RuntimeException(message);
    }

    /**
     * Handles not found errors with generic message.
     *
     * @param resource The resource type (e.g., "product", "order")
     * @return RuntimeException with generic message
     */
    public static RuntimeException handleNotFoundError(String resource) {
        LOGGER.warn("Resource not found: {}", resource);
        return new RuntimeException("The requested " + resource + " could not be found.");
    }

    /**
     * Handles unauthorized access errors.
     *
     * @return RuntimeException with unauthorized message
     */
    public static RuntimeException handleUnauthorizedError() {
        LOGGER.warn("Unauthorized access attempt");
        return new RuntimeException("You are not authorized to perform this action.");
    }

    /**
     * Handles generic errors with logging.
     *
     * @param operation The operation that failed
     * @param e The exception that occurred
     * @return RuntimeException with generic message
     */
    public static RuntimeException handleGenericError(String operation, Exception e) {
        LOGGER.error("Error during {}", operation, e);
        return new RuntimeException("An unexpected error occurred. Please try again later.");
    }
}