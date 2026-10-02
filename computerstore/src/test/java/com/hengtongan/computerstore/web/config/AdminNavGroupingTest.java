package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the grouped admin nav: four top-level dropdowns instead of fourteen
 * flat links. Dashboard stays a bare link -- it is the one destination that
 * should be one click away, and a dropdown holding only the dashboard would cost
 * a hover and a click to reach the panel's own home page.
 *
 * <h2>Why it is not a flat list any more</h2>
 *
 * The row used to be thirteen (then fourteen) links in a
 * {@code flex-row flex-nowrap} box inside an {@code overflow-x: auto} container.
 * Measured, that was 1453px of links against an 823px port on a 1280px laptop, so
 * the active link was off screen on most pages and {@code admin-nav.js} existed
 * only to scroll it back into view. Grouping the sections fixes the width at the
 * source instead of papering over it, which retires the scroller and the script.
 *
 * <h2>The defects this exists to prevent</h2>
 *
 * <ol>
 *   <li><b>A row that clips its own menus.</b> This is the trap in the obvious
 *       fix. A horizontal scroller needs {@code overflow-x: auto} with a
 *       non-visible {@code overflow-y} -- the CSS overflow rules force
 *       {@code overflow-y} to {@code auto} whenever {@code overflow-x} is not
 *       {@code visible} -- and that clips an absolutely positioned dropdown menu
 *       to the row's own box. Every menu would open and immediately vanish, on
 *       every admin page, with nothing logged. Nothing in the markup looks wrong,
 *       so only a rule that forbids the overflow can catch it.</li>
 *   <li><b>A trigger that never highlights.</b> A dropdown trigger points at
 *       {@code #}, so the old "is this the active link" test cannot apply to it.
 *       Each group has a flag naming its own sections instead. EL renders an
 *       undefined variable as {@code false} rather than erroring, so a flag used
 *       above its {@code <c:set>}, or one that misses a section its own menu
 *       contains, leaves whole pages with no highlight anywhere and no failing
 *       test. The flags and the menus are therefore read from the fragment and
 *       compared with each other, not restated here.</li>
 *   <li><b>A section with no route out of the nav.</b> A new
 *       {@code @WebServlet("/admin/...")} that nobody adds to the menu is
 *       reachable only by typing the URL. The set of admin servlet mappings and
 *       the set of nav links are both read from source and compared, so the two
 *       cannot drift.</li>
 * </ol>
 *
 * <h2>What it cannot see</h2>
 *
 * Everything here is a source lint, because this project keeps {@code mvn test}
 * free of a browser and container. The checks prove the intent is expressed,
 * that the menus are not clipped, and that the two sets agree. They cannot see
 * layout: a bar that wrapped to three lines on a phone would pass all of them.
 * Opening the panel at 1280px and at 375px is the only way to check that part.
 */
class AdminNavGroupingTest {

    private static final Path ADMIN_NAV =
            Path.of("src/main/webapp/WEB-INF/views/layouts/admin-nav.jspf");
    private static final Path HEADER =
            Path.of("src/main/webapp/WEB-INF/views/layouts/header.jspf");
    private static final Path APP_CSS_DIR = Path.of("src/main/webapp/assets/css/app");
    private static final Path ADMIN_SERVLETS =
            Path.of("src/main/java/com/hengtongan/computerstore/web/controller/admin");

    /** The row holding the five triggers. Renaming it must break the wrapping check. */
    private static final String ROW = ".admin-nav-items";

    /** The script that makes the menus open on hover. Server-relative, as in the appAssets array. */
    private static final String HOVER_JS = "/assets/js/dropdown-hover.js";

    /**
     * The opt-in class on each {@code .dropdown} wrapper, and the class on each
     * menu that the fade keys off. The two must move together: a wrapper marked
     * but an unmarked menu gets the timing with no animation, and a menu marked
     * with an unmarked wrapper sits permanently at opacity 0.
     */
    private static final String HOVERABLE = "dropdown-hover";
    private static final String HOVER_MENU = "dropdown-hover-menu";

    /**
     * The one admin section deliberately not in the sub-nav. It is an
     * {@code audit_logs} window -- a Settings/Security destination in every
     * mainstream panel -- and the store has no admin settings page to nest it
     * under yet, so it sits in the profile dropdown in {@code header.jspf} behind
     * an {@code isAdmin} check.
     */
    private static final String RELOCATED = "/admin/history";

    /** {@code <c:set var="_inCatalog" value="..."/>} */
    private static final Pattern FLAG_DEFINITION = Pattern.compile(
            "<c:set\\s+var=\"(_in[A-Za-z]+)\"\\s+value=\"([^\"]*)\"\\s*/?>");

    /** The first {@code ${_inX} a trigger's class and aria-current consult. */
    private static final Pattern TRIGGER_FLAG = Pattern.compile("\\$\\{(_in[A-Za-z]+)\\b");

    /** A trigger and the menu it opens, from the trigger to that menu's closing ul. */
    private static final Pattern GROUP =
            Pattern.compile("<a class=\"nav-link dropdown-toggle.*?</ul>", Pattern.DOTALL);

    /** {@code href=".../admin/orders"} */
    private static final Pattern HREF =
            Pattern.compile("href=\"[^\"]*?(/admin(?:/[A-Za-z0-9._-]+)*)\"");

    /** A single-quoted string, which is how EL spells a path inside a condition. */
    private static final Pattern SINGLE_QUOTED = Pattern.compile("'([^']+)'");

    /** {@code @WebServlet("/admin/orders")}, brace form included. */
    private static final Pattern WEBSERVLET = Pattern.compile(
            "@WebServlet\\s*\\(\\s*\\{?\\s*((?:\"[^\"]*\"\\s*,?\\s*)+)\\}?\\s*\\)");

    /** {@code class="..."} attribute values. */
    private static final Pattern CLASS_ATTR = Pattern.compile("class=\"([^\"]*)\"");

    /** {@code overflow: hidden|auto|scroll|clip} -- anything that clips a descendant. */
    private static final Pattern CLIPPING = Pattern.compile(
            "(?m)(?:^|;)\\s*overflow(?:-[xy])?\\s*:\\s*(hidden|auto|scroll|clip)\\s*[;!]");

    // ------------------------------------------------------------------
    // The rules, against the real files.
    // ------------------------------------------------------------------

    @Test
    void nothingBetweenTheRowAndTheMenuClipsIt() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path css : appStylesheets()) {
            for (String[] rule : adminNavRules(read(css))) {
                // A dropdown-item may legitimately clip its own text; an ancestor may
                // not, and that difference is the whole failure mode.
                if (rule[0].contains("dropdown-item")) continue;

                Matcher m = CLIPPING.matcher(rule[1]);
                if (m.find()) {
                    violations.add(css.getFileName() + " `" + rule[0].trim() + "` sets "
                            + m.group(0).strip().replaceAll("\\s+", " ") + " -- a dropdown menu is "
                            + "absolutely positioned, so this clips it to the element's own box");
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "the admin nav must not clip its own dropdown "
                + "menus. Any overflow other than visible forces overflow-y to auto whenever "
                + "overflow-x is not visible, which cuts an absolutely positioned menu off at the "
                + "row's edge: it opens and is immediately invisible, on every admin page, with "
                + "nothing logged.\n  " + String.join("\n  ", violations));
    }

    @Test
    void theRowWrapsAndIsNotPinnedToOneLine() throws IOException {
        // Two halves that have to agree. The stylesheet says `flex-wrap: wrap`, and
        // the fragment's class list does NOT carry `flex-nowrap` -- the Bootstrap
        // utility is `flex-wrap: nowrap !important`, so a stray `flex-nowrap` would
        // beat the stylesheet and reintroduce the horizontal overflow that the check
        // above exists to forbid, with that check still green.
        StringBuilder all = new StringBuilder();
        for (Path css : appStylesheets()) {
            all.append(stripComments(read(css))).append('\n');
        }
        assertTrue(wraps(all.toString(), ROW),
                () -> "no rule sets flex-wrap: wrap on " + ROW + ", so a section that does not "
                        + "fit has nowhere to go and the row overflows horizontally again -- which "
                        + "the overflow check above would then have to fail");

        assertTrue(!pinnedToOneLine(read(ADMIN_NAV)),
                () -> ADMIN_NAV + " puts flex-nowrap in a class list. That Bootstrap utility is "
                        + "flex-wrap: nowrap !important, so it overrides the stylesheet's "
                        + "flex-wrap: wrap: the row overflows again while every other check here "
                        + "stays green. Delete the utility -- wrapping is the intended behaviour.");
    }

    @Test
    void everyGroupTriggerLightsUpForExactlyItsOwnSections() throws IOException {
        String nav = read(ADMIN_NAV);
        Map<String, Set<String>> flags = definedFlags(nav);
        List<String> groups = groups(nav);
        List<String> problems = new ArrayList<>();

        assertTrue(groups.size() >= 4, () -> "expected the grouped triggers (Catalog, Sales, "
                + "Customers, Insights), found " + groups.size() + ". A silent zero or one "
                + "here would leave every group below unchecked, so this assertion is the guard on "
                + "the guard.");

        for (String group : groups) {
            String flag = triggerFlagOf(group);
            Set<String> leaves = pathsIn(HREF, group);
            Set<String> claimed = flags.get(flag);

            if (claimed == null) {
                problems.add("a trigger reads " + flag + " but no <c:set> defines it (defined: "
                        + flags.keySet() + "). EL renders an undefined variable as false, so that "
                        + "group would never highlight on any page.");
                continue;
            }
            if (!claimed.equals(leaves)) {
                Set<String> unhighlighted = difference(leaves, claimed);
                Set<String> foreign = difference(claimed, leaves);
                problems.add(flag + " names " + claimed + " but its own menu holds " + leaves
                        + (unhighlighted.isEmpty() ? "" : " -- never highlighted: " + unhighlighted)
                        + (foreign.isEmpty() ? "" : " -- would highlight pages in another group: " + foreign));
            }
        }

        assertTrue(problems.isEmpty(), () -> "a group's trigger flag and its own menu disagree.\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void everyGroupFlagIsDefinedBeforeItsTriggerUsesIt() throws IOException {
        String nav = read(ADMIN_NAV);
        List<String> problems = new ArrayList<>();

        for (String group : groups(nav)) {
            String flag = triggerFlagOf(group);
            int defined = nav.indexOf("<c:set var=\"" + flag + "\"");
            int used = nav.indexOf(group);
            if (defined < 0) {
                problems.add(flag + " is used but never defined");
            } else if (defined > used) {
                problems.add(flag + " is defined at offset " + defined + " but first used at " + used
                        + ", so the first render sees it undefined and the group never highlights");
            }
        }

        assertTrue(problems.isEmpty(), () -> "define every group flag in one block above the nav:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void everyAdminSectionIsReachableFromTheNavExactlyOnce() throws IOException {
        Set<String> servlets = adminServletPaths();
        Set<String> links = pathsIn(HREF, read(ADMIN_NAV));

        assertTrue(servlets.size() > 5, () -> "read only " + servlets.size() + " admin servlet "
                + "mappings out of " + ADMIN_SERVLETS + ". A silent zero or one here would let both "
                + "comparisons below pass on a near-empty set and make the test useless.");

        // The relocated section is expected to be absent here, so it is excluded from
        // the "nothing is orphaned" comparison rather than silently tolerated there.
        Set<String> reachable = new LinkedHashSet<>(links);
        reachable.add(RELOCATED);

        Set<String> missing = difference(servlets, reachable);
        assertTrue(missing.isEmpty(), () -> "these admin sections are served but no link to them "
                + "exists in " + ADMIN_NAV + " or in the profile dropdown, so they are reachable "
                + "only by typing the URL:\n  " + String.join("\n  ", missing));

        Set<String> dead = difference(links, servlets);
        assertTrue(dead.isEmpty(), () -> ADMIN_NAV + " links to " + dead + ", which no servlet in "
                + ADMIN_SERVLETS + " is mapped to. A link with no servlet behind it is a 404, and "
                + "one whose prefix is misspelled is dead to every reader of this file.");

        Set<String> expected = difference(servlets, Set.of(RELOCATED));
        assertEquals(expected, links,
                () -> "the nav must hold every admin section exactly once -- all of them except "
                        + RELOCATED + ", which lives in the profile dropdown. Expected " + expected
                        + ", nav has " + links);
    }

    @Test
    void theRelocatedSectionIsOutOfTheSubNavAndBehindAnAdminCheckInTheTopbar() throws IOException {
        assertTrue(!read(ADMIN_NAV).contains(RELOCATED),
                () -> RELOCATED + " is back in the admin sub-nav, which is only right if the "
                        + "grouping is being abandoned. It is a Settings/Security destination; if it "
                        + "belongs in the panel, delete this test and say why in its place.");

        String header = read(HEADER);
        int link = header.indexOf(RELOCATED);
        assertTrue(link >= 0, () -> RELOCATED + " is in neither the sub-nav nor " + HEADER
                + ". Taking it out of the panel without putting it anywhere leaves the audit log "
                + "reachable only by typing the URL.");

        // The guard must be the nearest preceding <c:if> and nothing may close it in
        // between -- the same shape AdminDashboardNavTrimTest checks.
        String before = header.substring(Math.max(0, link - 600), link);
        int guard = before.lastIndexOf("<c:if");
        assertTrue(guard >= 0 && before.substring(guard).contains("isAdmin()"),
                () -> RELOCATED + " in " + HEADER + " is not inside an isAdmin() check. Without one, "
                        + "a customer gets an admin link in their profile menu that 404s for them, "
                        + "which is both a broken link and a disclosure of what exists.");
    }

    @Test
    void theModerationCountRidesAGroupTrigger() throws IOException {
        // Inside a closed menu a badge on the Reviews leaf is never on screen, which is
        // the only reason the nav carries the count at all.
        List<String> problems = new ArrayList<>();
        for (String group : groups(read(ADMIN_NAV))) {
            boolean customers = "_inCustomers".equals(triggerFlagOf(group));
            boolean onTrigger = group.substring(0, group.indexOf("<ul")).contains("data-live-review-count");
            if (customers && !onTrigger) {
                problems.add("the Customers trigger carries no [data-live-review-count] badge, so a "
                        + "pending-review count is only visible after opening the menu");
            }
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n  ", problems));
    }

    @Test
    void theHoverScriptIsLoadedByTheFragmentThatNeedsIt() throws IOException {
        // The menus open on hover because this file is loaded, and nothing else
        // in the app pulls it in. A rename or a dropped include leaves the nav
        // working -- it just silently goes back to click-only, which reads as
        // "the hover was removed" rather than as a broken file.
        //
        // header.jspf, not admin-nav.jspf: the topbar profile menu is a hover
        // dropdown too and is the only one a customer gets, and admin-nav.jspf is
        // included on admin pages alone. Loading it from there would leave the
        // script absent from every customer page, so the one dropdown customers
        // have would be the one that does not open on hover.
        String jspf = read(HEADER);
        assertTrue(jspf.contains(HOVER_JS),
                () -> HEADER + " does not load " + HOVER_JS + ", so every dropdown falls back "
                        + "to click-to-open with no other symptom.");
    }

    @Test
    void theHoverScriptExistsWhereTheFragmentPointsAtIt() {
        // A reference to a file that is not on disk is a 404, and a 404 on a
        // deferred script is silent: the page works, the feature does not.
        assertTrue(Files.isRegularFile(Path.of("src/main/webapp" + HOVER_JS)),
                () -> HOVER_JS + " is referenced by " + HEADER + " but is not on disk.");
    }

    @Test
    void everyHoverableDropdownCarriesBothHooks() throws IOException {
        // dropdown-hover.js acts on the wrapper (.dropdown-hover) and the fade
        // keys off the menu (.dropdown-hover-menu). Half a pair fails quietly in
        // the direction that looks least like a bug: a wrapper with no fade gets
        // the timing with no animation, and a menu with no wrapper sits at
        // opacity 0 forever -- a menu that cannot be reached at all, on both the
        // admin bar and the customer's profile menu.
        List<String> problems = new ArrayList<>();
        for (Map.Entry<Path, String> page : List.of(
                Map.entry(ADMIN_NAV, stripJspComments(read(ADMIN_NAV))),
                Map.entry(HEADER, stripJspComments(read(HEADER))))) {
            problems.addAll(hookProblems(page.getKey().toString(), page.getValue()));
        }

        // The customer side is the point of the script being shared, so it is
        // checked by name rather than left to the pairing above: the profile menu
        // has to be opted in, or a customer gets a click-only menu.
        if (!read(HEADER).contains(HOVERABLE)) {
            problems.add(HEADER + " opts no dropdown in, so the customer profile menu is "
                    + "click-only while the admin bar opens on hover");
        }
        if (!read(HEADER).contains(HOVER_MENU)) {
            problems.add(HEADER + " has no " + HOVER_MENU + " menu, so the profile menu cannot fade");
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n  ", problems));
    }

    /**
     * Reports every {@code .dropdown} wrapper in {@code jspf} whose two hooks
     * disagree: a wrapper carrying {@code .dropdown-hover} whose menu lacks
     * {@code .dropdown-hover-menu} (hover timing with no animation), or the
     * reverse (a menu at {@code opacity: 0} forever, unreachable).
     *
     * <p>Both wrapper tags are matched: the admin bar uses {@code <div class="nav-item
     * dropdown">} and the topbar profile menu uses {@code <li>}, so matching only
     * the div would leave the customer side unchecked -- which is exactly the half
     * a customer sees.
     *
     * <p>The wrapper's own closing tag is deliberately not used to bound the search
     * for the menu, because it cannot be found by a non-greedy scan: the profile
     * trigger contains a {@code <div class="nav-profile-avatar">}, so a
     * {@code (.*?)</(?:div|li)>} scan stops at that inner {@code </div>} -- several
     * lines above the menu -- and the dropdown is then reported as having no menu
     * and skipped. That failure is silent and it skipped the customer menu, which
     * is the one this whole check exists to cover. Instead the search runs from one
     * wrapper's opening tag to the next wrapper's opening tag, and takes the first
     * menu in between: a dropdown's menu is always the first {@code <ul>} after its
     * trigger, and the next wrapper's tag bounds the region without needing the
     * tags to balance.
     */
    private static List<String> hookProblems(String label, String jspf) {
        List<String> problems = new ArrayList<>();
        Matcher open = Pattern.compile("<(?:div|li) class=\"nav-item dropdown([^\"]*)\">")
                .matcher(jspf);
        List<int[]> starts = new ArrayList<>();
        List<String> extras = new ArrayList<>();
        while (open.find()) {
            starts.add(new int[] { open.start(), open.end() });
            extras.add(open.group(1));
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i)[1];
            int to = i + 1 < starts.size() ? starts.get(i + 1)[0] : jspf.length();
            Matcher menu = Pattern.compile("<ul class=\"([^\"]*)\"").matcher(jspf);
            if (!menu.find(from) || menu.end() > to) continue;
            boolean wrapperHooked = carries(extras.get(i), HOVERABLE);
            boolean menuHooked = carries(menu.group(1), HOVER_MENU);
            if (wrapperHooked != menuHooked) {
                problems.add(label + ": a dropdown whose wrapper is "
                        + (wrapperHooked ? "opted in but whose menu is not" : "not opted in but whose menu is")
                        + " -- the two hooks have to move together, or "
                        + (wrapperHooked ? "the menu opens with no animation"
                                         : "the menu sits at opacity 0 and cannot be opened at all"));
            }
        }
        return problems;
    }

    /** True when {@code classList} contains {@code token}, ignoring surrounding space. */
    private static boolean carries(String classList, String token) {
        return List.of(classList.trim().split("\\s+")).contains(token);
    }

    @Test
    void theMenusFadeWithoutDependingOnADisplayToggle() throws IOException {
        // A menu that Bootstrap hides with `display: none` cannot animate: the
        // element is not rendered in the closed state, so there is no before
        // value to transition from. The rules have to hide it with visibility
        // instead, or the hover feels like a snap with no easing at all.
        StringBuilder all = new StringBuilder();
        for (Path css : appStylesheets()) {
            all.append(stripComments(read(css))).append('\n');
        }
        String clean = all.toString();

        assertTrue(hidesWithVisibility(clean),
                () -> "no rule hides a " + HOVER_MENU + " with visibility: hidden. Without it the "
                        + "menu can only be hidden by display: none, which cannot animate, and the "
                        + "open/close is a snap.");

        assertTrue(fadesToVisible(clean),
                () -> "no rule gives ." + HOVER_MENU + ".show an opacity, so the menu has nothing "
                        + "to fade to and the open is instant however the close is styled.");
    }

    // ------------------------------------------------------------------
    // Self-tests: prove each check above actually rejects the broken shape.
    // A lint that cannot fail is not a lint.
    // ------------------------------------------------------------------

    @Test
    void aClippingOverflowOnTheRowIsDetected() {
        // Exactly the shape the grouped nav had to avoid: the scroller that was there
        // before, left in place under the new class name.
        String css = """
                .admin-nav .admin-nav-items {
                    flex-wrap: wrap;
                    overflow-x: auto;
                    overflow-y: hidden;
                }
                """;
        assertTrue(!nothingClips(css), "the pre-grouping scroller shape must be reported");
    }

    @Test
    void aClippingOverflowOnTheNavItselfIsDetected() {
        assertTrue(!nothingClips(".admin-nav { overflow: hidden; }"),
                "clipping on the nav element clips the menus just as surely as on the row");
    }

    @Test
    void aClippingOverflowOnTheMenuWrapperIsDetected() {
        // A plausible-looking "tidy up" that reintroduces the bug one element further
        // up the tree, which is why the check walks every admin-nav rule, not one.
        assertTrue(!nothingClips(".admin-nav .admin-nav-menu { max-height: 60vh; overflow: auto; }"),
                "a clipped menu wrapper must be reported too");
    }

    @Test
    void visibleAndItemLevelOverflowAreAccepted() {
        // A dropdown-item may legitimately clip its own text; its ancestors may not.
        String css = """
                .admin-nav { min-height: 54px; }
                .admin-nav .admin-nav-items { flex-wrap: wrap; }
                .admin-nav .admin-nav-menu { margin-top: .4rem; }
                .admin-nav .admin-nav-menu .dropdown-item { overflow: hidden; text-overflow: ellipsis; }
                .admin-nav .container { min-height: 54px; }
                """;
        assertTrue(nothingClips(css), "this stylesheet must be accepted or the check is too broad");
    }

    @Test
    void aCommentedOutOverflowIsNotFlagged() {
        String css = """
                .admin-nav .admin-nav-items {
                    flex-wrap: wrap;
                    /* overflow-x: auto; -- considered and rejected, it clips the menus */
                }
                """;
        assertTrue(nothingClips(css), "a declaration inside a comment is not a declaration");
    }

    @Test
    void anOverflowInsideAMediaQueryIsDetected() {
        // A responsive "let the row scroll on phones" override is the most likely way
        // the scroller comes back, and it lands inside a @media block. Descending into
        // at-rules is what stops that from slipping through.
        String css = """
                @media (max-width: 991.98px) {
                    .admin-nav .admin-nav-items {
                        flex-wrap: nowrap;
                        overflow-x: auto;
                    }
                }
                """;
        assertTrue(!nothingClips(css), "a clipping rule nested in a @media block must be reported");
    }

    @Test
    void theRealStylesheetDoesNotClip() throws IOException {
        // Proves the real rules are accepted, so the passing check above is not simply
        // finding nothing to look at.
        assertTrue(nothingClips(String.join("\n",
                appStylesheets().stream().map(AdminNavGroupingTest::read).toList())),
                "the real admin-nav rules must be clean, otherwise the check is measuring nothing");
    }

    @Test
    void aFlexWrapWrapRuleAndNoUtilityAreAccepted() {
        String css = ".admin-nav .admin-nav-items { flex-wrap: wrap; align-items: center; }";
        String jspf = "<div class=\"navbar-nav flex-row gap-1 admin-nav-items\">";
        assertTrue(wraps(css, ROW), "flex-wrap: wrap on the row must be accepted");
        assertTrue(!pinnedToOneLine(jspf), "a class list without flex-nowrap must be accepted");
    }

    @Test
    void aMissingFlexWrapRuleIsDetected() {
        assertTrue(!wraps(".admin-nav .admin-nav-items { min-width: 0; }", ROW),
                "a row with no flex-wrap must be reported");
    }

    @Test
    void aFlexNowrapUtilityIsDetected() {
        String jspf = "<div class=\"navbar-nav flex-row flex-nowrap gap-1 admin-nav-items\">";
        assertTrue(pinnedToOneLine(jspf),
                "flex-nowrap in a class list must be reported: it is !important and beats the "
                        + "stylesheet, which is exactly why it needs its own check");
    }

    @Test
    void aMenuSectionMissingFromItsTriggerFlagIsDetected() {
        String nav = """
                <c:set var="_inCustomers" value="${fn:startsWith(_sp, '/admin/users') or fn:startsWith(_sp, '/admin/support')}"/>
                <div class="nav-item dropdown">
                <a class="nav-link dropdown-toggle${_inCustomers ? ' active' : ''}" href="#">Customers</a>
                <ul class="dropdown-menu">
                  <li><a href="/admin/users">Users</a></li>
                  <li><a href="/admin/reviews">Reviews</a></li>
                  <li><a href="/admin/support">Support</a></li>
                </ul>
                </div>
                """;
        Set<String> claimed = definedFlags(nav).get("_inCustomers");
        Set<String> leaves = pathsIn(HREF, groups(nav).get(0));

        assertTrue(leaves.contains("/admin/reviews"), "fixture must put Reviews in the menu");
        assertTrue(!claimed.contains("/admin/reviews"),
                "fixture must leave Reviews out of the flag, or this self-test would pass for "
                        + "the wrong reason");
        assertTrue(!claimed.equals(leaves), "a menu holding a section its trigger omits must be reported");
    }

    @Test
    void aTriggerFlagNamedByAnotherGroupIsDetected() {
        // The dangerous shape: the sets are the right size but attached to the wrong
        // group, so one page lights up two triggers and another lights up none.
        String nav = """
                <c:set var="_inSales" value="${fn:startsWith(_sp, '/admin/reports')}"/>
                <div class="nav-item dropdown">
                <a class="nav-link dropdown-toggle${_inSales ? ' active' : ''}" href="#">Sales</a>
                <ul class="dropdown-menu"><li><a href="/admin/orders">Orders</a></li></ul>
                </div>
                """;
        Set<String> claimed = definedFlags(nav).get("_inSales");
        Set<String> leaves = pathsIn(HREF, groups(nav).get(0));
        assertTrue(!claimed.equals(leaves), "Sales' flag naming Reports while its menu holds Orders "
                + "must be reported");
    }

    @Test
    void aTriggerWithNoFlagDefinitionIsDetected() {
        String nav = """
                <div class="nav-item dropdown">
                <a class="nav-link dropdown-toggle${_inSales ? ' active' : ''}" href="#">Sales</a>
                <ul class="dropdown-menu"><li><a href="/admin/orders">Orders</a></li></ul>
                </div>
                """;
        assertTrue(!definedFlags(nav).containsKey("_inSales"),
                "an undefined flag must read as absent so the trigger check reports it");
    }

    @Test
    void aTriggerThatMentionsNoFlagIsRejected() {
        String group = """
                <a class="nav-link dropdown-toggle" href="#">Sales</a>
                <ul class="dropdown-menu"><li><a href="/admin/orders">Orders</a></li></ul>
                """;
        boolean threw = false;
        try {
            triggerFlagOf(group);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, "a trigger that names no _inX flag can never highlight, so the read must "
                + "fail loudly rather than return a flag that happens to be absent");
    }

    @Test
    void aFlagDefinedAboveItsUseIsAccepted() {
        String nav = """
                <c:set var="_inCatalog" value="${fn:startsWith(_sp, '/admin/products')}"/>
                <a class="nav-link dropdown-toggle${_inCatalog ? ' active' : ''}" href="#">Catalog</a>
                <ul class="dropdown-menu"><li><a href="/admin/products">Products</a></li></ul>
                """;
        String flag = triggerFlagOf(groups(nav).get(0));
        assertTrue(definedFlags(nav).containsKey(flag), "a flag defined above its use must be accepted");
        assertTrue(nav.indexOf("<c:set var=\"" + flag + "\"") < nav.indexOf("${" + flag),
                "and the definition must come first");
    }

    @Test
    void aDisplayHiddenOnlyMenuIsRejected() {
        // The shape this replaces: correct in every other respect, but the menu
        // can only be hidden with display, so it snaps rather than animates.
        String css = """
                .admin-nav .admin-nav-menu { min-width: 12.5rem; }
                .dropdown-menu.show { display: block; }
                """;
        assertTrue(!hidesWithVisibility(css),
                "a menu hidden only by display must be reported: it cannot animate");
    }

    @Test
    void aMenuWithNoOpacityOnTheOpenStateIsRejected() {
        // Scoped like the real rules, so this fails for the reason it is about --
        // the missing opacity on the open state -- and not for the missing scope.
        String css = """
                @media (min-width: 992px) {
                    .dropdown-hover-menu { visibility: hidden; opacity: 0; }
                    .dropdown-hover-menu.show { visibility: visible; }
                }
                """;
        assertTrue(hidesWithVisibility(css), "fixture must hide the closed menu");
        assertTrue(!fadesToVisible(css), "a show rule with no opacity must be reported");
    }

    @Test
    void aVisibilityAndOpacityPairScopedToWideViewportsIsAccepted() {
        String css = """
                .admin-nav .admin-nav-menu { min-width: 12.5rem; }
                @media (min-width: 992px) {
                    .dropdown-hover-menu {
                        display: block;
                        visibility: hidden;
                        opacity: 0;
                        transition: opacity .16s ease, visibility 0s linear .16s;
                    }
                    .dropdown-hover-menu.show {
                        visibility: visible;
                        opacity: 1;
                        transition: opacity .16s ease, visibility 0s;
                    }
                }
                """;
        assertTrue(hidesWithVisibility(css), "hiding a closed menu with visibility must be accepted");
        assertTrue(fadesToVisible(css), "fading the open menu to opacity 1 must be accepted");
    }

    @Test
    void anUnscopedAlwaysDisplayedMenuIsRejected() {
        // The exact regression: the fade rules left at the top level, so below
        // 992px -- where Bootstrap makes the menu position:static -- every closed
        // menu is a 176px box in the navbar's normal flow and the bar is five
        // lines tall on every phone and tablet page.
        String css = """
                .dropdown-hover-menu {
                    display: block;
                    visibility: hidden;
                    opacity: 0;
                }
                .dropdown-hover-menu.show {
                    visibility: visible;
                    opacity: 1;
                }
                """;
        assertTrue(!hidesWithVisibility(css),
                "a menu laid out at every width must be reported: Bootstrap collapses the navbar "
                        + "below 992px, where position:static puts the always-displayed menu into the "
                        + "normal flow");
        assertTrue(!fadesToVisible(css), "and the open-state rule must be scoped the same way");
    }

    @Test
    void aFadeScopedToNarrowViewportsOnlyIsRejected() {
        // Scoped, but the wrong way round: a min-width is what makes the always
        // -displayed menu safe, so a max-width block would apply the fade exactly
        // where the menu is static and in the flow.
        String css = """
                @media (max-width: 991.98px) {
                    .dropdown-hover-menu {
                        display: block;
                        visibility: hidden;
                        opacity: 0;
                    }
                    .dropdown-hover-menu.show { opacity: 1; }
                }
                """;
        assertTrue(!hidesWithVisibility(css), "a max-width fade block must be reported");
        assertTrue(!fadesToVisible(css), "a max-width fade block must be reported");
    }

    @Test
    void aMissingHoverScriptIncludeIsDetected() {
        // The check is a substring test on the fragment, so prove it can come out
        // false rather than merely agreeing with the real file.
        String withoutIt = "<div class=\"admin-nav-items\"><a href=\"#\">Catalog</a></div>";
        assertTrue(!withoutIt.contains(HOVER_JS),
                "a fragment with no hover script must not read as having one");
    }

    @Test
    void aMatchedPairOfHooksIsAccepted() {
        // Both wrapper shapes, because the real markup uses each in a different
        // place: <div> for the admin bar, <li> for the topbar profile menu.
        assertTrue(hookProblems("div", """
                        <div class="nav-item dropdown dropdown-hover">
                            <a class="dropdown-toggle" href="#">Catalog</a>
                            <ul class="dropdown-menu admin-nav-menu dropdown-hover-menu"><li>x</li></ul>
                        </div>
                        """).isEmpty(), "a div wrapper with both hooks must be accepted");

        assertTrue(hookProblems("li", """
                        <li class="nav-item dropdown dropdown-hover">
                            <a class="nav-profile-btn dropdown-toggle" href="#">Profile</a>
                            <ul class="dropdown-menu dropdown-menu-end dropdown-hover-menu"><li>x</li></ul>
                        </li>
                        """).isEmpty(), "an li wrapper with both hooks must be accepted");
    }

    @Test
    void aWrapperWithoutTheMenuHookIsDetected() {
        // The wrapper opts into the timing, the menu has no fade class: opens on
        // hover, opens with no easing. Correct-looking enough to survive a review.
        //
        // The trigger holds a nested <div class="nav-profile-avatar">, as the real
        // one does, and that is the reason this fixture exists: a scan that ends
        // the wrapper at its first </div> stops there, never reaches the <ul>, and
        // reports nothing at all. With that shape in the fixture the check has to
        // still find the menu.
        String jspf = """
                <li class="nav-item dropdown dropdown-hover">
                    <a class="nav-profile-btn dropdown-toggle" href="#">
                        <div class="nav-profile-content">
                            <div class="nav-profile-avatar" aria-hidden="true"><img alt=""></div>
                            <div class="nav-profile-name">Sam</div>
                        </div>
                    </a>
                    <ul class="dropdown-menu dropdown-menu-end"><li><a href="/account">Account</a></li></ul>
                </li>
                """;
        assertEquals(1, hookProblems("header.jspf", jspf).size(),
                "a wrapper opted in with an unmarked menu must be reported even when the trigger "
                        + "contains nested divs");
    }

    @Test
    void aMenuWithoutTheWrapperHookIsDetected() {
        // The worse half: the menu carries the fade class but nothing drives it,
        // so it sits at opacity 0 with pointer-events: none and cannot be opened
        // at all -- a profile menu that looks present and does nothing.
        String jspf = """
                <li class="nav-item dropdown">
                    <a class="nav-profile-btn dropdown-toggle" href="#">
                        <div class="nav-profile-content">
                            <div class="nav-profile-avatar" aria-hidden="true"><img alt=""></div>
                        </div>
                    </a>
                    <ul class="dropdown-menu dropdown-menu-end dropdown-hover-menu"><li><a href="/account">Account</a></li></ul>
                </li>
                """;
        assertEquals(1, hookProblems("header.jspf", jspf).size(),
                "an unmarked wrapper with a marked menu must be reported");
    }

    @Test
    void aWrapperAndTheMenuBelongingToTheNextWrapperAreNotPaired() {
        // Two dropdowns side by side, the first opted in and the second not. The
        // menus must be paired with their own trigger, not with whichever
        // wrapper happens to be scanned first -- otherwise this reports two
        // problems where there is one, and the real one is lost in the noise.
        String jspf = """
                <div class="nav-item dropdown dropdown-hover">
                    <a class="dropdown-toggle" href="#">Catalog</a>
                    <ul class="dropdown-menu admin-nav-menu dropdown-hover-menu"><li>x</li></ul>
                </div>
                <div class="nav-item dropdown">
                    <a class="dropdown-toggle" href="#">Sales</a>
                    <ul class="dropdown-menu admin-nav-menu"><li>x</li></ul>
                </div>
                """;
        assertTrue(hookProblems("admin-nav.jspf", jspf).isEmpty(),
                "a fully paired dropdown next to a fully unpaired one is two correct dropdowns");
    }

    @Test
    void aDropdownWithNoMenuAtAllIsNotReported() {
        // Bootstrap's own markup for a menu built in JS, and a plain
        // <li class="dropdown"> with no dropdown-toggle. Neither has a menu to
        // pair with, so there is nothing to disagree about.
        assertTrue(hookProblems("x", "<li class=\"nav-item dropdown\"><a href=\"/x\">Plain</a></li>").isEmpty(),
                "a dropdown with no menu is not a half-paired hook");
    }

    // ------------------------------------------------------------------
    // Helpers. The predicates are separated from the @Test bodies so the
    // self-tests above can feed them source text directly.
    // ------------------------------------------------------------------

    /** Flag name to the paths its value expression names, in source order. */
    private static Map<String, Set<String>> definedFlags(String nav) {
        Map<String, Set<String>> flags = new LinkedHashMap<>();
        Matcher m = FLAG_DEFINITION.matcher(nav);
        while (m.find()) {
            flags.put(m.group(1), pathsIn(SINGLE_QUOTED, m.group(2)));
        }
        return flags;
    }

    /** Every trigger/menu block in the fragment, in source order. */
    private static List<String> groups(String nav) {
        List<String> out = new ArrayList<>();
        Matcher m = GROUP.matcher(nav);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** The flag a trigger's class and aria-current consult. */
    private static String triggerFlagOf(String group) {
        Matcher m = TRIGGER_FLAG.matcher(group);
        if (!m.find()) {
            throw new IllegalArgumentException("this group trigger names no _inX flag, so nothing can "
                    + "ever highlight it and the nav silently loses its active state:\n" + group);
        }
        return m.group(1);
    }

    /** The distinct paths a pattern finds in the text, in source order. */
    private static Set<String> pathsIn(Pattern pattern, String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Every {@code /admin...} path an admin servlet is mapped to. */
    private static Set<String> adminServletPaths() throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (Path servlet : adminServlets()) {
            Matcher m = WEBSERVLET.matcher(read(servlet));
            if (!m.find()) continue;
            for (String quoted : m.group(1).split(",")) {
                String path = quoted.replace("\"", "").trim();
                // DashboardServlet is mapped to both "/admin" and "/admin/"; they are
                // the same nav link, so the spelling that carries the trailing slash is
                // folded away rather than counted as a second, missing section.
                if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
                if (path.equals("/admin") || path.startsWith("/admin/")) out.add(path);
            }
        }
        return out;
    }

    private static List<Path> adminServlets() throws IOException {
        try (Stream<Path> files = Files.list(ADMIN_SERVLETS)) {
            return files.filter(p -> p.getFileName().toString().endsWith("Servlet.java"))
                    .sorted().toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** True when no rule in {@code css} clips a descendant of the admin nav. */
    private static boolean nothingClips(String css) {
        for (String[] rule : adminNavRules(stripComments(css))) {
            if (rule[0].contains("dropdown-item")) continue;
            if (CLIPPING.matcher(rule[1]).find()) return false;
        }
        return true;
    }

    /** True when some rule on {@code selector} sets {@code flex-wrap: wrap}. */
    private static boolean wraps(String css, String selector) {
        for (String[] rule : adminNavRules(stripComments(css))) {
            if (rule[0].contains(selector) && Pattern.compile("flex-wrap\\s*:\\s*wrap").matcher(rule[1]).find()) {
                return true;
            }
        }
        return false;
    }

    /** True when the closed hover menu is hidden with {@code visibility: hidden}. */
    private static boolean hidesWithVisibility(String clean) {
        return alwaysDisplayedRule(clean, "visibility:\\s*hidden") != null;
    }

    /** True when the open hover menu is given an {@code opacity} to fade to. */
    private static boolean fadesToVisible(String clean) {
        return alwaysDisplayedRule(clean, "opacity:\\s*1") != null;
    }

    /**
     * The block of CSS that sets {@code declaration} on a hover menu while keeping
     * it displayed, or {@code null} when no such block exists.
     *
     * <p>"While keeping it displayed" is the part that matters. The menu is laid out
     * at all times and hidden with visibility so it can animate, and that is only
     * safe while the menu is absolutely positioned. Both bars -- the admin sub-nav
     * and the site topbar -- are {@code navbar-expand-lg}, so below 992px
     * Bootstrap's collapsed-navbar rule makes the menu {@code position: static} at
     * a specificity that beats a plain class, and a menu that is always displayed
     * joins the normal flow: a 176px box under every trigger, on every phone and
     * tablet page, with the bar pushed to five lines. The styling that does this
     * therefore has to live inside a min-width media query, and this returns only a
     * block that is inside one.
     */
    private static String alwaysDisplayedRule(String clean, String declaration) {
        for (String[] rule : adminNavRules(clean)) {
            boolean onTheMenu = rule[0].contains(HOVER_MENU);
            boolean setsIt = Pattern.compile(declaration).matcher(rule[1]).find();
            if (onTheMenu && setsIt && rule[2].matches(".*@media[^\\{]*min-width.*")) {
                return rule[1];
            }
        }
        return null;
    }

    /** True when any class list in the fragment carries {@code flex-nowrap}. */
    private static boolean pinnedToOneLine(String jspf) {
        Matcher m = CLASS_ATTR.matcher(stripJspComments(jspf));
        while (m.find()) {
            for (String token : m.group(1).trim().split("\\s+")) {
                if (token.equals("flex-nowrap")) return true;
            }
        }
        return false;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> out = new LinkedHashSet<>(left);
        out.removeAll(right);
        return out;
    }

    /**
     * Whether a rule is one of the ones the checks here reason about.
     *
     * <p>Two families count. The {@code .admin-nav} rules carry the bar's layout
     * (wrapping, the menus' own sizing and shadows) and are scoped to the admin
     * bar. The {@code .dropdown-hover-menu} rules carry the shared fade, which
     * the topbar profile menu uses too, and so are deliberately not written under
     * {@code .admin-nav} -- they are matched on their own class.
     */
    private static boolean isWorthKeeping(String selector) {
        return selector.contains(".admin-nav") || selector.contains(HOVER_MENU);
    }

    /**
     * The admin-nav rules as {@code {selector, body, enclosing at-rules}} triples,
     * comments removed.
     *
     * <p>At-rule blocks are descended into rather than skipped: a rule nested in a
     * {@code @media} query is exactly where a responsive "let the row scroll on
     * phones" override would land, and that is one of the shapes this check has to
     * catch. An at-rule whose own selector names {@code .admin-nav} is kept as-is.
     */
    private static List<String[]> adminNavRules(String clean) {
        List<String[]> out = new ArrayList<>();
        collectRules(clean, out);
        return out;
    }

    private static void collectRules(String css, List<String[]> out) {
        collectRules(css, out, "");
    }

    /**
     * @param atRule the enclosing at-rule preludes, outermost first, or
     *               {@code ""} at the top level. Carried on each rule so a check
     *               can tell a global rule from one scoped to a width.
     */
    private static void collectRules(String css, List<String[]> out, String atRule) {
        StringBuilder selector = new StringBuilder();
        int i = 0;
        while (i < css.length()) {
            char c = css.charAt(i);
            if (c == '{') {
                int depth = 1;
                int j = i + 1;
                while (j < css.length() && depth > 0) {
                    char d = css.charAt(j);
                    if (d == '{') depth++;
                    else if (d == '}') depth--;
                    j++;
                }
                String found = selector.toString().trim();
                String body = css.substring(i + 1, j - 1);
                if (found.startsWith("@")) {
                    collectRules(body, out, atRule + found + " ");
                } else if (isWorthKeeping(found)) {
                    out.add(new String[] { found, body, atRule });
                }
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
    }

    private static String stripComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static String stripJspComments(String jspf) {
        return jspf.replaceAll("(?s)<%--.*?--%>", "");
    }

    private static List<Path> appStylesheets() throws IOException {
        try (Stream<Path> files = Files.list(APP_CSS_DIR)) {
            return files.filter(p -> p.toString().endsWith(".css")).sorted().toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + p, e);
        }
    }
}
