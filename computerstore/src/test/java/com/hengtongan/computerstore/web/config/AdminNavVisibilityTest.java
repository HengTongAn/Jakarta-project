package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two things that make the admin nav usable.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * The admin nav is thirteen links in a {@code flex-row flex-nowrap} row inside
 * an {@code overflow-x: auto} container, and it overflows at <em>every</em>
 * viewport width: measured at 1453px of links against an 823px port on a
 * 1280px laptop. Two things then went wrong together.
 *
 * <ol>
 *   <li>The active link was not scrolled into view. The {@code active} class was
 *       applied correctly on all thirteen pages, but on six of them the active
 *       link began past the right edge of the port, so the highlight was never
 *       actually on screen. Because Dashboard is link 1 it is the one link
 *       always visible, which made every page look like Dashboard was the
 *       selected one.</li>
 *   <li>The scrollbar was hidden with {@code scrollbar-width: none}. So 630px
 *       of navigation was not merely off screen, it was off screen with no
 *       indication that it existed.</li>
 * </ol>
 *
 * <h2>Why a static check rather than a browser check</h2>
 *
 * The failure is a scroll position, so the honest test is a browser. This
 * project deliberately keeps {@code mvn test} free of a browser dependency, so
 * the invariant is checked on the source instead. Both halves of the fix are
 * source-level facts: the fragment must load the script that reveals the active
 * link, and no rule may hide the scrollbar that makes the overflow legible.
 * Neither check can see layout, so neither claims to -- see
 * {@code docs/development/source-linting.md} for the browser harness that does.
 *
 * <p>The two checks are deliberately coupled to a single class name. If
 * {@code .admin-nav-scroll} were renamed in the CSS but not the JS (or the
 * reverse), the reveal would silently stop working, so the class name is
 * required to appear in both.
 */
class AdminNavVisibilityTest {

    private static final Path ADMIN_NAV_JSPF =
            Path.of("src/main/webapp/WEB-INF/views/layouts/admin-nav.jspf");
    private static final Path ADMIN_NAV_JS =
            Path.of("src/main/webapp/assets/js/admin-nav.js");
    private static final Path APP_CSS_DIR = Path.of("src/main/webapp/assets/css/app");

    /** The container the JS scrolls and the CSS styles. Renaming one must break the other. */
    private static final String SCROLLER = ".admin-nav-scroll";

    private static final Pattern INCLUDE = Pattern.compile("<script[^>]*src=\"([^\"]*)\"");

    @Test
    void adminNavFragmentLoadsTheRevealScript() throws IOException {
        String jspf = read(ADMIN_NAV_JSPF);
        List<String> scripts = new ArrayList<>();
        Matcher m = INCLUDE.matcher(jspf);
        while (m.find()) scripts.add(m.group(1));

        assertFalse(scripts.isEmpty(),
                ADMIN_NAV_JSPF + " loads no script at all, so nothing can reveal the active link");
        assertTrue(scripts.stream().anyMatch(s -> s.contains("admin-nav.js")),
                () -> ADMIN_NAV_JSPF + " must load admin-nav.js, otherwise the active link stays "
                        + "scrolled out of sight and the nav reads as if Dashboard were always "
                        + "selected. Scripts found: " + scripts);
    }

    @Test
    void adminNavScrollbarIsNeverHidden() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path css : appStylesheets()) {
            for (Rule rule : rules(read(css))) {
                if (!rule.selector.contains(SCROLLER)) continue;

                String hidden = declarationOf(rule.body, "scrollbar-width");
                if (hidden != null && hidden.trim().equals("none")) {
                    violations.add(css.getFileName() + " `" + rule.selector.trim()
                            + "` sets scrollbar-width: none -- the overflow becomes invisible again");
                }
                if (rule.selector.contains("::-webkit-scrollbar")
                        && declarationOf(rule.body, "display") != null
                        && declarationOf(rule.body, "display").trim().equals("none")) {
                    violations.add(css.getFileName() + " `" + rule.selector.trim()
                            + "` sets display: none on the scrollbar");
                }
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "the admin nav must keep a visible scrollbar:\n  " + String.join("\n  ", violations));
    }

    @Test
    void revealScriptAndStylesheetShareTheScrollerClassName() throws IOException {
        // If the container is renamed in one place only, revealActive() silently
        // finds nothing and the highlight goes back off screen. Both sides must
        // still name the same class.
        String js = read(ADMIN_NAV_JS);
        assertTrue(js.contains(SCROLLER),
                () -> ADMIN_NAV_JS + " no longer references " + SCROLLER
                        + ", so the active link will not be revealed");

        boolean styledSomewhere = false;
        for (Path css : appStylesheets()) {
            if (read(css).contains(SCROLLER)) styledSomewhere = true;
        }
        assertTrue(styledSomewhere,
                () -> "no stylesheet mentions " + SCROLLER + " any more, but the JSP and JS still use it");
    }

    // ------------------------------------------------------------------
    // Self-tests: prove each check above actually rejects the broken shape.
    // A lint that cannot fail is not a lint.
    // ------------------------------------------------------------------

    @Test
    void missingScriptIncludeIsDetected() {
        String broken = """
                <nav class="navbar admin-nav">
                    <div class="container">
                        <div class="navbar-nav admin-nav-scroll"> ... </div>
                    </div>
                </nav>
                """;
        assertFalse(includesRevealScript(broken),
                "a fragment with no admin-nav.js include must be rejected");
    }

    @Test
    void aScriptIncludeIsDetected() {
        String working = "<script defer src=\"/computerstore/assets/js/admin-nav.js?v=1\"></script>";
        assertTrue(includesRevealScript(working));
    }

    @Test
    void reHiddenScrollbarIsDetected() {
        String broken = """
                .admin-nav .admin-nav-scroll {
                    overflow-x: auto;
                    scrollbar-width: none;
                }
                """;
        assertFalse(scrollbarStaysVisible(broken),
                "scrollbar-width: none on the admin nav must be rejected");
    }

    @Test
    void hiddenWebkitScrollbarIsDetected() {
        // Chrome honours display:none on ::-webkit-scrollbar, so this hides the
        // affordance just as effectively as the standard property.
        String broken = """
                .admin-nav .admin-nav-scroll::-webkit-scrollbar {
                    display: none;
                }
                """;
        assertFalse(scrollbarStaysVisible(broken),
                "display: none on the admin nav's webkit scrollbar must be rejected");
    }

    @Test
    void visibleThinScrollbarIsAccepted() {
        String working = """
                .admin-nav .admin-nav-scroll {
                    overflow-x: auto;
                    scrollbar-width: thin;
                    scrollbar-color: var(--line) transparent;
                }
                .admin-nav .admin-nav-scroll::-webkit-scrollbar { height: 6px; }
                """;
        assertTrue(scrollbarStaysVisible(working));
    }

    @Test
    void hidingSomeOtherElementsScrollbarIsNotFlagged() {
        // The check is scoped to the admin nav. Hiding a scrollbar elsewhere in
        // the app is a different decision and must not fail this test.
        String unrelated = """
                .table-scroll {
                    overflow-x: auto;
                    scrollbar-width: none;
                }
                """;
        assertTrue(scrollbarStaysVisible(unrelated),
                "only the admin nav's scrollbar is in scope for this rule");
    }

    // ------------------------------------------------------------------
    // Helpers. The predicates are separated from the @Test bodies so the
    // self-tests above can feed them source text directly.
    // ------------------------------------------------------------------

    private static boolean includesRevealScript(String jspf) {
        Matcher m = INCLUDE.matcher(jspf);
        while (m.find()) {
            if (m.group(1).contains("admin-nav.js")) return true;
        }
        return false;
    }

    private static boolean scrollbarStaysVisible(String css) {
        for (Rule rule : rules(css)) {
            if (!rule.selector.contains(SCROLLER)) continue;
            String width = declarationOf(rule.body, "scrollbar-width");
            if (width != null && width.trim().equals("none")) return false;
            if (rule.selector.contains("::-webkit-scrollbar")) {
                String display = declarationOf(rule.body, "display");
                if (display != null && display.trim().equals("none")) return false;
            }
        }
        return true;
    }

    private record Rule(String selector, String body) { }

    /**
     * Minimal flat rule extractor: returns every {@code selector { body }} pair,
     * descending through at-rule blocks so the innermost selector is what gets
     * matched. Comments and strings are stripped first so a commented-out
     * declaration cannot fail the build.
     */
    private static List<Rule> rules(String css) {
        String clean = stripComments(css);
        List<Rule> out = new ArrayList<>();
        StringBuilder selector = new StringBuilder();
        int i = 0;
        while (i < clean.length()) {
            char c = clean.charAt(i);
            if (c == '{') {
                int depth = 1;
                int j = i + 1;
                while (j < clean.length() && depth > 0) {
                    char d = clean.charAt(j);
                    if (d == '{') depth++;
                    else if (d == '}') depth--;
                    j++;
                }
                out.add(new Rule(selector.toString().trim(), clean.substring(i + 1, j - 1)));
                selector.setLength(0);
                i = j;
            } else if (c == '}') {
                selector.setLength(0);
                i++;
            } else {
                selector.append(c);
                i++;
            }
        }
        return out;
    }

    private static String stripComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /** Value of {@code property} in a declaration block, or null if absent. */
    private static String declarationOf(String body, String property) {
        Matcher m = Pattern.compile("(?m)(?:^|;)\\s*" + Pattern.quote(property) + "\\s*:\\s*([^;]+)")
                .matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static List<Path> appStylesheets() throws IOException {
        try (var stream = Files.list(APP_CSS_DIR)) {
            return stream.filter(p -> p.toString().endsWith(".css")).sorted().toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
