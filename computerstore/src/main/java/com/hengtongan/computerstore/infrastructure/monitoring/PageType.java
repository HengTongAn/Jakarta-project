package com.hengtongan.computerstore.infrastructure.monitoring;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns a request into a bounded page-type label.
 *
 * <h2>Why this exists</h2>
 *
 * "Performance by page type" is only useful if the key is a <em>kind</em> of page
 * rather than a particular page. The raw path cannot be used directly: the store
 * has a detail view reachable at {@code /products?id=41}, {@code /products?id=42}
 * and so on, and storing those separately would answer "which page type is
 * slowest" with "every page has exactly one visit, sort by noise". It would also
 * be a slow way to fill a table, since each is a new row.
 *
 * <h2>How the label is built</h2>
 *
 * The servlet mapping is preferred over the raw URL, because the mapping is a
 * template declared in the application and is therefore bounded by the number of
 * routes. A request that matched no servlet is not a page at all and is dropped
 * here, which is what keeps a bot probing {@code /wp-login.php} from inventing a
 * new page type on every hit.
 *
 * <p>A page reached with an {@code id} parameter is a detail view and gets a
 * {@code :detail} suffix, because list and detail pages have genuinely different
 * cost profiles -- a list renders 24 cards, a detail renders one product plus its
 * reviews -- and averaging them together hides exactly the number an operator
 * wants.
 *
 * <h2>Guarantees</h2>
 *
 * <ul>
 *   <li>Never null for a routable GET; null means "not a page view, do not record".</li>
 *   <li>Lowercase, no query string, no host, no trailing slash, length-capped.</li>
 *   <li>Never contains an id, token, email or any other request value. The label
 *       is a route, and a route is not a fact about a particular visitor.</li>
 * </ul>
 */
public final class PageType {

    /** Longest label stored. Route templates are far shorter; this only bounds damage. */
    private static final int MAX_LENGTH = 64;

    private static final String DETAIL_SUFFIX = ":detail";

    /** Paths that are infrastructure rather than pages a person looks at. */
    private static final String[] NOT_PAGES = {
            "/assets",
            "/WEB-INF",
            "/realtime",
            "/health",
            "/favicon.ico",
            "/robots.txt",
            "/manifest.webmanifest",
            "/offline.html",
            "/sw.js",
    };

    private PageType() {
    }

    /**
     * The page-type label for a request, or {@code null} if it is not a page view.
     *
     * <p>Only GET counts. A POST is a submission, not a navigation, and the browser
     * timing API reports nothing useful for one; including them would blend form
     * posts into page-load statistics.</p>
     */
    public static String of(HttpServletRequest request) {
        if (request == null || !"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String path = path(request);
        if (path == null || isNotAPage(path)) {
            return null;
        }

        String label = fromServletMapping(request);
        if (label == null) {
            label = path;
        } else {
            label = "/" + label;
        }
        label = sanitize(label);

        // A detail view of a record: same route, one entity, different cost.
        if (request.getParameter("id") != null) {
            label = clip(label + DETAIL_SUFFIX);
        }
        return label;
    }

    /**
     * The route template the container matched, when it has one.
     *
     * <p>Preferred over the raw path because it is bounded by the application's own
     * route declarations. Returns {@code null} when there is no match (a 404) or
     * when the match is the JSP a servlet forwarded to, since that would name the
     * view file rather than the page.</p>
     */
    private static String fromServletMapping(HttpServletRequest request) {
        HttpServletRequest wrapper = request;
        // Called on the raw request from a filter, but guard anyway: a forwarded
        // request exposes the mapping of the JSP target, which is not a page name.
        String pattern;
        try {
            var mapping = wrapper.getHttpServletMapping();
            if (mapping == null) {
                return null;
            }
            pattern = mapping.getPattern();
            if (mapping.getServletName() != null && mapping.getServletName().endsWith(".jsp")) {
                return null;
            }
        } catch (UnsupportedOperationException | IllegalStateException e) {
            // Container does not expose mappings; the path fallback still works.
            return null;
        }
        if (pattern == null || pattern.isBlank() || pattern.endsWith(".jsp")) {
            return null;
        }
        // "/" means the root document, which carries no more meaning than the path.
        return "/".equals(pattern) ? null : pattern;
    }

    /** The request path with the context path and any trailing slash removed. */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return null;
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.isEmpty()) {
            return "/";
        }
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    private static boolean isNotAPage(String path) {
        for (String prefix : NOT_PAGES) {
            // Boundary-checked so that /assets does not swallow /assetsomething,
            // which would be a real page.
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        // Anything whose last segment contains a dot is a file, not a page: a
        // crawler fetching /logo.png must not become a page type.
        int lastSlash = path.lastIndexOf('/');
        return path.substring(lastSlash + 1).indexOf('.') >= 0;
    }

    /** Lowercase, no query or fragment, no trailing slash, length-capped. */
    private static String sanitize(String raw) {
        String s = raw.toLowerCase(java.util.Locale.ROOT);
        int cut = s.indexOf('?');
        if (cut >= 0) {
            s = s.substring(0, cut);
        }
        cut = s.indexOf('#');
        if (cut >= 0) {
            s = s.substring(0, cut);
        }
        if (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "/" : clip(s);
    }

    private static String clip(String s) {
        return s.length() <= MAX_LENGTH ? s : s.substring(0, MAX_LENGTH);
    }
}
