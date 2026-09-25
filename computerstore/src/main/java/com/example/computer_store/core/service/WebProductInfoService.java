package com.example.computer_store.core.service;

import com.example.computer_store.core.exception.ValidationException;

import java.io.IOException;
import java.net.URI;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Admin tool that pulls official product information from the web so the
 * product form's "auto-fill" panel can pre-fill editable detail fields.
 *
 * <p>Design goals:
 * <ul>
 *   <li><b>No API keys</b> – search uses the DuckDuckGo HTML endpoint and pages
 *       are fetched directly with the JDK {@link HttpClient} (no new dependency).</li>
 *   <li><b>Suggestions only</b> – everything extracted is returned to the admin
 *       to review and edit in the form before saving.</li>
 *   <li><b>Safe by default</b> – only http/https URLs, loopback/private/cloud
 *       link-local addresses are rejected (SSRF guard), response size and
 *       fetching time are capped, and no cookies/headers are forwarded to the
 *       fetched host.</li>
 * </ul>
 */
public class WebProductInfoService {

    public static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    public static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private static final int MAX_RESULTS = 8;
    private static final int MAX_SPECS = 40;
    private static final int MAX_HIGHLIGHTS = 6;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0 Safari/537.36";

    private static final String SEARCH_ENDPOINT = "https://html.duckduckgo.com/html/?q=";

    private static final Pattern RESULT_ANCHOR = Pattern.compile(
            "(?is)<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");
    private static final Pattern RESULT_SNIPPET = Pattern.compile(
            "(?is)<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern DEFINITION_PAIR = Pattern.compile(
            "(?is)<dt[^>]*>(.*?)</dt>\\s*<dd[^>]*>(.*?)</dd>");
    private static final Pattern TABLE_ROW = Pattern.compile("(?is)<tr[^>]*>(.*?)</tr>");
    private static final Pattern TABLE_CELL = Pattern.compile("(?is)<t[dh][^>]*>(.*?)</t[dh]>");
    private static final Pattern KEY_VALUE_LINE = Pattern.compile(
            "(?m)^\\s*([A-Za-z][A-Za-z0-9 /&().,+-]{1,60}?):\\s+(\\S.{2,200})$");
    private static final Pattern LIST_ITEM = Pattern.compile("(?is)<li[^>]*>(.*?)</li>");
    private static final Pattern BOX_PATTERN = Pattern.compile(
            "(?is)(?:what'?s in the box|in the box|box contents?|package contents?|package includes)"
            + "[:\\-\\s]*([^\\n]{5,300})");
    private static final Pattern WARRANTY_PATTERN = Pattern.compile(
            "(?is)warranty[\\s:\\-]{1,3}([^\\n]{5,150})");

    /**
     * Matches a meta description tag in either attribute order and with either
     * {@code name="description"} or {@code property="og:description"}:
     * {@code <meta name="description" content="...">} or
     * {@code <meta content="..." property="og:description">}. The captured
     * description is in whichever group matched.
     */
    private static final Pattern META_DESCRIPTION = Pattern.compile(
            "(?is)<meta[^>]+(?:name|property)=[\"'](?:og:)?description[\"'][^>]*content=[\"']([^\"']*)[\"'][^>]*/?>|"
            + "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*(?:name|property)=[\"'](?:og:)?description[\"']");

    /** Nav/menu-ish strings that frequently show up as false-positive spec keys. */
    private static final Set<String> JUNK_SPEC_KEYS = Set.of(
            "menu", "home", "products", "login", "sign in", "register", "cart", "shop",
            "account", "checkout", "search", "language", "currency", "follow us", "share",
            "subscribe", "cookies", "privacy", "terms", "about us", "read more", "contact us",
            "facebook", "twitter", "instagram", "youtube", "linkedin", "pinterest", "whatsapp",
            "telegram", "help", "faq", "newsletter", "shipping", "returns", "customer service",
            "live chat", "call us", "sitemap", "skip to content", "wishlist", "comparison", "reviews");

    private final HttpClient client;

    public WebProductInfoService() {
        this(HttpClient.newBuilder()
                // Do not follow redirects: each destination would need the
                // same DNS/private-network validation as the initial URL.
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(FETCH_TIMEOUT)
                .build());
    }

    /** Package-private so tests can inject a stub client. */
    WebProductInfoService(HttpClient client) {
        this.client = client;
    }

    /** One web search result (title, destination URL, snippet). */
    public record SearchResult(String title, String url, String snippet) {}

    /** One suggested key/value specification row. */
    public record SpecSuggestion(String key, String value) {}

    /** Suggested details extracted from an official product page. */
    public record ExtractResult(String pageTitle, String description,
                                String manufacturerPart, List<SpecSuggestion> specs,
                                List<String> highlights, String boxContents,
                                String warrantyInfo) {}

    /**
     * Keys that mark a specification as the manufacturer's part number / SKU.
     * The first key found (in priority order) wins for {@code manufacturerPart};
     * the row also stays in {@code specs} so the admin sees it in context.
     */
    private static final List<String> PART_NUMBER_ALIASES = List.of(
            "part number", "part no.", "part #", "model number", "model no.",
            "mpn", "sku", "stock code", "product code", "item number",
            "item no.", "product number", "p/n");

    /**
     * Searches the web (DuckDuckGo HTML endpoint – no API key required).
     *
     * @throws ValidationException when the query is empty/too long or the search
     *         endpoint cannot be reached
     */
    public List<SearchResult> search(String query) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            throw new ValidationException("Search query is required.");
        }
        if (query.trim().length() > 300) {
            throw new ValidationException("Search query must not exceed 300 characters.");
        }
        String html = fetch(SEARCH_ENDPOINT + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));

        List<SearchResult> results = new ArrayList<>();
        Matcher anchor = RESULT_ANCHOR.matcher(html);
        Matcher snippet = RESULT_SNIPPET.matcher(html);
        int cursor = 0;
        while (anchor.find() && results.size() < MAX_RESULTS) {
            String destination = resolveSearchUrl(anchor.group(1));
            String title = cleanText(anchor.group(2));
            if ((destination == null || destination.isEmpty()) && title.isEmpty()) {
                continue;
            }
            String snippetText = "";
            if (snippet.find(Math.max(cursor, anchor.end()))) {
                snippetText = cleanText(snippet.group(1));
                cursor = snippet.end();
            }
            results.add(new SearchResult(title, destination, snippetText));
        }
        return results;
    }

    /**
     * Fetches an official product page and extracts suggested details from it.
     *
     * @throws ValidationException for unsafe/private URLs
     * @throws IOException when the page cannot be fetched or is too large
     */
    public ExtractResult fetchAndExtract(String url) throws IOException {
        String safeUrl = validateFetchUrl(url);
        String html = fetch(safeUrl);
        String text = visibleText(html);
        List<SpecSuggestion> specs = extractSpecs(html, text);
        return new ExtractResult(
                extractTitle(html),
                extractDescription(html),
                extractManufacturerPart(specs),
                specs,
                extractHighlights(html),
                extractBoxContents(text),
                extractWarranty(text));
    }

    /**
     * Downloads a URL body (redirects followed, size and time capped).
     * Overridable so tests can stub responses without network access.
     */
    protected String fetch(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(FETCH_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();
        try {
            HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IOException("The page returned HTTP " + response.statusCode() + ".");
            }
            if (response.statusCode() >= 300) {
                throw new IOException("Redirects are not allowed when fetching product information.");
            }
            try (java.io.InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) {
                    throw new IOException("The page is larger than "
                            + (MAX_BODY_BYTES / 1024 / 1024) + " MB and was not read.");
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The request was interrupted.", e);
        }
    }

    // ------------------------------------------------------------------
    // URL safety
    // ------------------------------------------------------------------

    private static String validateFetchUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new ValidationException("URL is required.");
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new ValidationException("The URL could not be parsed.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ValidationException("Only http:// and https:// URLs are allowed.");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new ValidationException("The URL must include a host name.");
        }
        String hostLower = host.toLowerCase(Locale.ROOT);
        if (hostLower.startsWith("[") && hostLower.endsWith("]")) {
            hostLower = hostLower.substring(1, hostLower.length() - 1);
        }
        if (hostLower.equals("localhost") || hostLower.endsWith(".localhost")
                || hostLower.endsWith(".local") || hostLower.endsWith(".internal")) {
            throw new ValidationException("Local addresses are not allowed.");
        }
        if (isPrivateLiteralAddress(hostLower)) {
            throw new ValidationException("Private network addresses are not allowed.");
        }
        if (Boolean.parseBoolean(System.getProperty("computerstore.webinfo.resolveHosts", "true"))) {
            try {
                for (InetAddress address : InetAddress.getAllByName(hostLower)) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                            || address.isMulticastAddress()) {
                        throw new ValidationException("Private network addresses are not allowed.");
                    }
                }
            } catch (UnknownHostException e) {
                throw new ValidationException("The URL host could not be resolved.");
            }
        }
        return trimmed;
    }

    private static boolean isPrivateLiteralAddress(String hostLower) {
        // IPv6 loopback / link-local / unique-local literals.
        if (hostLower.contains(":") && (hostLower.startsWith("::")
                || hostLower.startsWith("fe80:")
                || hostLower.startsWith("fd")
                || hostLower.startsWith("fc"))) {
            return true;
        }
        if (!hostLower.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] parts = hostLower.split("\\.");
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false; // not a plain dotted-decimal address
            }
            if (octets[i] > 255) {
                return false;
            }
        }
        return octets[0] == 0
                || octets[0] == 10
                || octets[0] == 127
                || (octets[0] == 169 && octets[1] == 254)
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127);
    }

    /** Resolves a DuckDuckGo result link to the real destination URL. */
    private static String resolveSearchUrl(String rawHref) {
        if (rawHref == null || rawHref.isEmpty()) {
            return null;
        }
        String href = rawHref.trim();
        try {
            if (href.contains("uddg=")) {
                int q = href.indexOf('?');
                String query = q >= 0 ? href.substring(q + 1) : "";
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0 && "uddg".equals(pair.substring(0, eq))) {
                        String decoded = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                        if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                            return decoded;
                        }
                    }
                }
            }
            if (href.startsWith("//")) {
                return "https:" + href;
            }
            if (href.startsWith("/")) {
                return "https://duckduckgo.com" + href;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return href;
    }

    // ------------------------------------------------------------------
    // Extraction heuristics (best-effort – admin reviews every result)
    // ------------------------------------------------------------------

    private static String extractTitle(String html) {
        Matcher m = TITLE.matcher(html);
        String title = m.find() ? cleanText(m.group(1)) : "";
        return title.length() > 200 ? title.substring(0, 200) : title;
    }

    /**
     * Best-effort product description: the page's meta description
     * ({@code name="description"}) or Open Graph description, whichever comes
     * first. Many manufacturer pages keep the short product blurb there.
     */
    private static String extractDescription(String html) {
        Matcher m = META_DESCRIPTION.matcher(html);
        if (!m.find()) {
            return null;
        }
        String description = cleanText(m.group(1) != null ? m.group(1) : m.group(2));
        if (description.isEmpty()) {
            return null;
        }
        return description.length() > 500 ? description.substring(0, 500) + "…" : description;
    }

    /**
     * Finds the manufacturer part number / SKU among the extracted specs. Keys
     * are matched against {@link #PART_NUMBER_ALIASES} (e.g. "Part Number",
     * "Model No.", "MPN"); the highest-priority match wins. Returns {@code null}
     * when the page carries no recognizable part number.
     */
    private static String extractManufacturerPart(List<SpecSuggestion> specs) {
        for (String alias : PART_NUMBER_ALIASES) {
            for (SpecSuggestion spec : specs) {
                String key = spec.key().toLowerCase(Locale.ROOT);
                if (key.contains(alias)) {
                    return spec.value().length() > 100
                            ? spec.value().substring(0, 100) : spec.value();
                }
            }
        }
        return null;
    }

    private static List<SpecSuggestion> extractSpecs(String html, String text) {
        Map<String, SpecSuggestion> specs = new LinkedHashMap<>();

        Matcher dl = DEFINITION_PAIR.matcher(html);
        while (dl.find() && specs.size() < MAX_SPECS) {
            addSpec(specs, cleanText(dl.group(1)), cleanText(dl.group(2)));
        }

        Matcher rows = TABLE_ROW.matcher(html);
        while (rows.find() && specs.size() < MAX_SPECS) {
            List<String> cols = new ArrayList<>();
            Matcher cells = TABLE_CELL.matcher(rows.group(1));
            while (cells.find() && cols.size() < 4) {
                cols.add(cleanText(cells.group(1)));
            }
            if (cols.size() == 2) {
                addSpec(specs, cols.get(0), cols.get(1));
            }
        }

        Matcher lines = KEY_VALUE_LINE.matcher(text);
        while (lines.find() && specs.size() < MAX_SPECS) {
            addSpec(specs, lines.group(1).trim(), lines.group(2).trim());
        }

        return new ArrayList<>(specs.values());
    }

    private static void addSpec(Map<String, SpecSuggestion> specs, String rawKey, String rawValue) {
        if (rawKey == null || rawKey.isEmpty() || rawValue == null || rawValue.isEmpty()) {
            return;
        }
        String key = rawKey.length() > 100 ? rawKey.substring(0, 100).trim() : rawKey.trim();
        String value = rawValue.length() > 500 ? rawValue.substring(0, 500).trim() : rawValue.trim();
        String keyLower = key.toLowerCase(Locale.ROOT);
        // "What's in the box: ..." reads as a key/value line in plain text; it is
        // surfaced as a dedicated field instead, so drop the mangled key here.
        if (keyLower.contains(" in the box") || keyLower.startsWith("what")
                || JUNK_SPEC_KEYS.contains(keyLower)) {
            return;
        }
        specs.putIfAbsent(keyLower, new SpecSuggestion(key, value));
    }

    private static List<String> extractHighlights(String html) {
        List<String> highlights = new ArrayList<>();
        Matcher items = LIST_ITEM.matcher(html);
        while (items.find() && highlights.size() < MAX_HIGHLIGHTS) {
            String text = cleanText(items.group(1));
            if (text.length() >= 15 && text.length() <= 180) {
                highlights.add(text);
            }
        }
        return highlights;
    }

    private static String extractBoxContents(String text) {
        Matcher m = BOX_PATTERN.matcher(text);
        if (m.find()) {
            String captured = limit(m.group(1), 500);
            return captured.isEmpty() ? null : captured;
        }
        return null;
    }

    private static String extractWarranty(String text) {
        Matcher m = WARRANTY_PATTERN.matcher(text);
        while (m.find()) {
            String candidate = m.group(1).trim();
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.startsWith("information") || lower.startsWith("terms")
                    || lower.startsWith("request") || lower.startsWith("register")
                    || lower.startsWith("policy") || lower.startsWith("period:")) {
                continue;
            }
            String capped = limit(candidate, 150);
            return capped.isEmpty() ? null : capped;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // HTML -> text helpers
    // ------------------------------------------------------------------

    /** Visible text of a page: scripts/styles removed, block tags become newlines. */
    private static String visibleText(String html) {
        String cleaned = html.replaceAll(
                "(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>|<!--.*?-->", "");
        cleaned = cleaned.replaceAll(
                "(?is)</(p|div|li|tr|h[1-6]|dt|dd|section|article)>|<br\\s*/?>", "\n");
        cleaned = cleaned.replaceAll("(?s)<[^>]+>", " ");
        String decoded = decodeEntities(cleaned);
        return decoded.replaceAll("[ \\t\\r]+", " ")
                .replaceAll("\n[ \\t]+", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private static String cleanText(String html) {
        if (html == null) {
            return "";
        }
        String noTags = html.replaceAll("(?s)<[^>]+>", " ");
        return decodeEntities(noTags).replaceAll("\\s+", " ").trim();
    }

    private static String decodeEntities(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&nbsp;", " ");
    }

    private static String limit(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() <= max) {
            return t;
        }
        String cut = t.substring(0, max);
        int lastSpace = cut.lastIndexOf(' ');
        String shortened = lastSpace > max / 2 ? cut.substring(0, lastSpace) : cut;
        return shortened.trim() + "…";
    }
}
