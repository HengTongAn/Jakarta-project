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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks that every view a servlet forwards to actually exists.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * {@code AdminTransactionsServlet} forwarded to
 * {@code /WEB-INF/views/admin/transactions/detail.jsp}, and that file did not exist.
 * The servlet compiled, the class loaded, the admin nav linked to the section happily,
 * and the whole test suite was green -- because nothing between the source file and a
 * live request ever looks. The page failed only when an admin clicked a transaction,
 * as a {@code ServletException} from the container: a 500 discovered by using the app.
 *
 * <p>A forward to a missing path is a runtime error, not a compile error, and no
 * compiler in this project resolves JSP paths against the filesystem. So the check has
 * to be made on the source.
 *
 * <h2>Why the whole tree is walked, not a fixed list</h2>
 *
 * A list of expected views would be a second thing to forget to update: adding a page
 * and forgetting the list produces the same 500 with a green build. Deriving the set
 * from the servlets themselves means the list cannot drift, and it costs nothing to
 * extend.
 *
 * <p>Both halves are needed. The servlets say which views are reachable, so a view
 * that exists but is dead code is not reported -- that is not this test's business.
 * What matters is the other direction: a view named by a servlet must be there.
 *
 * <h2>Why it is a source lint</h2>
 *
 * {@code mvn test} deliberately carries no container and no database, so this reads
 * files rather than deploying. See {@code docs/development/source-linting.md}.
 */
class ServletForwardTargetTest {

    /** Servlet sources, where forward targets are written. */
    private static final Path SERVLET_DIR = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/controller");

    /** The deployed document root; a forward path is absolute against it. */
    private static final Path WEBAPP = Path.of("src/main/webapp");

    private static final String VIEWS_DIR = "/WEB-INF/views/";

    /** {@code getRequestDispatcher("/WEB-INF/views/....")}. The literal has to be there. */
    private static final Pattern FORWARD =
            Pattern.compile("getRequestDispatcher\\s*\\(\\s*\"(" + Pattern.quote(VIEWS_DIR) + "[^\"]+)\"");

    @Test
    void everyViewAServletForwardsToExists() throws IOException {
        List<Path> servlets = servlets();
        assertTrue(servlets.size() > 5,
                "expected to find the servlet sources, found " + servlets.size() + " -- has the "
                        + "controller package moved? A silent zero here would make this test useless.");

        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (Path servlet : servlets) {
            String source = Files.readString(servlet, StandardCharsets.UTF_8);
            Matcher m = FORWARD.matcher(source);
            while (m.find()) {
                checked++;
                String view = m.group(1);
                Path viewFile = WEBAPP.resolve(view.substring(1)).normalize();
                if (!Files.isRegularFile(viewFile)) {
                    missing.add(servlet + " forwards to " + view + ", which is not a file at "
                            + viewFile);
                }
            }
        }

        assertTrue(checked > 0,
                "No servlet forwards to a view by its full \"/WEB-INF/views/...\" literal. Either "
                        + "that is no longer how views are reached -- in which case point this test "
                        + "at whatever replaced it -- or the pattern needs updating, because right "
                        + "now it would pass while checking nothing.");

        if (!missing.isEmpty()) {
            fail("Each of these is a 500 the first time the page is requested, and the build stays "
                    + "green until then:\n  " + String.join("\n  ", missing));
        }
    }

    /**
     * The views the transaction feature is supposed to be reachable through.
     *
     * <p>Not a general check -- {@link #everyViewAServletForwardsToExists()} covers any view a
     * servlet names, including these. This exists so that deleting a transaction page fails with
     * a message naming the feature, rather than only as "a view is missing". The ledger's list,
     * detail and two customer views are the ones worth being able to name in a failure.
     */
    @Test
    void theTransactionViewsAreAllPresent() {
        List<String> required = List.of(
                VIEWS_DIR + "admin/transactions/list.jsp",
                VIEWS_DIR + "admin/transactions/detail.jsp",
                VIEWS_DIR + "customer/account/transactions.jsp",
                VIEWS_DIR + "customer/account/transaction-detail.jsp");

        List<String> missing = required.stream()
                .filter(view -> !Files.isRegularFile(WEBAPP.resolve(view.substring(1))))
                .toList();

        if (!missing.isEmpty()) {
            fail("The transaction feature is missing views:\n  " + String.join("\n  ", missing));
        }
    }

    // ------------------------------------------------------------------
    // Self-test: prove the check actually rejects the broken shape.
    // A lint that cannot fail is not a lint.
    // ------------------------------------------------------------------

    @Test
    void theCheckerFlagsAForwardToAViewThatIsNotThere() {
        // A fabricated path, not a real one. An earlier version of this fixture asserted that
        // admin/transactions/detail.jsp was missing -- which is the defect this test was written
        // for -- and started failing the moment that file was added, because it had been testing
        // the state of the repository rather than the behaviour of the checker. Resolving the
        // path against the real document root means only the existence check is under test.
        String missing = "request.getRequestDispatcher(\"/WEB-INF/views/admin/transactions/"
                + "no-such-view.jsp\")";
        List<String> problems = missingViewsIn(missing);

        assertTrue(problems.size() == 1,
                "a forward to a view that does not exist must be reported, got: " + problems);
        assertTrue(problems.get(0).endsWith("no-such-view.jsp"), problems.get(0));

        // And the same shape is clean when the view really is there, so the checker is not simply
        // rejecting every literal it is handed.
        assertTrue(missingViewsIn(
                "request.getRequestDispatcher(\"/WEB-INF/views/admin/transactions/list.jsp\")").isEmpty(),
                "a forward to a view that does exist must not be reported");
    }

    @Test
    void theCheckerIgnoresAForwardBuiltAtRuntime() {
        // A target assembled from a variable cannot be checked on the source, and must not be
        // guessed at. This asserts the blind spot is still there rather than pretending it is
        // covered: if views start being forwarded by a computed path, this test still passes and
        // this one file stops protecting anything.
        assertTrue(FORWARD.matcher(
                        "request.getRequestDispatcher(viewPath).forward(request, response);").find() == false,
                "only literals are resolvable; a computed path is out of this check's reach");
    }

    /** The views in one forward statement that are not a file, resolved under the document root. */
    private static List<String> missingViewsIn(String source) {
        List<String> problems = new ArrayList<>();
        Matcher m = FORWARD.matcher(source);
        while (m.find()) {
            String view = m.group(1);
            if (!Files.isRegularFile(WEBAPP.resolve(view.substring(1)).normalize())) {
                problems.add(view);
            }
        }
        return problems;
    }

    private static List<Path> servlets() throws IOException {
        try (Stream<Path> paths = Files.walk(SERVLET_DIR)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}