package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the wiring of {@code StorefrontAccessFilter}, the filter that answers 403 for an admin on
 * the customer storefront.
 *
 * <h2>Why this cannot be covered by a unit test</h2>
 *
 * {@link com.hengtongan.computerstore.web.filter.security.StorefrontAccessFilterTest} proves the
 * rule: which paths count as shopping, and which roles get refused. What it cannot see is whether
 * the container ever runs the filter. That depends entirely on {@code web.xml}, and every way it
 * can go wrong is silent — the filter looks configured in the descriptor, in review diffs, and in
 * the filter's own javadoc, while the storefront stays open to admins:
 *
 * <ul>
 *   <li><b>No mapping at all.</b> The filter never runs for any path. The rule is unit-tested and
 *       correct and unreachable.</li>
 *   <li><b>A pattern too narrow.</b> {@code /products} mapped but not {@code /products/*}, so the
 *       catalogue detail page and review submission stay open even though the catalogue index is
 *       refused.</li>
 *   <li><b>The mapping placed before {@code AuthenticationFilter}.</b> Filter order is mapping
 *       order. The cart, checkout, payment and account paths are all behind
 *       {@code AuthenticationFilter}, so a filter ordered first would answer an anonymous visitor
 *       with "you may not be an administrator" instead of redirecting them to login — a confusing
 *       403 where a redirect belongs, and it hands out the existence of the admin panel to anyone
 *       who types the URL.</li>
 *   <li><b>No {@code <async-supported>}.b> The SSE endpoint matches {@code /*} and traverses this
 *       chain, so a filter missing the element fails every {@code /realtime} request with "a filter
 *       or servlet of the current chain does not support asynchronous operations". That is the
 *       exact failure {@code AsyncSupportDescriptorTest} exists for, and it has already happened
 *       twice in this codebase.</li>
 * </ul>
 *
 * <p>None of these show up in the filter's own unit tests, so the descriptor is read here.
 *
 * <h2>Deliberately not checked</h2>
 *
 * The filter's rule is a Java data structure and is covered by unit tests against the real
 * filter. This test does not re-derive that rule from the source text: a second implementation of
 * the path table in a test can agree with a broken filter just as easily as it agrees with a
 * correct one. What is asserted is only what the descriptor and the fragments must supply.
 */
class StorefrontAccessFilterDescriptorTest {

    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");

    private static final String FILTER = "StorefrontAccessFilter";

    /** Named in the filter's own url-pattern list, and the ones the rule depends on. */
    private static final List<String> REQUIRED_PATTERNS = List.of(
            "/products", "/products/*",
            "/cart", "/cart/*",
            "/checkout",
            "/payment/aba", "/payment/aba/*",
            "/payment/card", "/payment/card/*",
            "/account", "/account/*");

    /** The filter this one is ordered against. */
    private static final String AUTH_FILTER = "AuthenticationFilter";

    private static final String HEADER = "src/main/webapp/WEB-INF/views/layouts/header.jspf";
    private static final String FOOTER = "src/main/webapp/WEB-INF/views/layouts/footer.jspf";
    private static final String REVIEW_403 = "src/main/webapp/WEB-INF/views/errors/403.jsp";
    private static final String ADMIN_PRODUCTS = "src/main/webapp/WEB-INF/views/admin/products/list.jsp";
    private static final String ADMIN_REVIEWS = "src/main/webapp/WEB-INF/views/admin/reviews.jsp";

    private static String descriptor() throws IOException {
        return Files.readString(WEB_XML);
    }

    // ------------------------------------------------------------------
    // The descriptor: is the filter actually wired to run?
    // ------------------------------------------------------------------

    @Test
    void theFilterIsMappedInTheDescriptor() throws IOException {
        assertTrue(descriptor().contains("<filter-name>" + FILTER + "</filter-name>"),
                FILTER + " is not declared in web.xml. Its rule is correct and unreachable, so "
                        + "the storefront stays open to admins while the filter reads as configured.");
    }

    @Test
    void everyStorefrontPathIsRoutedThroughTheFilter() throws IOException {
        String web = descriptor();
        List<String> missing = new ArrayList<>();
        String mapping = filterMappingOf(web, FILTER);
        for (String pattern : REQUIRED_PATTERNS) {
            if (!declaresPattern(mapping, pattern)) {
                missing.add(pattern);
            }
        }

        assertTrue(missing.isEmpty(),
                () -> "web.xml does not route these through " + FILTER + ": " + missing
                        + ". The filter only runs for the patterns it is mapped to, so an unmapped "
                        + "storefront path stays reachable by an admin however it is spelled. The "
                        + "exact-match patterns are load-bearing on their own: /cart answers "
                        + "without a trailing slash, and /account does not match /account/*.");

        // The other direction. A pattern mapped here that is not in the list above is a path the
        // container routes through this filter on the strength of an assumption in the descriptor
        // alone, with the filter deciding nothing about it. That is how /products/* ends up in
        // web.xml while isShoppingPath was never taught about it.
        List<String> extra = new ArrayList<>(patternsIn(web, FILTER));
        extra.removeAll(REQUIRED_PATTERNS);
        assertTrue(extra.isEmpty(),
                () -> FILTER + "'s mapping declares url-patterns the required set does not "
                        + "include: " + extra + ". Add the path to isShoppingPath and to the "
                        + "required list above, or drop the pattern; do not leave the decision to "
                        + "the descriptor.");
    }

    @Test
    void theFilterRunsAfterAuthentication() throws IOException {
        String web = descriptor();

        int auth = mappingPositionOf(web, AUTH_FILTER);
        int storefront = mappingPositionOf(web, FILTER);

        assertTrue(auth >= 0,
                AUTH_FILTER + " is no longer mapped. " + FILTER + " is ordered relative to it; if it "
                        + "was renamed or removed, an anonymous visitor to /cart or /checkout may "
                        + "get the admin refusal instead of a redirect to login.");
        assertTrue(storefront > auth,
                "The " + FILTER + " mapping must come after " + AUTH_FILTER + " in web.xml. Filter "
                        + "order is the order of the mappings, and the cart/checkout/payment/account "
                        + "paths are already behind " + AUTH_FILTER + ", so a filter placed first "
                        + "answers anonymous and non-admin requests with a 403 about being an "
                        + "administrator instead of redirecting them to login. " + FILTER + " at "
                        + "mapping " + storefront + ", " + AUTH_FILTER + " at " + auth + ".");
    }

    @Test
    void theFilterDeclaresAsyncSupportOnBothElements() throws IOException {
        // Tomcat reads async-supported off <filter> (FilterDef) while the spec puts it on
        // <filter-mapping>. /realtime matches /* and traverses this chain, so a missing element on
        // either is a 500 on every SSE connection.
        String web = descriptor();

        assertTrue(declaresAsyncOnFilterElement(web, FILTER),
                FILTER + "'s <filter> element is missing <async-supported>true</async-supported>. "
                        + "Tomcat builds a FilterDef from <filter> and checks that flag, so every "
                        + "/realtime request fails once this filter is in the chain.");
        assertTrue(declaresAsyncOnMappingElement(web, FILTER),
                FILTER + "'s <filter-mapping> is missing <async-supported>true</async-supported>. "
                        + "The Servlet spec puts the element there, so other containers will refuse "
                        + "startAsync() through this chain.");
    }

    @Test
    void aMappingFlagOnTheWrongElementIsReported() throws IOException {
        // Two occurrences in the wrong places is what a copy-paste of the <filter> block into the
        // mapping looks like, and a naive count would accept it. Each element is asserted on its own.
        String bothFlagsOnTheMapping = """
                <filter>
                  <filter-name>StorefrontAccessFilter</filter-name>
                </filter>
                <filter-mapping>
                  <filter-name>StorefrontAccessFilter</filter-name>
                  <url-pattern>/cart</url-pattern>
                  <async-supported>true</async-supported>
                  <async-supported>true</async-supported>
                </filter-mapping>
                """;
        assertFalse(declaresAsyncOnFilterElement(bothFlagsOnTheMapping, FILTER),
                "flags on the mapping must not satisfy the <filter> element: Tomcat reads FilterDef, "
                        + "which is built from <filter>");
        assertTrue(declaresAsyncOnMappingElement(bothFlagsOnTheMapping, FILTER),
                "this fixture has the mapping flag in place, so the mapping half must stay silent");
    }

    // ------------------------------------------------------------------
    // The fragments: no admin-visible link points at a refused page.
    // ------------------------------------------------------------------

    @Test
    void theRefusalPageDistinguishesTheTwoKindsOf403() throws IOException {
        String view = Files.readString(Path.of(REVIEW_403));

        assertTrue(view.contains("deniedFromAdminPanel"),
                REVIEW_403 + " no longer reads the deniedFromAdminPanel attribute. Two different "
                        + "filters forward here (AdminAuthorizationFilter and StorefrontAccessFilter) "
                        + "and they need different wording and a different way back.");
        assertTrue(view.contains("/admin"),
                REVIEW_403 + " offers no route back to the admin panel. An admin refused by "
                        + FILTER + " would otherwise be left with \"Back to store\", which is the "
                        + "page that just refused them.");
    }

    @Test
    void adminViewsDoNotLinkOutToTheStorefront() throws IOException {
        // Both of these used to be links into /products, which is now a 403 for an admin: a link
        // on the admin product list and in review moderation that opens a refusal page.
        //
        // Checked as an href rather than as the string "/products?id=" because a JSP comment
        // explaining why the link was removed still mentions the URL, and matching the bare string
        // would then fail on the very file that documents the fix. The tag form is what an
        // <a> actually renders, so it cannot be satisfied by prose.
        Pattern storefrontHref = Pattern.compile("<a\\b[^>]*href\\s*=\\s*\"[^\"]*contextPath\\}/products\\?");
        for (String path : List.of(ADMIN_PRODUCTS, ADMIN_REVIEWS)) {
            String view = Files.readString(Path.of(path));
            assertFalse(storefrontHref.matcher(view).find(),
                    path + " still has an <a> pointing at the public product page. " + FILTER
                            + " refuses it for an admin, so this link opens a 403 in a new tab. "
                            + "Render the product name as text instead.");
        }
    }

    @Test
    void theStorefrontNavIsNotGuardedByPathAlone() throws IOException {
        // The specific regression, kept separate from AdminDashboardNavTrimTest's check of the
        // exact guard string: header.jspf still keys the Admin button's *active state* on
        // fn:startsWith(_sp, '/admin'), which is correct and must survive. What must not come
        // back is using that test to decide whether an admin can see the Shop and Cart buttons,
        // because those pages are no longer reachable for an admin.
        String header = Files.readString(Path.of(HEADER));

        for (String marker : List.of("nav-shop-btn", "nav-cart-btn")) {
            assertTrue(guardBefore(header, marker).contains("sessionScope.user.isAdmin()"),
                    "The element carrying " + marker + " is not guarded by "
                            + "sessionScope.user.isAdmin(). It must be keyed on the role, not on the "
                            + "current path: an admin standing on a customer page is exactly the case "
                            + "a path test misses, and it is now a page that answers 403.");
        }
    }

    /** The {@code <c:if test="...">} immediately governing {@code marker}, or "" when unguarded. */
    private static String guardBefore(String source, String marker) {
        int item = source.indexOf(marker);
        if (item < 0) {
            return "";
        }
        int guardOpen = source.lastIndexOf("<c:if test=\"", item);
        if (guardOpen < 0) {
            return "";
        }
        int guardClose = source.indexOf("\">", guardOpen);
        // An intervening </c:if> means that guard has already closed and belongs to an
        // earlier element, so the marker is rendered unconditionally.
        if (source.substring(guardClose, item).contains("</c:if>")) {
            return "";
        }
        return source.substring(guardOpen + "<c:if test=\"".length(), guardClose);
    }

    // ------------------------------------------------------------------
    // Self-test: prove the pattern check can actually fail.
    // A lint that cannot fail is not a lint.
    // ------------------------------------------------------------------

    @Test
    void anUnmappedPatternIsReported() {
        String mappingMissingProducts = """
                <filter-mapping>
                  <filter-name>StorefrontAccessFilter</filter-name>
                  <url-pattern>/cart</url-pattern>
                  <url-pattern>/cart/*</url-pattern>
                </filter-mapping>
                """;
        assertFalse(declaresPattern(mappingMissingProducts, "/products"),
                "this fixture only means something while /products really is missing: it must be "
                        + "reported as an unmapped storefront path");
        assertTrue(declaresPattern(mappingMissingProducts, "/cart"),
                "the patterns the fixture does declare must stay silent, otherwise it does not prove "
                        + "which half of the check it is testing");
    }

    @Test
    void aFilterWithNoMappingCoversNothing() {
        // The whole-feature failure: the filter is declared and unit-tested, but never mapped, so
        // it never runs. An empty mapping block must report every required pattern as missing
        // rather than matching everything.
        assertFalse(declaresPattern("", "/products"),
                "an empty mapping block must not count as covering a pattern");
        assertTrue(filterMappingOf("""
                <filter>
                  <filter-name>StorefrontAccessFilter</filter-name>
                </filter>
                """, FILTER).isEmpty(),
                "a declared filter with no <filter-mapping> must yield an empty mapping block");
    }

    @Test
    void aPatternBelongingToAnotherFilterIsNotCounted() {
        // The failure this guards: /products is in the descriptor, but mapped to a different
        // filter, so StorefrontAccessFilter never runs for it and the catalogue stays open. The
        // lookup has to be scoped to this filter's own block, not to the whole file.
        String descriptorWithSomeoneElsesProducts = """
                <filter-mapping>
                  <filter-name>AuthenticationFilter</filter-name>
                  <url-pattern>/products</url-pattern>
                  <async-supported>true</async-supported>
                </filter-mapping>
                """;
        assertTrue(descriptorWithSomeoneElsesProducts.contains("<url-pattern>/products</url-pattern>"),
                "this fixture only means something while /products really is in the descriptor: "
                        + "the pattern has to be present for a different filter to claim it");
        assertFalse(declaresPattern(filterMappingOf(descriptorWithSomeoneElsesProducts, FILTER), "/products"),
                "a url-pattern routed through another filter must not be reported as covered");
    }

    @Test
    void theFilterElementIsNotMistakenForItsMapping() {
        // Both elements carry <filter-name>. If the declaration satisfied the mapping checks, an
        // unmapped filter would read as fully wired, and the order check would compare a
        // declaration against a mapping and get an answer that means nothing.
        String declaredButNeverMapped = """
                <filter>
                  <filter-name>StorefrontAccessFilter</filter-name>
                  <filter-class>com.example.StorefrontAccessFilter</filter-class>
                  <async-supported>true</async-supported>
                </filter>
                """;
        assertTrue(filterMappingOf(declaredButNeverMapped, FILTER).isEmpty(),
                FILTER + " is declared but never mapped: filterMappingOf must not return the "
                        + "<filter> element, or the filter reads as wired when it never runs");
        assertTrue(mappingPositionOf(declaredButNeverMapped, FILTER) < 0,
                "mappingPositionOf must be -1 for a declared-but-unmapped filter, so the ordering "
                        + "check reports it instead of reading position zero as correctly placed");
    }

    @Test
    void aDeclarationAfterTheMappingIsNotConfusedForIt() throws IOException {
        // The converse: a filter whose <filter> element is written after its <filter-mapping>
        // (legal, just unusual). Matching the first <filter-name> occurrence would find the
        // mapping first and then hunt past its own </filter> for a second one, concluding the
        // filter is unmapped and sending CI to a red that is not real.
        String mappingBeforeDeclaration = """
                <filter-mapping>
                  <filter-name>StorefrontAccessFilter</filter-name>
                  <url-pattern>/cart</url-pattern>
                  <async-supported>true</async-supported>
                </filter-mapping>
                <filter>
                  <filter-name>StorefrontAccessFilter</filter-name>
                  <filter-class>com.example.StorefrontAccessFilter</filter-class>
                  <async-supported>true</async-supported>
                </filter>
                """;
        assertTrue(declaresPattern(filterMappingOf(mappingBeforeDeclaration, FILTER), "/cart"),
                FILTER + " is mapped before it is declared; that is legal and must not read as "
                        + "unmapped");
    }

    // ------------------------------------------------------------------
    // Helpers. Text rather than DOM on purpose: these checks are about which
    // url-pattern string sits inside which <filter-mapping>, and the
    // surrounding descriptor prose discusses filters by name.
    // ------------------------------------------------------------------

    /** True when {@code mappingBlock} declares {@code pattern} as one of its url-patterns. */
    private static boolean declaresPattern(String mappingBlock, String pattern) {
        return !mappingBlock.isEmpty()
                && mappingBlock.contains("<url-pattern>" + pattern + "</url-pattern>");
    }

    /**
     * The text of {@code filterName}'s own {@code <filter-mapping>} block, inner content only.
     * <p>
     * Matching the block rather than the whole file is what makes the {@code isMapped} check mean
     * anything: a url-pattern belonging to some other filter is not evidence that this one is
     * mapped to it. Returns "" when the filter has no mapping at all, which every caller treats
     * as unmapped rather than as "everything is covered".
     */
    private static String filterMappingOf(String web, String filterName) {
        int mappingOpen = mappingPositionOf(web, filterName);
        if (mappingOpen < 0) {
            return "";
        }
        int end = web.indexOf("</filter-mapping>", mappingOpen);
        return end < 0 ? "" : web.substring(mappingOpen, end);
    }

    /**
     * Index of {@code filterName}'s {@code <filter-mapping>} opener, i.e. the position that
     * determines filter order. Returns -1 when the filter has no mapping at all, which every
     * caller treats as a failure rather than as "position zero" -- an absent filter ordered first
     * would otherwise read as correctly placed.
     *
     * <p>Both {@code <filter>} and {@code <filter-mapping>} carry a {@code <filter-name>}, and
     * the same filter legitimately has one of each. The element a given occurrence belongs to is
     * settled by which opener is nearer on its left, so every occurrence is tested rather than
     * assuming the declaration comes first.
     */
    private static int mappingPositionOf(String web, String filterName) {
        String marker = "<filter-name>" + filterName + "</filter-name>";
        for (int from = 0; ; ) {
            int name = web.indexOf(marker, from);
            if (name < 0) {
                return -1;
            }
            int mappingOpen = web.lastIndexOf("<filter-mapping>", name);
            int filterOpen = web.lastIndexOf("<filter>", name);
            if (mappingOpen > filterOpen) {
                return mappingOpen;
            }
            from = name + marker.length();
        }
    }

    /** Every url-pattern in {@code filterName}'s mapping, in declaration order. */
    private static List<String> patternsIn(String web, String filterName) {
        List<String> found = new ArrayList<>();
        Matcher m = Pattern.compile("<url-pattern>([^<]+)</url-pattern>")
                .matcher(filterMappingOf(web, filterName));
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    /**
     * The {@code <async-supported>} flag on {@code filterName}'s own {@code <filter>} element.
     * <p>
     * Separate from the mapping because Tomcat reads the flag there ({@code FilterDef}) while the
     * spec puts it on the mapping, so the two are asserted independently rather than by counting
     * occurrences in the file — two occurrences in the wrong place is the exact defect this guards,
     * and a count would pass it.
     */
    private static boolean declaresAsyncOnFilterElement(String web, String filterName) {
        String marker = "<filter-name>" + filterName + "</filter-name>";
        for (int from = 0; ; ) {
            int name = web.indexOf(marker, from);
            if (name < 0) {
                return false;
            }
            int filterOpen = web.lastIndexOf("<filter>", name);
            int mappingOpen = web.lastIndexOf("<filter-mapping>", name);
            if (filterOpen > mappingOpen) {
                int blockEnd = web.indexOf("</filter>", name);
                if (blockEnd > name) {
                    return web.substring(filterOpen, blockEnd).contains("<async-supported>true</async-supported>");
                }
            }
            from = name + marker.length();
        }
    }

    /** {@code <async-supported>true</async-supported>} inside {@code filterName}'s mapping block. */
    private static boolean declaresAsyncOnMappingElement(String web, String filterName) {
        String mapping = filterMappingOf(web, filterName);
        return !mapping.isEmpty() && mapping.contains("<async-supported>true</async-supported>");
    }
}
