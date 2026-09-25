package com.example.computer_store.core.service;

import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.service.WebProductInfoService.ExtractResult;
import com.example.computer_store.core.service.WebProductInfoService.SearchResult;
import com.example.computer_store.core.service.WebProductInfoService.SpecSuggestion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic tests for {@link WebProductInfoService}: the network layer is stubbed
 * by overriding {@code fetch}, so nothing in this suite performs real I/O.
 */
class WebProductInfoServiceTest {

    /** Test stand-in that serves fixture HTML for known URLs (never touches the network). */
    private static final class StubService extends WebProductInfoService {
        private final Map<String, String> responses = new HashMap<>();

        StubService with(String url, String html) {
            responses.put(url, html);
            return this;
        }

        @Override
        protected String fetch(String url) throws IOException {
            String body = responses.get(url);
            if (body == null) {
                throw new IOException("No stub response for " + url);
            }
            return body;
        }
    }

    private static final String OFFICIAL_PAGE_URL =
            "https://www.asus.com/laptops/rog-strix-16-2024/specs";

    private static final String SEARCH_FIXTURE =
            "<div class=\"result results_links results_links_deep web-result\">"
            + "<h2 class=\"result__title\"><a rel=\"nofollow\" class=\"result__a\" "
            + "href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.asus.com%2Flaptops%2Frog-strix%2Fspecs&amp;rut=abc123\">"
            + "ASUS ROG Strix G16 &amp; Specs</a></h2>"
            + "<a class=\"result__snippet\" href=\"//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.asus.com%2Flaptops%2Frog-strix%2Fspecs\">"
            + "Official specifications for the ROG Strix G16 gaming laptop.</a>"
            + "</div>";

    private static final String PAGE_FIXTURE =
            "<!DOCTYPE html><html><head><title>ASUS ROG Strix G16 (2024) - Specifications</title>"
            + "<meta name=\"description\" content=\"High-performance 16-inch gaming laptop "
            + "with Intel Core i9 HX processor and RTX graphics.\">"
            + "<script>var tracking = \"ignored\";</script><style>.tweaks{color:red}</style></head>"
            + "<body><nav><a>Home</a><a>Products</a><a>Cart</a></nav>"
            + "<h2>Key Features</h2><ul>"
            + "<li>16-inch QHD+ 165Hz display</li>"
            + "<li>Intel Core i9-14900HX processor</li>"
            + "<li>NVIDIA GeForce RTX 4060 graphics</li>"
            + "<li>Backlit keyboard with per-key RGB</li>"
            + "<li>short</li>"
            + "</ul>"
            + "<dl>"
            + "<dt>Display</dt><dd>16-inch QHD+ 165Hz</dd>"
            + "<dt>Processor</dt><dd>Intel Core i9-14900HX</dd>"
            + "<dt>Memory</dt><dd>32 GB DDR5-5600</dd>"
            + "<dt>Part Number</dt><dd>90NR08R1-M00630</dd>"
            + "<dt>Menu</dt><dd>Home</dd>"
            + "</dl>"
            + "<table><tr><th>Weight</th><td>2.5 kg</td></tr>"
            + "<tr><th>Battery</th><td>90 Wh</td></tr></table>"
            + "<p>What's in the box: Laptop, power adapter, user manual, carrying sleeve.</p>"
            + "<p>Warranty: 2-year international warranty with local service coverage.</p>"
            + "<p>Price: 1899.00 USD</p>"
            + "</body></html>";

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @Test
    void searchParsesDuckDuckGoResultsAndDecodesRedirectUrls() throws IOException {
        StubService service = new StubService()
                .with("https://html.duckduckgo.com/html/?q=rog+strix", SEARCH_FIXTURE);

        List<SearchResult> results = service.search("rog strix");

        assertEquals(1, results.size());
        SearchResult r = results.get(0);
        assertEquals("ASUS ROG Strix G16 & Specs", r.title());
        assertEquals("https://www.asus.com/laptops/rog-strix/specs", r.url());
        assertEquals("Official specifications for the ROG Strix G16 gaming laptop.", r.snippet());
    }

    @Test
    void searchReturnsEmptyListWhenEndpointHasNoResults() throws IOException {
        StubService service = new StubService()
                .with("https://html.duckduckgo.com/html/?q=zzzz", "<html><body>No results.</body></html>");

        List<SearchResult> results = service.search("zzzz");

        assertTrue(results.isEmpty());
    }

    @Test
    void searchRejectsBlankAndOversizedQueries() {
        WebProductInfoService service = new StubService();

        assertThrows(ValidationException.class, () -> service.search(null));
        assertThrows(ValidationException.class, () -> service.search(""));
        assertThrows(ValidationException.class, () -> service.search("   "));
        assertThrows(ValidationException.class, () -> service.search("x".repeat(301)));
    }

    // ------------------------------------------------------------------
    // Extraction
    // ------------------------------------------------------------------

    @Test
    void extractPullsTitleSpecsHighlightsBoxAndWarranty() throws IOException {
        StubService service = new StubService().with(OFFICIAL_PAGE_URL, PAGE_FIXTURE);

        ExtractResult result = service.fetchAndExtract(OFFICIAL_PAGE_URL);

        assertEquals("ASUS ROG Strix G16 (2024) - Specifications", result.pageTitle());
        assertEquals("High-performance 16-inch gaming laptop with Intel Core i9 "
                + "HX processor and RTX graphics.", result.description());
        assertEquals("90NR08R1-M00630", result.manufacturerPart(),
                "the Part Number spec row must be surfaced as the manufacturer part");

        Map<String, String> specs = new HashMap<>();
        for (SpecSuggestion s : result.specs()) {
            specs.put(s.key(), s.value());
        }
        assertEquals("16-inch QHD+ 165Hz", specs.get("Display"));
        assertEquals("Intel Core i9-14900HX", specs.get("Processor"));
        assertEquals("32 GB DDR5-5600", specs.get("Memory"));
        assertEquals("2.5 kg", specs.get("Weight"));
        assertEquals("90 Wh", specs.get("Battery"));
        assertEquals("90NR08R1-M00630", specs.get("Part Number"),
                "the part number must also remain visible as a spec row");
        assertTrue(specs.containsKey("Warranty"), "warranty line should surface as a spec too");

        List<String> highlights = result.highlights();
        assertEquals(4, highlights.size(), "undersized '<li>' items must be ignored");
        assertTrue(highlights.contains("16-inch QHD+ 165Hz display"));

        assertEquals("Laptop, power adapter, user manual, carrying sleeve.", result.boxContents());
        assertEquals("2-year international warranty with local service coverage.", result.warrantyInfo());
    }

    @Test
    void extractSkipsNavAndMangledKeys() throws IOException {
        StubService service = new StubService().with(OFFICIAL_PAGE_URL, PAGE_FIXTURE);

        ExtractResult result = service.fetchAndExtract(OFFICIAL_PAGE_URL);

        boolean hasMenuKey = result.specs().stream()
                .anyMatch(s -> s.key().equalsIgnoreCase("menu"));
        boolean hasMangledBoxKey = result.specs().stream()
                .anyMatch(s -> s.key().toLowerCase().contains("in the box"));

        assertFalse(hasMenuKey, "nav labels must not become specification rows");
        assertFalse(hasMangledBoxKey, "the box-contents phrase must not leak in as a spec key");
    }

    @Test
    void extractReturnsNullDescriptionAndPartWhenThePageHasNone() throws IOException {
        String minimalPage = "<html><head><title>Widget</title></head><body>"
                + "<dl><dt>Weight</dt><dd>1 kg</dd></dl></body></html>";

        ExtractResult result = new StubService()
                .with("https://example.com/minimal", minimalPage)
                .fetchAndExtract("https://example.com/minimal");

        assertEquals("Widget", result.pageTitle());
        assertNull(result.description(), "no meta description -> null, so the form stays untouched");
        assertNull(result.manufacturerPart(), "no part-number key -> null");
    }

    @Test
    void extractReadsMetaDescriptionInReversedAttributeOrder() throws IOException {
        String page = "<html><head><title>Fan</title>"
                + "<meta content=\"Silent cooling fan for desktops.\" property=\"og:description\">"
                + "</head><body><p>Some text.</p></body></html>";

        ExtractResult result = new StubService()
                .with("https://example.com/fan", page)
                .fetchAndExtract("https://example.com/fan");

        assertEquals("Silent cooling fan for desktops.", result.description());
    }

    @Test
    void extractIsCleanedAndCapped() throws IOException {
        StringBuilder bigPage = new StringBuilder("<html><head><title>T</title></head><body>");
        bigPage.append("<dl>");
        for (int i = 0; i < 60; i++) {
            bigPage.append("<dt>Key ").append(i).append("</dt><dd>Value ").append(i).append("</dd>");
        }
        bigPage.append("</dl>");
        bigPage.append("<ul>");
        for (int i = 0; i < 30; i++) {
            bigPage.append("<li>Highlight item number ").append(i).append(" with a full sentence length.</li>");
        }
        bigPage.append("</ul></body></html>");

        ExtractResult result = new StubService()
                .with("https://example.com/big", bigPage.toString())
                .fetchAndExtract("https://example.com/big");

        assertTrue(result.specs().size() <= 40, "spec count must be capped");
        assertTrue(result.highlights().size() <= 6, "highlight count must be capped");
    }

    // ------------------------------------------------------------------
    // SSRF guards
    // ------------------------------------------------------------------

    @Test
    void rejectsLocalAndPrivateUrls() {
        WebProductInfoService service = new StubService();
        for (String url : List.of(
                "http://localhost:8080/x",
                "http://127.0.0.1/x",
                "http://0.0.0.0/x",
                "http://10.1.2.3/x",
                "http://192.168.1.5/x",
                "http://172.16.0.1/x",
                "http://169.254.169.254/latest/meta-data/",
                "http://[::1]/x")) {
            assertThrows(ValidationException.class,
                    () -> service.fetchAndExtract(url), "expected block for " + url);
        }
    }

    @Test
    void rejectsNonHttpSchemesAndMalformedUrls() {
        WebProductInfoService service = new StubService();
        assertThrows(ValidationException.class, () -> service.fetchAndExtract("ftp://files.example.com/specs.pdf"));
        assertThrows(ValidationException.class, () -> service.fetchAndExtract("file:///etc/passwd"));
        assertThrows(ValidationException.class, () -> service.fetchAndExtract("not a url"));
        assertThrows(ValidationException.class, () -> service.fetchAndExtract(""));
        assertThrows(ValidationException.class, () -> service.fetchAndExtract("   "));
    }

    @Test
    void allowsPublicHttpsPage() throws IOException {
        StubService service = new StubService().with(OFFICIAL_PAGE_URL, PAGE_FIXTURE);
        ExtractResult result = service.fetchAndExtract(OFFICIAL_PAGE_URL);
        assertEquals("ASUS ROG Strix G16 (2024) - Specifications", result.pageTitle());
    }
}