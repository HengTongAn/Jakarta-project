package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

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
 * Guards the Bootstrap gutter invariant that {@code theme.css} depends on.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * A Bootstrap row pulls itself outside its container by half its own gutter
 * ({@code margin-inline: calc(var(--bs-gutter-x) / -2)}), and the container's
 * horizontal padding is the only thing absorbing that pull. The two are
 * therefore locked together:
 *
 * <pre>container padding-x  ==  container --bs-gutter-x / 2  &gt;=  row gutter / 2</pre>
 *
 * {@code theme.css} has a "responsive comfort pass" that narrows
 * {@code .container}'s gutter on small screens to win back content width. It
 * did that by shrinking the container gutter alone, while the {@code g-3} and
 * {@code g-4} rows inside kept their full gutter. On every page, at every
 * width below 768px, the row then hung outside the page by 4px, and the
 * document scrolled sideways: {@code scrollWidth} 394 against a 390px
 * viewport. A 4px overflow is easy to dismiss and it is the single most
 * visible "this site was not built for my phone" signal there is.
 *
 * <h2>Why a static check rather than a browser check</h2>
 *
 * The failure is a computed-style property, so the honest test is a browser.
 * This project deliberately keeps {@code mvn test} free of a browser
 * dependency, so the invariant is checked on the source instead: for every
 * media block that narrows a container gutter, a row gutter no larger than it
 * must be declared in the same block. That is precisely the edit that fixes the
 * overflow, which makes the rule both narrow enough to be useful and specific
 * enough to not fire on unrelated CSS.
 *
 * <p>Values are compared in rem, because {@code .container} and {@code .row}
 * gutters are both authored in rem and rem is a fixed ratio to px. Any
 * non-rem length is reported as unsupported rather than silently skipped, so
 * the check cannot pass by not understanding the stylesheet.
 */
class CssGutterInvariantTest {

    private static final Path APP_CSS = Path.of("src/main/webapp/assets/css/app");

    /** Root font size the rem values resolve against, in px. */
    private static final double REM_PX = 16.0;

    /** Longest gutter any {@code g-*} class can request (Bootstrap's {@code g-5}). */
    private static final double MAX_BOOTSTRAP_GUTTER_REM = 3.0;

    @Test
    void everyNarrowedContainerGutterHasAMatchingRowClamp() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path css : appStylesheets()) {
            String source = Files.readString(css, encoding());
            for (MediaBlock block : MediaBlock.parse(source)) {
                // Widest container gutter declared anywhere in this block.
                Double containerGutter = null;
                String containerSelector = null;
                for (Declaration d : block.declarations) {
                    if (!d.selectorIsContainer() || d.gutterRem == null) continue;
                    if (containerGutter == null || d.gutterRem > containerGutter) {
                        containerGutter = d.gutterRem;
                        containerSelector = d.selector;
                    }
                }
                if (containerGutter == null) continue;   // block does not touch container gutters

                // Widest row gutter declared in the same block.
                Double rowGutter = null;
                for (Declaration d : block.declarations) {
                    if (!d.selectorIsRow() || d.gutterRem == null) continue;
                    if (rowGutter == null || d.gutterRem > rowGutter) rowGutter = d.gutterRem;
                }

                if (rowGutter == null) {
                    violations.add(String.format(
                            "%s @media %s narrows %s to %srem but declares no row gutter clamp, "
                                    + "so g-3/g-4 rows pull outside the container padding and the page "
                                    + "overflows horizontally",
                            css.getFileName(), block.condition, containerSelector, containerGutter));
                } else if (rowGutter > containerGutter) {
                    violations.add(String.format(
                            "%s @media %s: container gutter %srem is narrower than the row gutter "
                                    + "%srem, so rows hang outside the page by %srem",
                            css.getFileName(), block.condition, containerGutter, rowGutter,
                            round((rowGutter - containerGutter) / 2.0)));
                }
            }
        }

        assertTrue(violations.isEmpty(),
                () -> "Bootstrap gutter invariant broken (row pulls outside container padding):\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    void containerPaddingAbsorbsTheWidestGutterClassUsedInViews() throws IOException {
        // The base (unmediaed) case: Bootstrap's default container padding is
        // 0.75rem per side, which absorbs g-3 (1rem) and g-4 (1.5rem) exactly.
        // It is asserted directly so a change to the default cannot pass by
        // accident, and so the self-tests below have a real baseline to break.
        Set<String> gutterClasses = gutterClassesUsedInViews();
        assertFalse(gutterClasses.isEmpty(), "expected at least one g-* row in the views");

        double widestGutter = 0;
        for (String cls : gutterClasses) {
            widestGutter = Math.max(widestGutter, bootstrapGutterRem(cls));
        }
        final double widest = widestGutter;
        // A row needs container padding of at least half its own gutter, so
        // Bootstrap's default 0.75rem per side absorbs a 1.5rem gutter exactly.
        assertTrue(widest <= 1.5,
                () -> "views use a " + widest + "rem row gutter, which needs at least "
                        + (widest / 2.0) + "rem of container padding per side; Bootstrap's default "
                        + "0.75rem no longer covers it, so the base case would overflow too");

        assertEquals(3.0, MAX_BOOTSTRAP_GUTTER_REM, "sanity: g-5 gutter");
    }

    @Test
    void everyGutterValueInAppCssIsARemLengthTheCheckUnderstands() throws IOException {
        // If a gutter is authored in px or a calc(), the comparison above would
        // skip it and the test would pass without having looked. Fail loudly
        // instead of pretending to have verified something.
        List<String> unsupported = new ArrayList<>();
        Pattern gutter = Pattern.compile("--bs-gutter-x\\s*:\\s*([^;}]+)");

        for (Path css : appStylesheets()) {
            String source = Files.readString(css, encoding());
            Matcher m = gutter.matcher(source);
            while (m.find()) {
                String value = m.group(1).trim();
                if (!value.matches("\\d*\\.?\\d+rem")) {
                    unsupported.add(css.getFileName() + ": --bs-gutter-x: " + value);
                }
            }
        }
        assertTrue(unsupported.isEmpty(),
                () -> "gutter values must be plain rem for this check to verify them: " + unsupported);
    }

    @Test
    void theDetectorBitesOnTheOriginalDefect() {
        // The pre-fix stylesheet: container narrowed, rows untouched. This is
        // the exact shape that produced the 4px overflow, so the check must
        // reject it.
        String broken = """
                @media (max-width: 767.98px) {
                    .container { --bs-gutter-x: 1rem; }
                    .hero { padding: 3rem 0; }
                }
                """;
        List<String> violations = findViolations(broken, "hypothetical.css");
        assertEquals(1, violations.size(), () -> "expected the missing clamp to be reported: " + violations);
        assertTrue(violations.get(0).contains("no row gutter clamp"),
                () -> "should name the missing clamp, got: " + violations.get(0));
    }

    @Test
    void theDetectorBitesWhenTheRowGutterIsStillWider() {
        // A clamp that is present but too small to absorb the rows is the same
        // bug wearing a disguise, and must also be rejected.
        String broken = """
                @media (max-width: 767.98px) {
                    .container { --bs-gutter-x: 1rem; }
                    .container .row { --bs-gutter-x: 1.5rem; }
                }
                """;
        List<String> violations = findViolations(broken, "hypothetical.css");
        assertEquals(1, violations.size(), () -> "expected the too-wide row gutter to be reported: " + violations);
        assertTrue(violations.get(0).contains("narrower than the row gutter"),
                () -> "should name the mismatch, got: " + violations.get(0));
    }

    @Test
    void theDetectorAcceptsTheFixedStylesheetAndIgnoresUnrelatedBlocks() {
        // The shape that is correct: narrowed container plus a matching clamp.
        String good = """
                @media (max-width: 767.98px) {
                    .container { --bs-gutter-x: 1rem; }
                    .container .row { --bs-gutter-x: 1rem; }
                    .hero { padding: 3rem 0; }
                }
                @media (max-width: 575.98px) {
                    .navbar { min-height: 58px; }
                }
                """;
        assertTrue(findViolations(good, "hypothetical.css").isEmpty(),
                "the fix, and blocks that never touch container gutters, must both pass");
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> findViolations(String source, String label) {
        List<String> violations = new ArrayList<>();
        for (MediaBlock block : MediaBlock.parse(source)) {
            Double containerGutter = null;
            String containerSelector = null;
            for (Declaration d : block.declarations) {
                if (!d.selectorIsContainer() || d.gutterRem == null) continue;
                if (containerGutter == null || d.gutterRem > containerGutter) {
                    containerGutter = d.gutterRem;
                    containerSelector = d.selector;
                }
            }
            if (containerGutter == null) continue;

            Double rowGutter = null;
            for (Declaration d : block.declarations) {
                if (!d.selectorIsRow() || d.gutterRem == null) continue;
                if (rowGutter == null || d.gutterRem > rowGutter) rowGutter = d.gutterRem;
            }

            if (rowGutter == null) {
                violations.add(label + " @media " + block.condition + " narrows " + containerSelector
                        + " to " + containerGutter + "rem but declares no row gutter clamp");
            } else if (rowGutter > containerGutter) {
                violations.add(label + " @media " + block.condition + ": container gutter "
                        + containerGutter + "rem is narrower than the row gutter " + rowGutter + "rem");
            }
        }
        return violations;
    }

    private static List<Path> appStylesheets() throws IOException {
        try (Stream<Path> files = Files.list(APP_CSS)) {
            return files.filter(p -> p.toString().endsWith(".css")).sorted().toList();
        }
    }

    private static java.nio.charset.Charset encoding() {
        return StandardCharsets.UTF_8;
    }

    private static Set<String> gutterClassesUsedInViews() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        Pattern p = Pattern.compile("\\bg-([0-5])\\b");
        try (Stream<Path> files = Files.walk(Path.of("src/main/webapp/WEB-INF/views"))) {
            for (Path f : files.filter(x -> x.toString().endsWith(".jsp") || x.toString().endsWith(".jspf")).toList()) {
                Matcher m = p.matcher(Files.readString(f, encoding()));
                while (m.find()) found.add("g-" + m.group(1));
            }
        }
        return found;
    }

    /** Bootstrap 5 {@code $spacers} gutter map, in rem. */
    private static double bootstrapGutterRem(String gutterClass) {
        return switch (gutterClass) {
            case "g-0" -> 0.25;
            case "g-1" -> 0.5;
            case "g-2" -> 1.0;
            case "g-3" -> 1.0;
            case "g-4" -> 1.5;
            case "g-5" -> 3.0;
            default -> throw new IllegalArgumentException("unknown gutter class " + gutterClass);
        };
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** One {@code @media} block and the gutter declarations inside it. */
    record MediaBlock(String condition, List<Declaration> declarations) {

        static List<MediaBlock> parse(String css) {
            List<MediaBlock> blocks = new ArrayList<>();
            Matcher open = Pattern.compile("@media\\s*([^{]+)\\{").matcher(css);
            while (open.find()) {
                String condition = open.group(1).trim();
                int depth = 1;
                int i = open.end();
                StringBuilder body = new StringBuilder();
                while (i < css.length() && depth > 0) {
                    char c = css.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) break;
                    }
                    if (depth >= 1) body.append(c);
                    i++;
                }
                blocks.add(new MediaBlock(condition, Declaration.parse(body.toString())));
            }
            return blocks;
        }
    }

    /** A single {@code selector { --bs-gutter-x: value }} rule. */
    record Declaration(String selector, Double gutterRem) {

        static List<Declaration> parse(String css) {
            List<Declaration> out = new ArrayList<>();
            // Strip comments first: prose in this codebase quotes selectors
            // verbatim, and a comment containing ".container" must not be read
            // as a rule.
            String clean = css.replaceAll("/\\*.*?\\*/", " ");
            Matcher m = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(clean);
            while (m.find()) {
                String selector = m.group(1).trim();
                String body = m.group(2);
                Matcher g = Pattern.compile("--bs-gutter-x\\s*:\\s*(\\d*\\.?\\d+)rem").matcher(body);
                if (g.find()) {
                    out.add(new Declaration(selector, Double.parseDouble(g.group(1))));
                } else if (body.contains("--bs-gutter-x")) {
                    out.add(new Declaration(selector, null));   // present but not a plain rem
                }
            }
            return out;
        }

        /**
         * A row is a row, including when the rule is scoped to a container
         * (e.g. {@code .container .row}). The mutual exclusion matters: without
         * it the clamp rule reads as a container declaration as well, so
         * comparing "widest container" against "widest row" would compare the
         * clamp against itself and never report a clamp that is too small.
         */
        boolean selectorIsRow() {
            return selector.matches("(?s).*\\.row\\b.*") && !selector.contains(".row::");
        }

        boolean selectorIsContainer() {
            return selector.matches("(?s).*\\.container\\b.*") && !selectorIsRow();
        }
    }

    private CssGutterInvariantTest() {
    }
}
