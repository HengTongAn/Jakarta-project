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
 * Keeps the customer-facing transaction pages scoped to the signed-in customer.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * {@code TransactionService} offers two ways to read a transaction. {@code getTransaction(long)}
 * is keyed on the id alone and is correct for an admin. {@code getTransactionForUser(long, int)}
 * constrains on {@code user_id} as well, and is the only one a customer page may call.
 *
 * <p>The wrong one is a single token away and gives a working page: the detail renders, the id
 * comes from the URL, and one customer is looking at another's card payment. Nothing about it
 * looks broken on screen, which is why a code review is not a sufficient guard -- the error is
 * a plausible-looking line, not a typo.
 *
 * <h2>Why the unscoped list methods are named too</h2>
 *
 * {@code getAllTransactions} returns every customer's rows with no filter argument at all, so it
 * has no wrong-argument variant to catch. Naming it here is the whole defence for the list view.
 *
 * <h2>What is not checked</h2>
 *
 * That the customer page reads {@code userId} off the session rather than a request parameter.
 * That needs the servlet running. This test only holds the half that can be read off the source:
 * which service methods a {@code web/controller/customer} class is allowed to call.
 */
class CustomerTransactionScopeTest {

    private static final Path CUSTOMER_CONTROLLERS = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/controller/customer");

    /** Service methods that return rows for every customer, with no user argument to narrow by. */
    private static final List<String> UNSCOPED = List.of(
            "getTransaction", "getAllTransactions", "getTransactionStats", "getTransactionsByOrder");

    /** {@code transactionService().someMethod(}. */
    private static final Pattern CALL = Pattern.compile(
            "transactionService\\s*\\(\\s*\\)\\s*\\.\\s*([A-Za-z]+)\\s*\\(");

    @Test
    void customerControllersNeverCallAnUnscopedTransactionLookup() throws IOException {
        List<Path> controllers = controllers();
        assertTrue(controllers.size() > 5,
                "expected to find the customer controllers, found " + controllers.size()
                        + " -- has the package moved? A silent zero here would make this test useless.");

        List<String> offences = new ArrayList<>();
        for (Path controller : controllers) {
            for (String call : callsIn(Files.readString(controller, StandardCharsets.UTF_8))) {
                if (UNSCOPED.contains(call)) {
                    offences.add(controller.getFileName() + " calls transactionService()." + call
                            + "(), which is not scoped to a customer. Use the *ForUser variant.");
                }
            }
        }

        if (!offences.isEmpty()) {
            fail("These would show one customer another customer's payments:\n  "
                    + String.join("\n  ", offences));
        }
    }

    @Test
    void theCustomerTransactionPageUsesTheScopedLookup() throws IOException {
        // The positive counterpart. Without it the check above would also pass if the page had
        // been deleted outright, leaving /account/transactions serving nothing while the only
        // test guarding it stayed green.
        Path servlet = CUSTOMER_CONTROLLERS.resolve("CustomerTransactionsServlet.java");
        assertTrue(Files.isRegularFile(servlet),
                servlet + " is gone, so /account/transactions no longer has a controller");

        String source = Files.readString(servlet, StandardCharsets.UTF_8);
        assertTrue(source.contains("getTransactionForUser"),
                servlet + " must read a single transaction with getTransactionForUser, not "
                        + "getTransaction -- the latter returns any customer's row for a given id");
        assertTrue(source.contains("getTransactionsByUser"),
                servlet + " must list with getTransactionsByUser so the page shows only this "
                        + "customer's transactions");
    }

    @Test
    void theDetectorActuallyCatchesAnUnscopedCall() {
        assertTrue(offendingCalls("app().transactionService().getTransaction(42);").size() == 1,
                "getTransaction(id) is the id-only lookup and must be reported");
        assertTrue(offendingCalls("app().transactionService().getAllTransactions(null, null, 25, 0);")
                        .size() == 1,
                "getAllTransactions has no user argument at all and must be reported");
        assertTrue(offendingCalls("app().transactionService().getTransactionForUser(42, 7);").isEmpty(),
                "getTransactionForUser is constrained on the customer and must be accepted");
    }

    @Test
    void theDetectorIgnoresOtherServices() {
        // orderService() has its own user-scoped getters and its own unscoped ones. Flagging
        // every unscoped call on any service would bury the real signal under unrelated noise.
        assertTrue(offendingCalls("app().orderService().getOrder(42);").isEmpty(),
                "only transactionService calls are in scope for this rule");
    }

    private static List<String> offendingCalls(String source) {
        List<String> calls = callsIn(source);
        calls.removeIf(call -> !UNSCOPED.contains(call));
        return calls;
    }

    private static List<String> callsIn(String source) {
        List<String> calls = new ArrayList<>();
        Matcher m = CALL.matcher(source);
        while (m.find()) {
            calls.add(m.group(1));
        }
        return calls;
    }

    private static List<Path> controllers() throws IOException {
        try (Stream<Path> paths = Files.walk(CUSTOMER_CONTROLLERS)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}