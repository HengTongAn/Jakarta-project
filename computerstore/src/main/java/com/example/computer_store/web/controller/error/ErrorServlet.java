package com.example.computer_store.web.controller.error;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global error handler servlet.
 * Catches all unhandled exceptions and provides user-friendly error pages
 * while logging full stack traces for debugging.
 */
@WebServlet("/error")
public class ErrorServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorServlet.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handleError(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handleError(request, response);
    }

    private void handleError(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        Throwable throwable = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
        String requestUri = (String) request.getAttribute("jakarta.servlet.error.request_uri");
        String servletName = (String) request.getAttribute("jakarta.servlet.error.servlet_name");

        // Log the error with full context
        String correlationId = (String) request.getAttribute("correlationId");
        if (correlationId == null) {
            correlationId = "unknown";
        }

        StringBuilder logMessage = new StringBuilder()
                .append("Error handled: status=").append(statusCode)
                .append(", uri=").append(requestUri)
                .append(", servlet=").append(servletName)
                .append(", correlationId=").append(correlationId);

        if (throwable != null) {
            logMessage.append(", exception=").append(throwable.getClass().getName())
                    .append(": ").append(throwable.getMessage());

            // Full stack trace attached to the same correlation id. SLF4J
            // renders the throwable itself, so no manual printStackTrace is
            // needed (and nothing leaks to stderr).
            LOGGER.debug("Full stack trace for correlationId={}", correlationId, throwable);
        }

        LOGGER.warn(logMessage.toString());

        // Determine user-friendly message
        String userMessage;
        int httpStatus = statusCode != null ? statusCode : 500;

        if (httpStatus == 404) {
            userMessage = "The page you're looking for doesn't exist.";
        } else if (httpStatus == 403) {
            userMessage = "You don't have permission to access this resource.";
        } else if (httpStatus == 400) {
            userMessage = "Invalid request. Please check your input and try again.";
        } else if (httpStatus == 429) {
            userMessage = "Too many requests. Please slow down and try again.";
        } else {
            userMessage = "An unexpected error occurred. Our team has been notified.";
        }

        // Set response status
        response.setStatus(httpStatus);

        // Check if client accepts JSON (AJAX request)
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"" + escapeJson(userMessage) + "\",\"status\":" + httpStatus + "}");
            return;
        }

        // Forward to appropriate JSP error page
        String errorPage;
        if (httpStatus == 404) {
            errorPage = "/WEB-INF/views/errors/404.jsp";
        } else if (httpStatus == 403) {
            errorPage = "/WEB-INF/views/errors/403.jsp";
        } else {
            errorPage = "/WEB-INF/views/errors/500.jsp";
        }

        request.setAttribute("errorMessage", userMessage);
        request.setAttribute("statusCode", httpStatus);
        request.setAttribute("correlationId", correlationId);
        request.getRequestDispatcher(errorPage).forward(request, response);
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
