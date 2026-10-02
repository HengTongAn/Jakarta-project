package com.hengtongan.computerstore.infrastructure.monitoring;

/**
 * Reduces a User-Agent string to a browser family and a device class.
 *
 * <h2>Why not store the User-Agent</h2>
 *
 * A raw User-Agent is a fingerprint: combined with IP and timing it is routinely
 * used to single out an individual, and it is unbounded, so every crawler, every
 * bot release and every Chrome point version becomes its own row. Neither is
 * wanted here. The question this feature exists to answer is "is the slowness
 * specific to one browser", and a family answers it.
 *
 * <p>So this returns one of a fixed, small set of labels and nothing else. The
 * exact version, platform build and device model are discarded at the boundary and
 * never reach the database.</p>
 *
 * <h2>Ordering matters</h2>
 *
 * Every Chromium browser also claims to be Safari, and Edge claims to be Chrome.
 * The checks are therefore ordered most-specific-first and return on first match,
 * so an Edge request is reported as Edge rather than as whichever browser it
 * impersonates.
 */
public final class UserAgentClassifier {

    /** Unknown is a real bucket, not a failure: bots and old clients land here. */
    public static final String UNKNOWN = "Other";
    public static final String UNKNOWN_DEVICE = "unknown";

    private UserAgentClassifier() {
    }

    /**
     * The browser family, one of: Edge, Opera, Chrome, Firefox, Safari, Samsung,
     * IE, {@link #UNKNOWN}.
     *
     * @param userAgent the raw header, may be null or blank
     */
    public static String browser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent;

        // Chromium derivatives first: each of these also matches the checks below.
        if (contains(ua, "Edg/") || contains(ua, "Edge/") || contains(ua, "EdgA/") || contains(ua, "EdgiOS/")) {
            return "Edge";
        }
        if (contains(ua, "OPR/") || contains(ua, "Opera")) {
            return "Opera";
        }
        if (contains(ua, "SamsungBrowser")) {
            return "Samsung";
        }
        if (contains(ua, "Firefox/") || contains(ua, "FxiOS")) {
            return "Firefox";
        }
        if (contains(ua, "Chrome/") || contains(ua, "CriOS")) {
            return "Chrome";
        }
        if (contains(ua, "Trident/") || contains(ua, "MSIE")) {
            return "IE";
        }
        // Safari is last among the real browsers: every Chromium UA carries
        // "Safari/537.36", and it is only a Safari if nothing above claimed it.
        if (contains(ua, "Safari/") && contains(ua, "Version/")) {
            return "Safari";
        }
        return UNKNOWN;
    }

    /** Coarse device class: mobile, tablet, or desktop. Never a model name. */
    public static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN_DEVICE;
        }
        String ua = userAgent;
        // Parenthesised rather than relying on && binding tighter than ||, which is what
        // makes this line correct but easy to break by editing one clause.
        if (contains(ua, "iPad") || contains(ua, "Tablet")
                || (contains(ua, "Android") && !contains(ua, "Mobile"))) {
            // Android without "Mobile" is the conventional tablet signal; phone UAs
            // carry "Mobile Safari" and fall through to the check below.
            return "tablet";
        }
        if (contains(ua, "Mobi") || contains(ua, "iPhone") || contains(ua, "iPod")
                || contains(ua, "Windows Phone") || contains(ua, "BlackBerry")) {
            return "mobile";
        }
        return "desktop";
    }

    /**
     * Whether a request looks like an automated client rather than a browser.
     *
     * <p>Bots do not run the timing script, so they contribute only server-side
     * numbers and would drag the server figures toward zero while adding nothing
     * to the client ones. They are excluded from the whole feature instead of
     * being averaged in as a fast outlier.</p>
     */
    public static boolean isAutomatedClient(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            // No UA at all is overwhelmingly a script or a health check.
            return true;
        }
        String ua = userAgent;
        return contains(ua, "bot") || contains(ua, "Bot") || contains(ua, "crawler")
                || contains(ua, "spider") || contains(ua, "curl/") || contains(ua, "wget/")
                || contains(ua, "python-requests") || contains(ua, "Java/")
                || contains(ua, "okhttp") || contains(ua, "HeadlessChrome")
                || contains(ua, "monitoring") || contains(ua, "Uptime");
    }

    private static boolean contains(String haystack, String needle) {
        return haystack.contains(needle);
    }
}
