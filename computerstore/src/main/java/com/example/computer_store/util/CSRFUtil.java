package com.example.computer_store.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

/**
 * CSRF (Cross-Site Request Forgery) protection utilities.
 * Generates and validates CSRF tokens to prevent unauthorized actions on behalf of authenticated users.
 */
public final class CSRFUtil {

    private static final String CSRF_TOKEN_SESSION_KEY = "csrfToken";
    private static final String CSRF_TOKEN_REQUEST_PARAM = "csrfToken";

    private CSRFUtil() {
    }

    /**
     * Generates a new CSRF token and stores it in the session.
     * If a token already exists, it returns the existing one.
     *
     * @param session The HTTP session
     * @return The CSRF token
     */
    public static String generateToken(HttpSession session) {
        String token = (String) session.getAttribute(CSRF_TOKEN_SESSION_KEY);
        if (token == null) {
            token = UUID.randomUUID().toString();
            session.setAttribute(CSRF_TOKEN_SESSION_KEY, token);
        }
        return token;
    }

    /**
     * Validates the CSRF token from the request against the session token.
     *
     * @param request The HTTP request
     * @return true if the token is valid, false otherwise
     */
    public static boolean validateToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }

        String sessionToken = (String) session.getAttribute(CSRF_TOKEN_SESSION_KEY);
        String requestToken = getRequestToken(request);

        return sessionToken != null && sessionToken.equals(requestToken);
    }

    /**
     * Extracts the CSRF token from the incoming request.
     * Handles regular form parameters, the {@code X-CSRF-Token} header used by
     * AJAX calls, and {@code multipart/form-data} uploads where the container
     * does not expose body parts as request parameters until they are parsed.
     */
    private static String getRequestToken(HttpServletRequest request) {
        String requestToken = request.getParameter(CSRF_TOKEN_REQUEST_PARAM);

        // Also check header for AJAX requests
        if (requestToken == null) {
            requestToken = request.getHeader("X-CSRF-Token");
        }

        // For multipart/form-data requests (e.g. product image upload) the token
        // is sent as a form field inside the multipart body, which is only
        // visible through getParts(). Parse the parts to find it.
        if (requestToken == null && request.getContentType() != null
                && request.getContentType().toLowerCase().startsWith("multipart/form-data")) {
            try {
                Collection<Part> parts = request.getParts();
                for (Part part : parts) {
                    if (CSRF_TOKEN_REQUEST_PARAM.equals(part.getName())) {
                        try (InputStream is = part.getInputStream()) {
                            requestToken = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        break;
                    }
                }
            } catch (IOException | jakarta.servlet.ServletException e) {
                // Token cannot be read; validation will fail safely.
            }
        }

        return requestToken;
    }

    /**
     * Clears the CSRF token from the session (e.g., after logout).
     *
     * @param session The HTTP session
     */
    public static void clearToken(HttpSession session) {
        session.removeAttribute(CSRF_TOKEN_SESSION_KEY);
    }

    /**
     * Gets the CSRF token from the session without generating a new one.
     *
     * @param session The HTTP session
     * @return The CSRF token or null if not present
     */
    public static String getToken(HttpSession session) {
        return (String) session.getAttribute(CSRF_TOKEN_SESSION_KEY);
    }

    /**
     * Gets the request parameter name for CSRF token.
     *
     * @return The parameter name
     */
    public static String getTokenParamName() {
        return CSRF_TOKEN_REQUEST_PARAM;
    }
}