package com.hengtongan.computerstore.util.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/** Cheap request classification helpers for the per-request filter chain. */
public final class RequestUtil {

    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            "css", "js", "png", "jpg", "jpeg", "gif", "svg", "webp",
            "ico", "woff", "woff2", "ttf", "eot", "map");

    private RequestUtil() {
    }

    /** True for CSS/JS/fonts/images and the /assets, /uploads, /realtime paths. */
    public static boolean isStaticOrStream(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/assets/") || path.startsWith("/uploads/")
                || path.equals("/realtime")) {
            return true;
        }
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        return dot > slash && STATIC_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase());
    }
}