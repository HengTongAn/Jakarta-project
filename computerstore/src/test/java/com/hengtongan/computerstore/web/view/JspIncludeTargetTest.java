package com.hengtongan.computerstore.web.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks that every statically included fragment actually exists.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * {@code admin/coming-soon.jsp} included {@code ../../layouts/header.jspf}. That view lives one
 * level up, at {@code ../layouts/header.jspf}, so the path climbed past {@code views} into
 * {@code WEB-INF}, where there is no {@code layouts} directory. The page compiled, every test in
 * the suite passed, and the file existed and looked correct -- the only way to tell was to resolve
 * the path.
 *
 * <p>Because it is the page the "coming soon" gate forwards to, the consequence was that
 * <em>every gated admin section answered with a 500</em> rather than the placeholder: Payments and
 * Performance, on every request.
 *
 * <h2>Why this is checkable but a missing forward target was not</h2>
 *
 * A static include is resolved relative to the file doing the including, so the target can be
 * computed from the source tree -- {@link ServletForwardTargetTest} does the same for dispatcher
 * paths. What makes this bug easy to miss is that {@link JspTagBalanceTest} is blind to it: a
 * broken include is not a malformed tag, so the file reads as clean.
 *
 * <h2>What it does not check</h2>
 *
 * {@code <jsp:include>} and {@code jsp:forward}, which are resolved at runtime and named with
 * expressions in places. Those are checked by reading, not by resolving a path.
 */
class JspIncludeTargetTest {

    private static final Path VIEWS = Path.of("src/main/webapp/WEB-INF/views");

    /** {@code <%@ include file="layouts/header.jspf" %>}. */
    private static final Pattern INCLUDE =
            Pattern.compile("<%@\\s*include\\s+file\\s*=\\s*\"([^\"]+)\"");

    /**
     * Stripped before scanning. A fragment documents its own use by showing the
     * {@code <%@ include ... %>} line in a comment, and those are examples -- Jasper ignores a
     * directive inside {@code <%-- --%>}, so counting them reports files that do not exist for
     * every fragment that explains itself. {@link JspTagBalanceTest} strips comments for the same
     * reason.
     */
    private static final Pattern COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);

    @Test
    void everyStaticallyIncludedFragmentExists() throws IOException {
        List<Path> views = allViews();
        assertTrue(views.size() > 10,
                "expected to find the view files, found " + views.size() + " -- has the directory "
                        + "moved? A silent zero here would make this test useless.");

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Path view : views) {
            for (String target : includesIn(Files.readString(view, StandardCharsets.UTF_8))) {
                checked++;
                // An include is relative to the file doing the including, so resolve against
                // its own directory rather than the views root.
                Path resolved = view.getParent().resolve(target).normalize();
                if (!Files.isRegularFile(resolved)) {
                    problems.add(view + " includes " + target + ", which resolves to " + resolved
                            + " -- no such file");
                }
            }
        }

        assertTrue(checked > 0,
                "No view statically includes a fragment. Either that is no longer how the layouts "
                        + "are shared -- in which case point this test at what replaced it -- or the "
                        + "pattern needs updating, because right now it would pass while checking "
                        + "nothing.");

        if (!problems.isEmpty()) {
            fail("A view whose fragment is missing is a 500 on that page, and the build stays green "
                    + "until an admin opens it:\n  " + String.join("\n  ", problems));
        }
    }

    /**
     * The paths must climb out of {@code views} without escaping the webapp.
     *
     * <p>A {@code ../} chain that resolves outside {@code src/main/webapp} is either a mistake or,
     * if it happens to hit a real file elsewhere on the developer's disk, silently picking up
     * whatever is there. Either way the page does not behave the same on another machine, and
     * there is no file for the check above to fail against.
     */
    @Test
    void noIncludeEscapesTheWebappRoot() throws IOException {
        Path webapp = Path.of("src/main/webapp").toAbsolutePath().normalize();
        List<String> problems = new ArrayList<>();

        for (Path view : allViews()) {
            for (String target : includesIn(Files.readString(view, StandardCharsets.UTF_8))) {
                Path resolved = view.getParent().resolve(target).normalize();
                if (!resolved.startsWith(webapp)) {
                    problems.add(view + " includes " + target + ", which resolves to " + resolved
                            + " -- outside " + webapp);
                }
            }
        }

        if (!problems.isEmpty()) {
            fail("These includes resolve outside the deployed document root, so they work or fail "
                    + "depending on what happens to sit at that path on the machine:\n  "
                    + String.join("\n  ", problems));
        }
    }

    // ------------------------------------------------------------------
    // Self-test: prove the resolution actually rejects a bad path.
    // A lint that cannot fail is not a lint.
    // ------------------------------------------------------------------

    @Test
    void theCheckerRejectsAnIncludeThatClimbsTooFar() {
        // The real defect, as a string. Resolved the same way the check resolves, so this
        // tests the resolution and not a restatement of it.
        Path header = VIEWS.resolve("layouts/header.jspf");
        assertTrue(Files.isRegularFile(header), "fixture assumes layouts/header.jspf exists");

        Path climbsTooFar = VIEWS.resolve("layouts").resolve("../../layouts/header.jspf").normalize();
        assertTrue(!climbsTooFar.startsWith(VIEWS),
                "the fixture must resolve outside the views root, or it proves nothing: " + climbsTooFar);

        Path correct = VIEWS.resolve("layouts").resolve("../layouts/header.jspf").normalize();
        assertTrue(Files.isRegularFile(correct),
                "and the same include written correctly must resolve to the real file");
    }

    @Test
    void theCheckerFindsTheIncludeDirectives() {
        assertTrue(includesIn("<%@ include file=\"../layouts/header.jspf\" %>").size() == 1);
        assertTrue(includesIn("<%@include file=\"x.jspf\"%>").size() == 1,
                "whitespace around the directive must not hide it");
        assertTrue(includesIn("<%@ page contentType=\"text/html\" %>").isEmpty(),
                "a page directive is not an include");
    }

    @Test
    void theCheckerIgnoresAnIncludeShownInsideAComment() {
        // The badge fragments document their own use by printing the include line in a comment.
        // Counting those reports a file that does not exist, once per fragment that explains
        // itself -- and the real broken include, should there ever be one, is the same shape.
        assertTrue(includesIn("<%-- usage: <%@ include file=\"../../components/badge.jspf\" %> --%>")
                        .isEmpty(),
                "a directive named inside a JSP comment is an example, not an include");
    }

    private static List<String> includesIn(String source) {
        List<String> targets = new ArrayList<>();
        Matcher m = INCLUDE.matcher(COMMENT.matcher(source).replaceAll(" "));
        while (m.find()) {
            targets.add(m.group(1));
        }
        return targets;
    }

    /**
     * Every view, as absolute paths.
     *
     * <p>Absolute because these paths are also compared against the absolute document root, and a
     * relative path never reports as being inside an absolute one -- which fails every include in
     * the project at once and hides the real defect among the noise.
     */
    private static List<Path> allViews() throws IOException {
        Path root = VIEWS.toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jsp") || name.endsWith(".jspf");
                    })
                    .sorted()
                    .toList();
        }
    }
}