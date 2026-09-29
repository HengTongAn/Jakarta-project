package com.hengtongan.computerstore.util.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * One-shot messages shown after a redirect (Post/Redirect/Get pattern).
 */
public final class Flash {

    private Flash() {
    }

    public static void success(HttpServletRequest request, String message) {
	store(request, "flashMessage", message, "success");
    }

    public static void error(HttpServletRequest request, String message) {
	store(request, "flashMessage", message, "danger");
    }

    public static void warning(HttpServletRequest request, String message) {
	store(request, "flashMessage", message, "warning");
    }

    private static void store(HttpServletRequest request, String messageKey, String message, String type) {
	request.getSession().setAttribute(messageKey, message);
	request.getSession().setAttribute("flashType", type);
    }
}
