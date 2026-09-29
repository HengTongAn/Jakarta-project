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
 * Checks that every JSP view has balanced {@code c:} tags.
 * <p>
 * This exists because the failure mode is nasty and has now bitten twice in this
 * codebase. A missing {@code </c:otherwise>} or a stray brace in a closing tag
 * does not fail the build, does not fail a unit test, and does not show up until
 * a customer requests the page &mdash; where it is a hard 500 from the Jasper
 * compiler, reported as the misleading "the end tag is unbalanced". Both times,
 * the defect was in a page that a passing test suite already covered.
 *
 * <p>It is a source lint rather than a rendering test on purpose: it needs no
 * database, no servlet container and no credentials, so it runs on every
 * {@code mvn test} and catches the typo at the moment it is written.
 *
 * <h2>What it deliberately tolerates</h2>
 * <ul>
 *   <li>Tag names inside JSP comments and scriptlets. Those are prose, and
 *       counting them produced a false "unclosed" report on a correct page.</li>
 *   <li>Void tags such as {@code c:out} and {@code c:set}, which never open a
 *       scope.</li>
 * </ul>
 */
class JspTagBalanceTest {

    private static final Pattern TAG = Pattern.compile(
            "<(/?)(c:[a-zA-Z]+)((?:\"[^\"]*\"|'[^']*'|[^>])*?)(/?)>", Pattern.DOTALL);

    /** Stripped before counting: markup mentioned inside either is not markup. */
    private static final Pattern COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern SCRIPTLET = Pattern.compile("<%.*?%>", Pattern.DOTALL);

    /** Tags that open no scope. */
    private static final List<String> VOID = List.of(
            "c:out", "c:url", "c:param", "c:import", "c:redirect", "c:set", "c:remove");

    private static Path viewsDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            Path candidate = dir.resolve("src/main/webapp/WEB-INF/views");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate src/main/webapp/WEB-INF/views from "
                + System.getProperty("user.dir"));
    }

    private static List<Path> allViews() throws IOException {
        try (Stream<Path> paths = Files.walk(viewsDir())) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".jsp") || name.endsWith(".jspf");
                    })
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyViewHasBalancedTags() throws IOException {
        List<Path> views = allViews();
        assertTrue(views.size() > 10,
                "expected to find the view files, found " + views.size() + " -- has the "
                        + "directory moved? A silent zero here would make this test useless.");

        List<String> problems = new ArrayList<>();
        for (Path view : views) {
            problems.addAll(check(view));
        }
        if (!problems.isEmpty()) {
            fail("unbalanced JSP tags, each of which is a 500 when the page is requested:\n  "
                    + String.join("\n  ", problems));
        }
    }

    @Test
    void theCheckerActuallyFailsOnKnownBadMarkup() {
        // A checker that cannot fail is worse than none: it reports "all clean"
        // while a page is broken. These are the two real defects from this
        // codebase, so if the detection logic regresses, this fails first.
        assertTrue(problemsIn("""
                <c:choose>
                <c:when test="${a}">A</c:when>
                <c:otherwise>B
                </c:choose>
                """).size() > 0, "must flag a missing </c:otherwise>");

        assertTrue(problemsIn("""
                <c:forEach var="x" items="${y}">
                <li>${x}</li>
                </c:forEach}>
                """).size() > 0, "must flag a stray brace in a closing tag");

        assertTrue(problemsIn("""
                <c:if test="${a}">
                <div>x</div>
                """).size() > 0, "must flag an unclosed <c:if>");

        assertTrue(problemsIn("""
                <c:choose>
                  <c:when test="${a}">A</c:when>
                  <c:otherwise>B</c:otherwise>
                </c:choose>
                """).isEmpty(), "must accept a correctly balanced block");

        assertTrue(problemsIn("""
                <%-- mentions a <c:choose> in prose --%>
                <c:if test="${a}"><div>x</div></c:if>
                """).isEmpty(), "must ignore tags named inside a comment");
    }

    /** Runs the same check over a snippet, for the self-test above. */
    private static List<String> problemsIn(String source) {
        List<String> problems = new ArrayList<>();
        check(source, "<snippet>", problems);
        return problems;
    }

    private static List<String> check(Path view) throws IOException {
        List<String> problems = new ArrayList<>();
        check(new String(Files.readAllBytes(view), StandardCharsets.UTF_8), view.toString(), problems);
        return problems;
    }

    private static void check(String raw, String label, List<String> problems) {
        String text = SCRIPTLET.matcher(raw).replaceAll(" ");
        text = COMMENT.matcher(text).replaceAll(" ");
        List<String[]> stack = new ArrayList<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) {
            String close = m.group(1);
            String name = m.group(2);
            String attrs = m.group(3);
            boolean selfClose = !m.group(4).isEmpty();
            if (VOID.contains(name)) {
                continue;
            }
            int line = 1;
            for (int i = 0; i < m.start(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            if (!close.isEmpty()) {
                // "</name>" and nothing else. A permissive regex reads the stray
                // brace in "</c:forEach}>" as an attribute and reports the tag as
                // well formed, which is exactly how that typo reached production.
                if (!attrs.isBlank() || selfClose) {
                    problems.add(label + ":" + line + ": </" + name + attrs
                            + (selfClose ? "/" : "") + "> has content after the tag name");
                }
                if (!stack.isEmpty() && stack.get(stack.size() - 1)[0].equals(name)) {
                    stack.remove(stack.size() - 1);
                } else {
                    problems.add(label + ":" + line + ": </" + name + "> closes nothing"
                            + (stack.isEmpty() ? "" : "; open: " + stack));
                }
            } else if (!selfClose) {
                stack.add(new String[]{name, String.valueOf(line)});
            }
        }
        for (String[] open : stack) {
            problems.add(label + ":" + open[1] + ": <" + open[0] + "> is never closed");
        }
    }
}
