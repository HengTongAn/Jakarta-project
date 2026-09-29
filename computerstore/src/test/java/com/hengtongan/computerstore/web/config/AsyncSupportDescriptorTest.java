package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the async-support declarations in {@code web.xml}.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * {@code /realtime} is the Server-Sent Events stream. It calls
 * {@link jakarta.servlet.AsyncContext} via {@code request.startAsync()}, which the
 * servlet spec permits only when <em>every filter in the chain is async-capable</em>.
 * {@code /realtime} is matched by the {@code /*} url-pattern, so its chain is all ten
 * filters mapped to {@code /*}.
 *
 * <p>One filter in that chain was missing its {@code <async-supported>} declaration, so
 * {@code startAsync()} threw {@code IllegalStateException: A filter or servlet of the
 * current chain does not support asynchronous operations} and the endpoint returned 500
 * on every request. Tomcat's own warning names every offender, which is what makes this
 * diagnosable at all: it lists the chain members that lack the declaration.
 *
 * <h2>Where the declaration has to go, and why both elements carry it</h2>
 *
 * The two elements are not interchangeable, and this is not a matter of taste:
 *
 * <ul>
 *   <li>The Servlet specification puts {@code <async-supported>} on the
 *       {@code <filter-mapping>}. That is the normative location.</li>
 *   <li>Tomcat ignores it there. Tomcat builds a {@code FilterDef} from the
 *       {@code <filter>} element, and
 *       {@code ApplicationFilterChain.findNonAsyncFilters()} consults
 *       {@code FilterDef.getAsyncSupportedBoolean()} — verified by disassembling
 *       {@code ApplicationFilterChain} from Tomcat 11's {@code catalina.jar}, not by
 *       assumption. Tomcat's own {@code conf/web.xml} template likewise shows the element
 *       on {@code <filter>}.</li>
 * </ul>
 *
 * So a descriptor that declares it on only one of the two elements declares it in a place
 * its container may ignore. Both are asserted.
 *
 * <h2>Why a source lint</h2>
 *
 * Every other test of the SSE endpoint drives the servlet object directly and never
 * consults the descriptor, so the whole class of defect is invisible to them: they pass
 * against a {@code web.xml} that makes the deployed endpoint return 500. The descriptor is
 * only ever read at container startup, so the only way to cover it is to read the file.
 *
 * <h2>Why DOM and not regular expressions</h2>
 *
 * Because this file's own comments discuss these very tags and therefore contain the
 * literal text {@code <filter>} and {@code <filter-mapping>}. A regex over the raw file
 * counts those comment mentions as real elements and mis-pairs every block after the
 * first. That is not hypothetical: an earlier hand-written regex check of this
 * descriptor reported 18 filters and 17 mappings against an actual count of 15 and 15.
 * {@link DocumentBuilder} skips comments, so it sees only real elements.
 */
class AsyncSupportDescriptorTest {

    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path JAVA_ROOT = Path.of("src/main/java");

    /** {@code @WebServlet(...)} and its argument list, which cannot contain ')'. */
    private static final Pattern WEBSERVLET =
            Pattern.compile("@WebServlet\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    private static final Pattern ASYNC_TRUE =
            Pattern.compile("asyncSupported\\s*=\\s*true");
    private static final Pattern URL_PATTERNS =
            Pattern.compile("(?:urlPatterns|value)\\s*=\\s*\\{?\\s*((?:\"[^\"]*\"\\s*,?\\s*)+)");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    /**
     * A minimal descriptor with the declaration missing from both elements. Used to
     * prove the detector below actually reports a violation, so a future rewrite of the
     * checker cannot quietly pass everything.
     */
    private static final String BARE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee">
              <filter>
                <filter-name>ChainFilter</filter-name>
                <filter-class>com.example.ChainFilter</filter-class>
              </filter>
              <filter-mapping>
                <filter-name>ChainFilter</filter-name>
                <url-pattern>/*</url-pattern>
              </filter-mapping>
            </web-app>
            """;

    /** The same descriptor, correct. Guards against a checker that always reports. */
    private static final String COMPLETE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee">
              <filter>
                <filter-name>ChainFilter</filter-name>
                <filter-class>com.example.ChainFilter</filter-class>
                <async-supported>true</async-supported>
              </filter>
              <filter-mapping>
                <filter-name>ChainFilter</filter-name>
                <url-pattern>/*</url-pattern>
                <async-supported>true</async-supported>
              </filter-mapping>
            </web-app>
            """;

    // ------------------------------------------------------------------
    // The rules, against the real descriptor.
    // ------------------------------------------------------------------

    @Test
    void everyFilterDeclaresAsyncSupportBecauseTomcatReadsFilterDef() throws Exception {
        Document doc = parse(WEB_XML);
        List<String> offenders = new ArrayList<>();
        for (Element filter : children(doc, "filter")) {
            if (!declaresAsyncSupport(filter)) {
                offenders.add(localName(filter, "filter-name"));
            }
        }
        assertTrue(offenders.isEmpty(),
                "These <filter> elements lack <async-supported>true</async-supported>. Tomcat "
                        + "builds a FilterDef from this element and "
                        + "ApplicationFilterChain.findNonAsyncFilters() reads it, so any one of "
                        + "them fails startAsync() for every request it is mapped to: " + offenders);
    }

    @Test
    void everyFilterMappingDeclaresAsyncSupportBecauseTheSpecPutsItThere() throws Exception {
        Document doc = parse(WEB_XML);
        List<String> offenders = new ArrayList<>();
        for (Element mapping : children(doc, "filter-mapping")) {
            if (!declaresAsyncSupport(mapping)) {
                offenders.add(localName(mapping, "filter-name"));
            }
        }
        assertTrue(offenders.isEmpty(),
                "These <filter-mapping> elements lack <async-supported>true</async-supported>. "
                        + "The Servlet specification requires it on the mapping, so a container "
                        + "that reads the spec's location would refuse startAsync(): " + offenders);
    }

    /**
     * The invariant that actually caused the outage, stated directly: for every servlet
     * that declares async support, no filter in its chain may be missing the declaration.
     * The two tests above catch the same regression today; this one catches it with the
     * reason attached, so a failure explains the outage instead of just naming a tag.
     */
    @Test
    void everyAsyncServletHasAFullyAsyncCapableChain() throws Exception {
        Document doc = parse(WEB_XML);
        List<String> asyncPaths = asyncServletPaths();
        assertFalse(asyncPaths.isEmpty(),
                "No servlet declares asyncSupported = true. If the SSE endpoint was removed, "
                        + "delete this test too; if it was only renamed, this assertion is the "
                        + "signal that the streaming check below went blind.");

        for (String path : asyncPaths) {
            List<String> chain = new ArrayList<>();
            List<String> blocking = new ArrayList<>();
            for (String filter : chainFor(doc, path)) {
                chain.add(filter);
                if (!isAsyncCapable(doc, filter)) {
                    blocking.add(filter);
                }
            }
            assertFalse(chain.isEmpty(),
                    "No filter is mapped to the async path " + path + ", so the chain check is "
                            + "not exercising anything. Expected the /* filters to match.");
            assertTrue(blocking.isEmpty(),
                    "startAsync() on " + path + " would fail with \"A filter or servlet of the "
                            + "current chain does not support asynchronous operations\". These "
                            + "filters are on its chain but do not declare <async-supported>: "
                            + blocking + ". Full chain: " + chain);
        }
    }

    @Test
    void theAsyncChainIsWiderThanTheFiltersThatLookStreamRelated() throws Exception {
        // The bug was reachable precisely because these ten all sit on /* and not on
        // /realtime, so the endpoint cannot be protected by naming the "streaming" filters.
        Document doc = parse(WEB_XML);
        List<String> chain = chainFor(doc, "/realtime");
        assertTrue(chain.size() >= 10,
                "Expected at least the ten filters mapped to /* on the /realtime chain, found "
                        + chain.size() + ": " + chain);
    }

    @Test
    void filterMappingsUseUrlPatternsSoTheChainIsComputable() throws Exception {
        Document doc = parse(WEB_XML);
        List<String> byServletName = new ArrayList<>();
        for (Element mapping : children(doc, "filter-mapping")) {
            if (!children(mapping, "servlet-name").isEmpty()) {
                byServletName.add(localName(mapping, "filter-name"));
            }
        }
        assertTrue(byServletName.isEmpty(),
                "These filters are mapped by <servlet-name> rather than <url-pattern>: "
                        + byServletName + ". The chain check resolves mappings by url-pattern, so "
                        + "it would silently skip them and pass while they are still blocking "
                        + "startAsync(). Map them by url-pattern, or teach the check to resolve "
                        + "servlet names, before adding a filter this way.");
    }

    // ------------------------------------------------------------------
    // Self-test: the checker must still bite.
    // ------------------------------------------------------------------

    @Test
    void theDetectorReportsAMissingDeclaration() throws Exception {
        Document bare = parseString(BARE);
        assertEquals(List.of("ChainFilter"), filtersMissingAsyncSupport(bare),
                "A filter without the declaration must be reported by name.");
        assertEquals(List.of("ChainFilter"), mappingsMissingAsyncSupport(bare),
                "A mapping without the declaration must be reported by name.");
        assertEquals(List.of("ChainFilter"), chainBlockersFor(bare, "/realtime"),
                "The chain check must report the filter that would block startAsync().");
    }

    @Test
    void theDetectorAcceptsACompleteDeclaration() throws Exception {
        Document complete = parseString(COMPLETE);
        assertTrue(filtersMissingAsyncSupport(complete).isEmpty(),
                "A filter that declares <async-supported> must not be reported.");
        assertTrue(mappingsMissingAsyncSupport(complete).isEmpty(),
                "A mapping that declares <async-supported> must not be reported.");
        assertTrue(chainBlockersFor(complete, "/realtime").isEmpty(),
                "A fully async-capable chain must not be reported.");
    }

    @Test
    void commentsMentioningTheTagsAreNotCountedAsElements() throws Exception {
        // The real web.xml explains this rule in comments that contain the literal tag
        // text. A regex-based checker reads those as elements; a DOM-based one does not.
        String withComment = COMPLETE.replace(
                "<filter-mapping>",
                "<!-- mentions <filter> and <filter-mapping> in prose -->\n    <filter-mapping>");
        Document doc = parseString(withComment);
        assertEquals(1, children(doc, "filter").size(),
                "A tag named inside an XML comment must not be parsed as an element.");
        assertEquals(1, children(doc, "filter-mapping").size(),
                "A tag named inside an XML comment must not be parsed as an element.");
        assertTrue(filtersMissingAsyncSupport(doc).isEmpty(),
                "Comment text must not make the real filter look non-compliant.");
    }

    // ------------------------------------------------------------------
    // Shared logic, so the self-tests and the real assertions cannot drift.
    // ------------------------------------------------------------------

    private static List<String> filtersMissingAsyncSupport(Document doc) {
        List<String> offenders = new ArrayList<>();
        for (Element filter : children(doc, "filter")) {
            if (!declaresAsyncSupport(filter)) {
                offenders.add(localName(filter, "filter-name"));
            }
        }
        return offenders;
    }

    private static List<String> mappingsMissingAsyncSupport(Document doc) {
        List<String> offenders = new ArrayList<>();
        for (Element mapping : children(doc, "filter-mapping")) {
            if (!declaresAsyncSupport(mapping)) {
                offenders.add(localName(mapping, "filter-name"));
            }
        }
        return offenders;
    }

    private static List<String> chainBlockersFor(Document doc, String path) {
        List<String> blocking = new ArrayList<>();
        for (String filter : chainFor(doc, path)) {
            if (!isAsyncCapable(doc, filter)) {
                blocking.add(filter);
            }
        }
        return blocking;
    }

    /**
     * True when the element carries {@code <async-supported>true</async-supported>}.
     * Anything else — absent, false, or empty — counts as a violation, so a partially
     * filled-in descriptor fails rather than passing on a technicality.
     */
    private static boolean declaresAsyncSupport(Element element) {
        for (Element child : children(element, "async-supported")) {
            if ("true".equalsIgnoreCase(text(child))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsyncCapable(Document doc, String filterName) {
        for (Element filter : children(doc, "filter")) {
            if (filterName.equals(localName(filter, "filter-name"))) {
                return declaresAsyncSupport(filter);
            }
        }
        // A filter mapped but never declared cannot be async-capable.
        return false;
    }

    /**
     * The filters Tomcat will invoke for {@code path}, in declaration order, using the
     * specification's url-pattern matching: exact, path prefix, extension, and the
     * default mapping that matches everything.
     */
    private static List<String> chainFor(Document doc, String path) {
        Set<String> seen = new LinkedHashSet<>();
        for (Element mapping : children(doc, "filter-mapping")) {
            for (Element pattern : children(mapping, "url-pattern")) {
                if (matches(text(pattern), path)) {
                    String name = localName(mapping, "filter-name");
                    if (name != null) {
                        seen.add(name);
                    }
                }
            }
        }
        return new ArrayList<>(seen);
    }

    /** Servlet-specification url-pattern matching. */
    private static boolean matches(String pattern, String path) {
        if (pattern == null || pattern.isEmpty() || "/".equals(pattern)) {
            return true;
        }
        if (pattern.endsWith("/*")) {
            String base = pattern.substring(0, pattern.length() - 2);
            return path.equals(base) || path.startsWith(base + "/");
        }
        if (pattern.startsWith("*.")) {
            return path.endsWith(pattern.substring(1));
        }
        return pattern.equals(path);
    }

    /** Url patterns of every {@code @WebServlet} that declares {@code asyncSupported}. */
    private static List<String> asyncServletPaths() throws IOException {
        List<String> paths = new ArrayList<>();
        try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
            List<Path> sources = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path source : sources) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                Matcher servlet = WEBSERVLET.matcher(text);
                while (servlet.find()) {
                    String args = servlet.group(1);
                    if (!ASYNC_TRUE.matcher(args).find()) {
                        continue;
                    }
                    Matcher patterns = URL_PATTERNS.matcher(args);
                    if (!patterns.find()) {
                        continue;
                    }
                    Matcher quoted = QUOTED.matcher(patterns.group(1));
                    while (quoted.find()) {
                        paths.add(quoted.group(1));
                    }
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return paths;
    }

    // ------------------------------------------------------------------
    // XML plumbing.
    // ------------------------------------------------------------------

    private static Document parse(Path path) throws Exception {
        return builder().parse(path.toFile());
    }

    private static Document parseString(String xml) throws Exception {
        return builder().parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));
    }

    private static DocumentBuilder builder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Namespace-unaware on purpose: it makes the local-name lookups below correct
        // without qualifying every tag, and the document declares the Jakarta EE
        // namespace, which is not resolved over the network during these tests.
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        // The descriptor must not be able to pull in external entities.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) ->
                new org.xml.sax.InputSource(new java.io.StringReader("")));
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
