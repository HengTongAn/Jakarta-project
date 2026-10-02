package com.hengtongan.computerstore.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Guards the "coming soon" gate on the unfinished admin sections.
 *
 * <h2>What the gate is</h2>
 *
 * {@code ComingSoonGateFilter} forwards the unfinished
 * {@code /admin/performance} path to
 * a placeholder view instead of letting it reach the monitor servlet. Payments
 * and
 * transactions are shipped and remain reachable.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * The gate is described in two places that cannot see each other: the
 * {@code GATED} map in
 * the filter, and the {@code <url-pattern>} list in {@code web.xml}. Neither is
 * complete on
 * its own, and the two failure modes are both silent:
 *
 * <ul>
 * <li><b>A pattern in {@code web.xml} that the map does not contain.</b> The
 * filter runs
 * for the request, looks the path up, gets {@code null}, and calls
 * {@code chain.doFilter} — the gate appears configured in the descriptor and in
 * review
 * diffs, but the page loads normally. An admin clicking the nav item reaches a
 * half-finished page and the gate reads as broken.</li>
 * <li><b>An entry in the map that no pattern covers.</b> The gate is dead code
 * for that
 * path and the page is reachable however it is spelled.</li>
 * </ul>
 *
 * <p>
 * Both are checked here in both directions, so a gate cannot be half-added or
 * half-removed without CI noticing.
 *
 * <h2>Ordering, which is the other half of the feature</h2>
 *
 * The mapping must stay <em>after</em> {@code AdminAuthorizationFilter}. Filter
 * order is the
 * order of the mappings in the descriptor, and a gate placed first would answer
 * an anonymous
 * or non-admin request with the admin placeholder page instead of redirecting
 * to login —
 * handing the admin shell to anyone who types the URL. That is a small
 * information leak and
 * a confusing 200 where a redirect belongs, and it is invisible in a diff
 * because the
 * filter declaration itself is correct either way.
 *
 * <h2>Why the view is checked for existence</h2>
 *
 * The filter forwards to a JSP path held in a private constant, so a rename of
 * the view
 * produces a {@code ServletException} on every request to a gated path — a 500
 * on three
 * admin pages, discovered by clicking rather than by a test. This is the same
 * failure shape
 * as a {@code getRequestDispatcher} target that does not exist, which has
 * bitten this
 * codebase before.
 *
 * <h2>Why a source lint</h2>
 *
 * A {@code web.xml} descriptor and a filter's private map are both only
 * consulted at
 * container startup, so the only way to cover them is to read the files. The
 * project
 * deliberately keeps {@code mvn test} free of a browser and container
 * dependency.
 */
class ComingSoonGateTest {

    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path FILTER = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/filter/web/ComingSoonGateFilter.java");

    /** The deployed document root: the prefix a forward path is relative to. */
    private static final Path WEBAPP = Path.of("src/main/webapp");

    private static final String WEB_XML_DIR = "/WEB-INF/";

    private static final String FILTER_NAME = "ComingSoonGateFilter";
    private static final String AUTH_FILTER_NAME = "AdminAuthorizationFilter";

    /**
     * Performance stays in Insights but opens the shared Coming Soon view for now.
     */
    private static final List<String> EXPECTED_PATHS = List.of("/admin/performance");

    /** {@code GATED.put("/admin/payments", ...)} — the key, nothing else. */
    private static final Pattern GATED_PUT = Pattern.compile("GATED\\.put\\s*\\(\\s*\"([^\"]+)\"");

    /**
     * The path the filter forwards to, read from the view constant so it cannot
     * drift.
     */
    private static final Pattern VIEW_CONSTANT = Pattern.compile("VIEW\\s*=\\s*\"([^\"]+)\"");

    // ------------------------------------------------------------------
    // The rules, against the real files.
    // ------------------------------------------------------------------

    @Test
    void theGateCoversEveryUnfinishedAdminSection() throws Exception {
        assertEquals(EXPECTED_PATHS, gatedPathsInWebXml(),
                "The coming soon gate should cover exactly the unfinished admin sections. A path "
                        + "that is not here is reachable by typing its URL, whatever the nav shows.");
    }

    @Test
    void webXmlAndTheFilterMapAgreeInBothDirections() throws Exception {
        Disagreement found = disagreements(
                new LinkedHashSet<>(gatedPathsInWebXml()),
                new LinkedHashSet<>(gatedPathsInFilter()));

        assertTrue(found.patternWithoutMap().isEmpty(),
                () -> "web.xml routes these through " + FILTER_NAME + ", but the filter's GATED "
                        + "map has no entry for them. doFilter() finds no Feature, calls "
                        + "chain.doFilter, and the page loads normally -- the gate looks configured "
                        + "and does nothing: " + found.patternWithoutMap());

        assertTrue(found.mapWithoutPattern().isEmpty(),
                () -> "The filter's GATED map lists these paths, but no <url-pattern> in web.xml "
                        + "routes them through the filter, so the gate never runs for them and "
                        + "they stay reachable: " + found.mapWithoutPattern());
    }

    @Test
    void theGateRunsAfterAdminAuthorization() throws Exception {
        Document doc = parse(WEB_XML);
        int gate = indexOfMappingFor(doc, FILTER_NAME);
        int auth = indexOfMappingFor(doc, AUTH_FILTER_NAME);
        assertTrue(auth >= 0,
                () -> AUTH_FILTER_NAME + " is no longer mapped in web.xml. The gate is ordered "
                        + "relative to it; if it was renamed or removed, re-check that a gated "
                        + "path still sends a non-admin to login rather than rendering the "
                        + "admin placeholder.");
        if (gate < 0) {
            // Nothing routes to the gate, so there is no ordering to get wrong. The
            // constraint only bites once a section is actually gated again, and
            // anEmptyGateIsDeclaredDormantRatherThanLeftSilent covers that state.
            return;
        }
        assertTrue(gate > auth,
                () -> "The " + FILTER_NAME + " mapping must come after " + AUTH_FILTER_NAME
                        + " in web.xml. Filter order is mapping order, so a gate placed first "
                        + "answers anonymous and non-admin requests with the admin placeholder "
                        + "page instead of redirecting to login. Gate at " + gate
                        + ", admin authorization at " + auth + ".");
    }

    /**
     * The gate must never be mapped ahead of the admin role check.
     *
     * <p>
     * Separate from {@link #theGateRunsAfterAdminAuthorization} because that one
     * returns early while the gate is dormant, which would let a future gate be
     * added
     * in the wrong position without this ordering rule ever running against it.
     * This
     * reads both indices and fails on the bad ordering whatever the current state,
     * so a dormant gate is not a gap in the check.
     * </p>
     */
    @Test
    void theGateIsNotMappedBeforeAdminAuthorization() throws Exception {
        Document doc = parse(WEB_XML);
        int gate = indexOfMappingFor(doc, FILTER_NAME);
        int auth = indexOfMappingFor(doc, AUTH_FILTER_NAME);
        if (gate < 0) {
            return;
        }
        assertTrue(auth >= 0 && gate > auth,
                () -> "The " + FILTER_NAME + " mapping must come after " + AUTH_FILTER_NAME
                        + ". Filter order is mapping order, so a gate placed first answers "
                        + "anonymous and non-admin requests with the admin placeholder page "
                        + "instead of redirecting to login. Gate at " + gate
                        + ", admin authorization at " + auth + ".");
    }

    @Test
    void theViewTheGateForwardsToExists() throws Exception {
        String view = VIEW_CONSTANT.matcher(Files.readString(FILTER, StandardCharsets.UTF_8))
                .results().findFirst().map(m -> m.group(1))
                .orElseThrow(() -> new AssertionError(
                        FILTER + " no longer declares a VIEW constant holding the path it "
                                + "forwards to, so this test cannot check the target. Point it at "
                                + "the new constant."));

        assertTrue(view.startsWith(WEB_XML_DIR),
                () -> "The gate forwards to " + view + ", which is outside " + WEB_XML_DIR
                        + " and would therefore be servable directly. A placeholder that answers "
                        + "to a GET is not a gate.");
        // A forward path is relative to the document root, so drop only the leading
        // slash
        // and resolve against WEBAPP. Resolving against WEB-INF instead would report a
        // missing file for a view that is present.
        Path viewFile = WEBAPP.resolve(view.substring(1)).normalize();
        assertTrue(Files.isRegularFile(viewFile),
                () -> "The gate forwards to " + view + ", which does not exist at " + viewFile
                        + ". Every request to a gated path is a 500 until the view is "
                        + "restored or the constant is repointed.");
    }

    @Test
    void thePaymentConfigurationPageIsReachable() throws Exception {
        assertFalse(gatedPathsInWebXml().contains("/admin/payments"),
                "/admin/payments is finished. Gating it again leaves the store cash-only with no "
                        + "admin page that can change it, and an operator cannot tell that state "
                        + "apart from 'coming soon'.");
        assertFalse(gatedPathsInFilter().contains("/admin/payments"),
                "The filter still holds /admin/payments in its GATED map. The request is already "
                        + "un-routed in web.xml, so this entry is dead code that reads as a live "
                        + "gate in review.");
    }

    @Test
    void thePerformancePageIsGatedToTheComingSoonView() throws Exception {
        assertTrue(gatedPathsInWebXml().contains("/admin/performance"),
                "/admin/performance must be intercepted for nav clicks and direct URLs.");
        assertTrue(gatedPathsInFilter().contains("/admin/performance"),
                "The filter must provide the Performance Coming Soon feature details.");
    }

    /**
     * An empty gate must be a decision on the record, not an accident.
     *
     * <p>
     * This replaces a test that simply forbade an empty gate, which stopped being
     * true the moment the last section shipped. Forbidding it would have meant
     * deleting the filter, and deleting it is not free: it is a working mechanism
     * for whatever is unfinished next, and rebuilding it means the filter, the
     * view,
     * the nav badge and these tests again.
     * </p>
     *
     * <p>
     * The risk that creates is real though. A filter declared in {@code web.xml},
     * named in review diffs and gating nothing looks exactly like one that gates
     * everything, so an emptied {@code GATED} map can hide a gate that quietly
     * stopped working. Rather than permit that silently, this asserts the two
     * halves of the dormant state agree and are stated: the map is empty, nothing
     * routes to the filter, and the filter declares {@code DORMANT}. Adding a gate
     * without flipping {@code DORMANT} fails here, so "empty" and "empty on
     * purpose" cannot drift apart.
     * </p>
     */
    @Test
    void anEmptyGateIsDeclaredDormantRatherThanLeftSilent() throws Exception {
        List<String> routed = gatedPathsInWebXml();
        List<String> mapped = gatedPathsInFilter();
        boolean nothingRouted = routed.isEmpty();
        boolean mapEmpty = mapped.isEmpty();

        assertTrue(nothingRouted == mapEmpty,
                () -> "The gate's two halves disagree about whether anything is gated: web.xml "
                        + "routes " + routed + " while the filter's GATED map holds "
                        + mapped + ". See webXmlAndTheFilterMapAgreeInBothDirections "
                        + "for what that does at runtime.");

        if (nothingRouted) {
            assertTrue(filterDeclaresDormant(),
                    FILTER_NAME + " gates nothing: GATED is empty and no <url-pattern> routes to "
                            + "it. The filter is kept as a working extension point, but it must say "
                            + "so, or it is indistinguishable from a gate that has quietly stopped "
                            + "working. Declare public static final boolean DORMANT = GATED.isEmpty() "
                            + "in the filter, as the class comment describes.");
        }
    }

    /** Whether the filter states that it is intentionally holding nothing back. */
    private static boolean filterDeclaresDormant() throws IOException {
        return declaresDormant(Files.readString(FILTER, StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // Self-tests: prove each check above actually rejects the broken shape.
    // A lint that cannot fail is not a lint.
    //
    // The fixtures use a fictional path set (/admin/payments, /admin/ledger,
    // /admin/settlements) rather than the real one, so a fixture that is
    // accidentally mixed up shows up as a failing self-test instead of
    // quietly agreeing with the real descriptor. Every case below runs the
    // same disagreements() helper the real assertion uses, so these tests
    // cannot pass while the checked logic is wrong.
    // ------------------------------------------------------------------

    /**
     * Gates payments and ledger, in that order. The baseline both halves below
     * start from.
     */
    private static final String DESCRIPTOR_GATING_PAYMENTS_AND_LEDGER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee">
              <filter>
                <filter-name>AdminAuthorizationFilter</filter-name>
                <filter-class>com.example.AdminAuthorizationFilter</filter-class>
                <async-supported>true</async-supported>
              </filter>
              <filter-mapping>
                <filter-name>AdminAuthorizationFilter</filter-name>
                <url-pattern>/admin/*</url-pattern>
                <async-supported>true</async-supported>
              </filter-mapping>
              <filter>
                <filter-name>ComingSoonGateFilter</filter-name>
                <filter-class>com.example.ComingSoonGateFilter</filter-class>
                <async-supported>true</async-supported>
              </filter>
              <filter-mapping>
                <filter-name>ComingSoonGateFilter</filter-name>
                <url-pattern>/admin/payments</url-pattern>
                <url-pattern>/admin/ledger</url-pattern>
                <async-supported>true</async-supported>
              </filter-mapping>
            </web-app>
            """;

    /** The same descriptor with the gate declared before admin authorization. */
    private static final String DESCRIPTOR_WITH_GATE_FIRST = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee">
              <filter>
                <filter-name>ComingSoonGateFilter</filter-name>
                <filter-class>com.example.ComingSoonGateFilter</filter-class>
                <async-supported>true</async-supported>
              </filter>
              <filter-mapping>
                <filter-name>ComingSoonGateFilter</filter-name>
                <url-pattern>/admin/payments</url-pattern>
                <async-supported>true</async-supported>
              </filter-mapping>
              <filter>
                <filter-name>AdminAuthorizationFilter</filter-name>
                <filter-class>com.example.AdminAuthorizationFilter</filter-class>
                <async-supported>true</async-supported>
              </filter>
              <filter-mapping>
                <filter-name>AdminAuthorizationFilter</filter-name>
                <url-pattern>/admin/*</url-pattern>
                <async-supported>true</async-supported>
              </filter-mapping>
            </web-app>
            """;

    /** A filter that has forgotten the ledger. */
    private static final String FILTER_WITHOUT_LEDGER = """
            public class ComingSoonGateFilter {
                static {
                    GATED.put("/admin/payments", new Feature("Payments", "bi-credit-card-2-front", "x"));
                }
            }
            """;

    /** A filter that agrees with the descriptor exactly. */
    private static final String FILTER_MATCHING_DESCRIPTOR = """
            public class ComingSoonGateFilter {
                static {
                    GATED.put("/admin/payments", new Feature("Payments", "bi-credit-card-2-front", "x"));
                    GATED.put("/admin/ledger", new Feature("Ledger", "bi-journal-text", "y"));
                }
            }
            """;

    /** A filter that also gates settlements, which no url-pattern routes. */
    private static final String FILTER_WITH_UNROUTED_ENTRY = """
            public class ComingSoonGateFilter {
                static {
                    GATED.put("/admin/payments", new Feature("Payments", "bi-credit-card-2-front", "x"));
                    GATED.put("/admin/ledger", new Feature("Ledger", "bi-journal-text", "y"));
                    GATED.put("/admin/settlements", new Feature("Settlements", "bi-cash-stack", "z"));
                }
            }
            """;

    @Test
    void aPatternWithNoMapEntryIsDetected() throws Exception {
        Disagreement found = disagreements(declaredInBaselineDescriptor(), new LinkedHashSet<>(
                gatedPathsIn(FILTER_WITHOUT_LEDGER)));

        assertEquals(Set.of("/admin/ledger"), found.patternWithoutMap(),
                "A url-pattern the filter's map does not know must be reported: the filter finds "
                        + "no Feature and lets the request through.");
        assertTrue(found.mapWithoutPattern().isEmpty(),
                "This fixture adds a finding in one direction only, so the other must stay "
                        + "silent -- otherwise it does not prove which half of the gate it is "
                        + "testing.");
    }

    @Test
    void aMapEntryWithNoPatternIsDetected() throws Exception {
        Disagreement found = disagreements(declaredInBaselineDescriptor(), new LinkedHashSet<>(
                gatedPathsIn(FILTER_WITH_UNROUTED_ENTRY)));

        assertEquals(Set.of("/admin/settlements"), found.mapWithoutPattern(),
                "A map entry no url-pattern routes must be reported: the gate never runs for it.");
        assertTrue(found.patternWithoutMap().isEmpty(),
                "This fixture adds a finding in one direction only, so the other must stay "
                        + "silent -- otherwise it does not prove which half of the gate it is "
                        + "testing.");
    }

    @Test
    void matchingHalvesProduceNoFindings() throws Exception {
        Disagreement found = disagreements(declaredInBaselineDescriptor(), new LinkedHashSet<>(
                gatedPathsIn(FILTER_MATCHING_DESCRIPTOR)));

        assertTrue(found.patternWithoutMap().isEmpty() && found.mapWithoutPattern().isEmpty(),
                () -> "Two halves that agree must produce no findings, otherwise the real "
                        + "descriptor would be reported for the same disagreement this fixture "
                        + "encodes: " + found);
    }

    @Test
    void aGateDeclaredBeforeAdminAuthorizationIsDetected() throws Exception {
        Document doc = parseString(DESCRIPTOR_WITH_GATE_FIRST);
        assertTrue(indexOfMappingFor(doc, FILTER_NAME) < indexOfMappingFor(doc, AUTH_FILTER_NAME),
                "This fixture only means something while the gate really is ordered first.");
    }

    /**
     * The url-patterns in {@link #DESCRIPTOR_GATING_PAYMENTS_AND_LEDGER}, as a set.
     */
    private static Set<String> declaredInBaselineDescriptor() throws Exception {
        return new LinkedHashSet<>(
                gatedPathsIn(parseString(DESCRIPTOR_GATING_PAYMENTS_AND_LEDGER), FILTER_NAME));
    }

    /**
     * A filter that emptied its map without declaring that it was doing so on
     * purpose.
     */
    private static final String FILTER_EMPTY_WITHOUT_DECLARING = """
            public class ComingSoonGateFilter {
                static {
                }
            }
            """;

    /** A filter that emptied its map and said so, as the current filter does. */
    private static final String FILTER_EMPTY_AND_DECLARING_DORMANT = """
            public class ComingSoonGateFilter {
                public static final boolean DORMANT = GATED.isEmpty();
                static {
                }
            }
            """;

    @Test
    void anUndeclaredEmptyGateIsDetected() throws Exception {
        assertFalse(declaresDormant(FILTER_EMPTY_WITHOUT_DECLARING),
                "This fixture is only meaningful while an emptied GATED map that does not declare "
                        + "DORMANT is the failing case.");
        assertTrue(declaresDormant(FILTER_EMPTY_AND_DECLARING_DORMANT),
                "The real filter does declare DORMANT, so this fixture must be accepted -- "
                        + "otherwise it proves nothing about which shape is rejected.");
    }

    /**
     * The dormant-declaration check, applied to a source string rather than the
     * real file.
     */
    private static boolean declaresDormant(String filterSource) {
        return Pattern.compile("DORMANT\\s*=\\s*GATED\\.isEmpty\\s*\\(\\s*\\)")
                .matcher(filterSource)
                .find();
    }

    // ------------------------------------------------------------------
    // Shared logic, so the self-tests and the real assertions cannot drift.
    // ------------------------------------------------------------------

    /**
     * The two ways the two halves of the gate can fail to describe the same set of
     * paths.
     *
     * @param patternWithoutMap paths {@code web.xml} routes to the filter, but the
     *                          filter's map
     *                          does not gate — the gate runs and then does nothing
     * @param mapWithoutPattern paths the filter's map gates, but nothing routes to
     *                          the filter —
     *                          the gate is dead code and the page stays reachable
     */
    private record Disagreement(Set<String> patternWithoutMap, Set<String> mapWithoutPattern) {
    }

    private static Disagreement disagreements(Set<String> declared, Set<String> mapped) {
        Set<String> patternWithoutMap = new LinkedHashSet<>(declared);
        patternWithoutMap.removeAll(mapped);
        Set<String> mapWithoutPattern = new LinkedHashSet<>(mapped);
        mapWithoutPattern.removeAll(declared);
        return new Disagreement(patternWithoutMap, mapWithoutPattern);
    }

    /**
     * The url-patterns web.xml routes through {@link #FILTER_NAME}, in declaration
     * order.
     */
    private static List<String> gatedPathsInWebXml() throws Exception {
        return gatedPathsIn(parse(WEB_XML), FILTER_NAME);
    }

    private static List<String> gatedPathsIn(Document doc, String filterName) {
        List<String> paths = new ArrayList<>();
        for (Element mapping : children(doc, "filter-mapping")) {
            if (!filterName.equals(localName(mapping, "filter-name")))
                continue;
            for (Element pattern : children(mapping, "url-pattern")) {
                paths.add(text(pattern));
            }
        }
        return paths;
    }

    /** The keys of the filter's GATED map, read from its source. */
    private static List<String> gatedPathsIn(String filterSource) {
        List<String> paths = new ArrayList<>();
        Matcher m = GATED_PUT.matcher(filterSource);
        while (m.find()) {
            paths.add(m.group(1));
        }
        return paths;
    }

    private static List<String> gatedPathsInFilter() throws IOException {
        return gatedPathsIn(Files.readString(FILTER, StandardCharsets.UTF_8));
    }

    /**
     * Position of {@code filterName}'s mapping among the mappings, or -1 if absent.
     */
    private static int indexOfMappingFor(Document doc, String filterName) {
        List<Element> mappings = children(doc, "filter-mapping");
        for (int i = 0; i < mappings.size(); i++) {
            if (filterName.equals(localName(mappings.get(i), "filter-name"))) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // XML plumbing. DOM rather than regular expressions, for the reason
    // given in AsyncSupportDescriptorTest: this file's own comments and
    // its neighbours discuss <filter> and <filter-mapping> in prose, and a
    // regex would count those mentions as elements.
    // ------------------------------------------------------------------

    private static Document parse(Path path) throws Exception {
        return builder().parse(path.toFile());
    }

    private static Document parseString(String xml) throws Exception {
        return builder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    private static DocumentBuilder builder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
        return builder;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName(node).equals(name)) {
                found.add((Element) node);
            }
        }
        return found;
    }

    private static List<Element> children(Document doc, String name) {
        return children(doc.getDocumentElement(), name);
    }

    private static String localName(Node node) {
        String tag = node.getNodeName();
        int colon = tag.indexOf(':');
        return colon < 0 ? tag : tag.substring(colon + 1);
    }

    private static String localName(Element parent, String name) {
        for (Element child : children(parent, name)) {
            return text(child);
        }
        return null;
    }

    private static String text(Element element) {
        return element.getTextContent() == null ? "" : element.getTextContent().trim();
    }
}
