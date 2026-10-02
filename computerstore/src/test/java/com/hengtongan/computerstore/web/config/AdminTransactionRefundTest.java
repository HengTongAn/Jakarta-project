package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards admin refunding of a transaction.
 *
 * <h2>The flow being guarded</h2>
 *
 * <pre>
 *   GET  /admin/transactions?id=N      -&gt; detail, Refund offered or a reason it is not
 *   POST /admin/transactions          -&gt; action=refund, transactionId=N
 *       service: REFUND/COMPLETED row on the order, guarded against a second refund
 *       caller:  order -&gt; REFUNDED (restocks), audited
 * </pre>
 *
 * <h2>Why most of these are source checks</h2>
 *
 * The dangerous part of a refund is not that it fails loudly, it is that it succeeds
 * twice, or succeeds once and leaves the order describing something else. Both are
 * decisions the source states and the running system only reveals: there is no
 * reachable database in this environment, so a behavioural test would either skip or
 * lie. Reading the source is what can actually be asserted here.
 *
 * <ul>
 *   <li><b>The double-refund guard runs inside the write transaction.</b> The single
 *       most expensive way this feature could be wrong is two admins refunding one
 *       payment from two tabs. The guard is only a guard if it is on the same
 *       connection as the insert it guards; a {@code SELECT} on a separate connection
 *       cannot see the uncommitted row.</li>
 *   <li><b>The payment row is not flipped to REFUNDED.</b> Two completed rows reading
 *       net zero is a ledger. Overwriting the payment destroys the fact that the
 *       customer paid, and takes the receipt with it, since the receipt lookup asks
 *       for a COMPLETED payment.</li>
 *   <li><b>No invented gateway reference.</b> No provider exposes a refund call, so
 *       the old {@code "REFUND-" + id} pattern stored a fabricated id in the one
 *       column a customer quotes to their bank.</li>
 *   <li><b>Refund rows do not masquerade as payments.</b> A refund is COMPLETED, so
 *       every "is this settled?" test on the customer side is true for one. Without a
 *       type check a customer gets a "Payment received" receipt for money coming back.</li>
 *   <li><b>The order is written after the ledger, and its failure is reported as a
 *       warning.</b> The reverse order would return stock against a payment that was
 *       never handed back.</li>
 * </ul>
 *
 * <h2>What is not checked</h2>
 *
 * That the refund inserts and commits, and that the JSP renders. Both need a database.
 * The SQL itself is asserted only by inspection, which is weaker than it looks and
 * worth stating plainly rather than letting a green suite imply otherwise.
 */
class AdminTransactionRefundTest {

    private static final Path SRC = Path.of("src/main/java/com/hengtongan/computerstore");
    private static final Path VIEWS = Path.of("src/main/webapp/WEB-INF/views");

    private static final Path SERVICE = SRC.resolve("core/service/TransactionService.java");
    private static final Path REPOSITORY = SRC.resolve("core/repository/TransactionRepository.java");
    private static final Path SERVLET = SRC.resolve(
            "web/controller/admin/AdminTransactionsServlet.java");
    private static final Path ADMIN_DETAIL = VIEWS.resolve("admin/transactions/detail.jsp");
    private static final Path RECEIPT = VIEWS.resolve("customer/account/receipt.jsp");
    private static final Path CUSTOMER_TX_LIST = VIEWS.resolve("customer/account/transactions.jsp");
    private static final Path CUSTOMER_TX_DETAIL = VIEWS.resolve(
            "customer/account/transaction-detail.jsp");

    // ------------------------------------------------------------ the service

    @Test
    void serviceRefusesToRefundAnythingButASettledPayment() throws IOException {
        String source = read(SERVICE);
        assertTrue(source.contains("refundPayment"),
                "TransactionService must expose the refund entry point");
        assertFalse(source.contains("createRefundTransaction"),
                "createRefundTransaction is the unguarded predecessor: it took a caller-supplied "
                        + "amount, a caller-supplied user id and a PENDING status, validated nothing, "
                        + "and had no callers. Two refund paths is the hazard, not one loose one.");
    }

    @Test
    void refundRejectsNonPaymentRows() throws IOException {
        String source = methodBody(read(SERVICE), "refundPayment");
        assertTrue(source.contains("TransactionType.PAYMENT"),
                "A refund row must not itself be refundable, or the admin URL mints money "
                        + "out of nothing rather than moving it");
    }

    @Test
    void refundRejectsUnsettledPayments() throws IOException {
        String source = methodBody(read(SERVICE), "refundPayment");
        assertTrue(source.contains("TransactionStatus.COMPLETED"),
                "A failed or pending payment never took the money, so refunding it would "
                        + "record a movement that never happened");
    }

    @Test
    void refundAmountCurrencyAndCustomerComeFromThePaymentNotTheCaller() throws IOException {
        String source = methodBody(read(SERVICE), "refundPayment");
        assertTrue(source.contains("setAmount(payment.getAmount())"),
                "The refunded amount has to be the amount that was paid");
        assertTrue(source.contains("setCurrency(payment.getCurrency())"),
                "Hardcoding USD would file a non-USD payment back under the wrong currency");
        assertTrue(source.contains("setUserId(payment.getUserId())"),
                "The customer id is NOT NULL and taken off the payment; a caller-supplied id "
                        + "would file the refund against somebody else's account");
    }

    @Test
    void refundRowIsCompletedNotPending() throws IOException {
        String source = methodBody(read(SERVICE), "refundPayment");
        assertTrue(source.contains("Transaction.TransactionType.REFUND"));
        // Nothing will call back to settle a refund row: there is no gateway callback
        // for it. A row left at PENDING reads as a payment that failed to go out.
        assertFalse(source.contains("TransactionStatus.PENDING"),
                "The refund row is completed as it is written; nothing settles it later");
    }

    @Test
    void refundInventsNoGatewayReference() throws IOException {
        String source = read(SERVICE);
        assertFalse(source.contains("\"REFUND-\""),
                "\"REFUND-\" + id fabricates a provider reference. No provider was called, so "
                        + "there is no reference to store, and this is the column a customer "
                        + "quotes to their bank.");
        assertTrue(source.contains("LEDGER"),
                "The gateway columns should say the movement was a ledger entry, rather than "
                        + "sitting empty and implying a confirmation that never arrived");
    }

    @Test
    void refundDoesNotRewriteTheOriginalPayment() throws IOException {
        String source = methodBody(read(SERVICE), "refundPayment");
        assertFalse(source.contains("updateStatus(c, transactionId"),
                "Flipping the payment to REFUNDED erases the fact that the customer paid and "
                        + "removes the receipt, which findSettledPaymentForUser looks up as a "
                        + "COMPLETED payment. The refund is a second movement, not a correction.");
    }

    // ------------------------------------------------------ the guard itself

    @Test
    void doubleRefundGuardLivesInsideTheWriteTransaction() throws IOException {
        String service = methodBody(read(SERVICE), "refundPayment");
        int commit = service.indexOf("c.commit()");
        int guard = service.indexOf("hasSettledRefund");
        assertTrue(guard > 0, "The refund must check for an existing refund");
        assertTrue(guard < commit, "The guard must run before the commit, inside the same "
                + "transaction. A check that cannot see the insert it guards is not a check, "
                + "and two admins in two tabs each pass a guard that read a separate connection.");

        // Ordering alone is not enough. A guard that is mentioned but not acted on --
        // commented out, short-circuited, its result discarded -- passes every
        // positional check above while refunding every order twice. So the comment
        // and string state of the line is recovered rather than guessed at, the way
        // methodBody does it, or this check would trip on the prose above it.
        String lineBeforeGuard = lastStatementIn(service, guard);
        assertFalse(lineBeforeGuard.contains("//"),
                "The double-refund guard appears to be commented out");
        assertFalse(lineBeforeGuard.matches("(?s).*if \\(\\s*false\\s*&&.*"),
                "The double-refund guard is short-circuited by a constant false condition");
        assertTrue(lineBeforeGuard.contains("if ("),
                "The call to hasSettledRefund must be the condition of an if, not a bare "
                        + "statement. Its result has to decide whether the refund proceeds.");
    }

    @Test
    void guardQueryTakesTheCallersConnection() throws IOException {
        String repository = read(REPOSITORY);
        String signature = signatureOf(repository, "hasSettledRefund");
        assertTrue(signature.contains("Connection c"),
                "hasSettledRefund must take the caller's connection. Opening its own is what "
                        + "makes the double-refund race possible.");
        String method = methodBody(repository, "hasSettledRefund");
        assertFalse(method.contains("conn()"),
                "hasSettledRefund must not open its own connection");
        assertTrue(method.contains("'REFUND', 'PARTIAL_REFUND'"),
                "A partial refund is money back too, and must count towards the same guard");
        assertTrue(method.contains("'COMPLETED'"),
                "Only a settled refund has handed money back, so only a settled one blocks");
    }

    // -------------------------------------------------------------- the servlet

    @Test
    void refundPostIsReachableAndGuarded() throws IOException {
        String source = read(SERVLET);
        assertTrue(source.contains("protected void doPost"),
                "AdminTransactionsServlet must handle the refund POST");
        assertFalse(source.contains("operations are read-only"),
                "The read-only stub is gone; an admin can now act on a transaction");
        assertTrue(source.contains("\"refund\".equals(action)"),
                "The POST must be keyed on an explicit action so an unrelated POST cannot refund");
    }

    @Test
    void refundRejectsBeforeWritingAnything() throws IOException {
        String source = methodBody(read(SERVLET), "doPost");
        int check = source.indexOf("refundBlockedBecause");
        int refund = source.indexOf("refundPayment");
        assertTrue(check > 0 && refund > 0, "Both the check and the refund must be present");
        assertTrue(check < refund,
                "The precondition runs before the refund, so a doomed request never writes a "
                        + "refund row it would then have to report as half-done");
    }

    @Test
    void orderIsUpdatedAfterTheLedgerAndItsFailureIsNotFatal() throws IOException {
        String source = methodBody(read(SERVLET), "doPost");
        int refund = source.indexOf("refundPayment");
        int orderUpdate = source.indexOf("updateStatus");
        assertTrue(refund < orderUpdate,
                "The ledger row commits first. The reverse order would return stock to "
                        + "inventory against a payment that was never handed back.");
        assertTrue(source.contains("Flash.warning"),
                "A refund that recorded but did not move the order is a warning, not a success "
                        + "and not a hard error: the money did go back, and saying otherwise "
                        + "would push an admin to refund it a second time");
    }

    @Test
    void refundIsAuditedAndTheOrderIsTheRightOne() throws IOException {
        String source = methodBody(read(SERVLET), "doPost");
        assertTrue(source.contains("AuditLogger.logAdminAction"),
                "A refund moves money and must leave an admin-action record");
        assertTrue(source.contains("Order.Status.REFUNDED"));
        assertTrue(source.contains("payment.getOrderId()"),
                "The order comes from the payment being refunded, not from a form parameter, "
                        + "so the order and the refund cannot be pointed at different rows");
    }

    @Test
    void refundableCheckExplainsItselfRatherThanOnlyHidingTheButton() throws IOException {
        String source = read(SERVLET);
        String method = methodBody(source, "refundBlockedBecause");
        assertTrue(source.contains("refundBlockedReason"),
                "The reason must reach the view; a silently absent button is unreadable");
        assertTrue(method.contains("TransactionType.PAYMENT")
                        && method.contains("TransactionStatus.COMPLETED")
                        && method.contains("Order.Status.COMPLETED"),
                "Type, settled status and order state are all part of the decision");
        // The order rule is the one the service cannot enforce: restocking belongs to
        // OrderService, and it only runs from a completed order.
        assertTrue(method.contains("stock"),
                "The order-state rule should say it exists because of restocking, which is the "
                        + "reason an admin cannot refund a still-processing order");
    }

    @Test
    void detailViewReceivesTheRowsItRenders() throws IOException {
        String source = read(SERVLET);
        assertTrue(source.contains("setAttribute(\"orderTransactions\""),
                "The detail view renders an order's whole transaction list and nothing was "
                        + "setting it, so that table only ever showed its empty state -- and "
                        + "those rows are what reveals an already-refunded payment");
        assertTrue(source.contains("setAttribute(\"settledRefund\""));
        assertTrue(source.contains("setAttribute(\"refundBlockedReason\""));
    }

    @Test
    void findSettledRefundIgnoresUnsettledRefundRows() throws IOException {
        String source = methodBody(read(SERVLET), "findSettledRefund");
        assertTrue(source.contains("TransactionStatus.COMPLETED"),
                "Only a completed refund has returned money, so only a completed one blocks "
                        + "a second refund");
        assertTrue(source.contains("PARTIAL_REFUND"),
                "A partial refund is money back and must be found, even though only whole "
                        + "refunds are issued today");
        assertTrue(source.contains("return null"),
                "An order with no refund must report none, not fall through to a row that was "
                        + "never a refund");
    }

    // -------------------------------------------------------- the customer side

    @Test
    void refundRowsAreNotOfferedAsPaymentReceipts() throws IOException {
        for (Path view : List.of(CUSTOMER_TX_LIST, CUSTOMER_TX_DETAIL, RECEIPT)) {
            String source = read(view);
            assertTrue(source.contains("'PAYMENT'"),
                    view + " must check the transaction type as well as the status before "
                            + "treating a row as a settled payment");
        }
    }

    @Test
    void receiptDoesNotClaimPaymentWasReceivedForARefund() throws IOException {
        String source = read(RECEIPT);
        assertTrue(source.contains("'REFUND'"),
                "A refund is COMPLETED, so the settled test alone passes it. The receipt needs "
                        + "its own branch or it prints \"Payment received\" and \"Total paid\" "
                        + "for money travelling the other way.");
        assertTrue(source.contains("Money returned to you"),
                "The refund branch should say what happened");
    }

    @Test
    void adminDetailRendersBothOutcomes() throws IOException {
        String source = read(ADMIN_DETAIL);
        assertTrue(source.contains("name=\"action\" value=\"refund\""),
                "The refund form must post the action the servlet switches on");
        assertTrue(source.contains("name=\"csrfToken\""),
                "CSRFProtectionFilter validates every POST and exempts nothing");
        assertTrue(source.contains("refundBlockedReason"),
                "When the button is absent the reason must be shown, not just omitted");
        assertTrue(source.contains("settledRefund"),
                "An already-refunded order should link to the refund that did it");
        assertTrue(source.contains("no provider"),
                "A refund must not look like a provider confirmation. The gateway columns are "
                        + "empty on purpose and the page has to say why.");
    }

    @Test
    void adminDetailShowsNoGatewayReferenceForARefund() throws IOException {
        String source = read(ADMIN_DETAIL);
        assertTrue(source.contains("Gateway refund ID"),
                "\"Not issued yet\" would be wrong for a refund -- nothing is pending, there was "
                        + "never a call. The row should say it was a ledger entry.");
    }

    // ------------------------------------------------------------- self-checks

    /**
     * The checks above only mean something if the extraction they rely on can fail.
     * A matcher that silently returned the whole file would make every "the guard is
     * inside the transaction" style assertion pass for the wrong reason.
     */
    @Test
    void methodBodyExtractionCanFail() throws IOException {
        String source = read(SERVICE);
        try {
            String body = methodBody(source, "noSuchMethodAnywhere");
            fail("Expected extraction of a missing method to fail, got: " + body);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("noSuchMethodAnywhere"));
        }
        // And it must actually narrow, or it is returning everything.
        // A method that is called before it is declared: a naive first-match search
        // returns the caller's body, which would make every "the guard is inside the
        // method" assertion above pass against the wrong method.
        String servlet = read(SERVLET);
        String caller = methodBody(servlet, "doPost");
        assertTrue(caller.contains("refundPayment"),
                "doPost calls refundPayment, so a first-match search on it finds this call first");
        String real = methodBody(servlet, "refundBlockedBecause");
        assertTrue(real.contains("TransactionType.PAYMENT"),
                "methodBody matched a call site instead of the declaration");
        assertFalse(real.contains("refundPayment("),
                "methodBody returned doPost rather than refundBlockedBecause");

        // findSettledRefund is the shape that broke this helper: it is called from
        // doPost and showDetail, both above its own declaration, and one of those
        // calls opens its own line. A first-match search finds the call.
        String refundFinder = methodBody(servlet, "findSettledRefund");
        assertTrue(refundFinder.contains("TransactionStatus.COMPLETED"),
                "methodBody matched a call to findSettledRefund rather than its declaration");
        assertFalse(refundFinder.contains("setAttribute"),
                "methodBody returned showDetail rather than findSettledRefund");

        String body = methodBody(source, "refundPayment");
        assertTrue(body.length() < source.length(),
                "methodBody returned the entire file, so it is not extracting a method");
        assertFalse(body.contains("public void updateTransactionStatus"),
                "methodBody leaked a neighbouring method into the extraction");
    }

    /** Reads a file, failing the test rather than returning empty text on a missing path. */
    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "Expected to find " + path);
        return Files.readString(path);
    }

    /**
 * The text from the start of the statement containing {@code at} to {@code at},
 * with comments and string literals blanked out.
 *
 * <p>Needed because the check above cannot just look at the preceding characters: a
 * javadoc paragraph a few lines above legitimately ends in a slash, so a naive
 * scan reports the guard as commented out and the test fails on correct code.
 */
    private static String lastStatementIn(String source, int at) {
        int lineStart = source.lastIndexOf('\n', at) + 1;
        StringBuilder cleaned = new StringBuilder(source.substring(lineStart, at));
        // Blank comment runs, so prose above cannot be read as code.
        int comment;
        while ((comment = indexOf(cleaned, "//")) >= 0) {
            int end = cleaned.indexOf("\n", comment);
            if (end < 0) {
                end = cleaned.length();
            }
            for (int i = comment; i < end; i++) {
                cleaned.setCharAt(i, ' ');
            }
            if (end == cleaned.length()) {
                break;
            }
        }
        return cleaned.toString();
    }

    private static int indexOf(StringBuilder text, String needle) {
        return text.toString().indexOf(needle);
    }

/** The declaration line of a method, which carries its parameter list. */
    private static String signatureOf(String source, String methodName) {
        int at = declarationOf(source, methodName);
        int end = source.indexOf('\n', at);
        return end < 0 ? source.substring(at) : source.substring(at, end);
    }

    /**
     * The offset of a method's <em>declaration</em>, not its first mention.
     *
     * <p>A plain {@code indexOf(name + "(")} finds the first call site, which for a
     * method invoked before it is defined returns the body of whatever method the
     * call sits in. That is a silent wrong answer rather than a failure, so the
     * declaration is required to sit at the start of a line preceded only by
     * modifiers or a return type, and to not be preceded by a {@code .} -- which is
     * what distinguishes {@code refundPayment(} from {@code x.refundPayment(}.
     */
    private static int declarationOf(String source, String methodName) {
        int from = 0;
        while (true) {
            int hit = source.indexOf(methodName + "(", from);
            if (hit < 0) {
                throw new IllegalArgumentException(
                        "No declaration of " + methodName + " in the source");
            }
            String prefix = source.substring(source.lastIndexOf('\n', hit) + 1, hit);
            boolean notAQualifiedCall = hit == 0 || source.charAt(hit - 1) != '.';
            if (isDeclarationPrefix(prefix) && notAQualifiedCall) {
                return hit;
            }
            from = hit + 1;
        }
    }

    /**
     * Whether everything on the line before a method name is a modifier or a type,
     * and so part of its declaration rather than a statement calling something.
     */
    private static boolean isDeclarationPrefix(String prefix) {
        String trimmed = prefix.strip();
        // Empty means the name opened the line, which is how a wrapped call site
        // reads: "findSettledRefund(\n ...)" on its own line. A declaration always has
        // a return type or a modifier in front of the name, so there is never nothing.
        if (trimmed.isEmpty() || trimmed.contains("=") || trimmed.contains("(")) {
            return false;
        }
        // Every character must belong to an identifier or a generic bound: "private
        // static ", "List<Transaction> ", "boolean ". A call site reads as
        // "app().transactionService()." or "String blocked = " and is rejected by the
        // check above on the '=' or by the '.' further in.
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean identifierish = Character.isLetterOrDigit(c) || c == '_' || c == '$'
                    || c == '<' || c == '>' || c == '.' || c == ' ' || c == ',';
            if (!identifierish) {
                return false;
            }
        }
        return true;
    }

    /**
     * The body of one method, from its signature to the line that closes it.
     *
     * <p>Brace counting, so it does not care how the method is formatted or how long it
     * is. It skips braces inside string literals and comments, which matters because the
     * refund method is mostly prose about refunding money and a naive counter stops at the
     * first brace-shaped character in a comment.
     */
    private static String methodBody(String source, String methodName) {
        int signature = declarationOf(source, methodName);
        int open = source.indexOf('{', signature);
        if (open < 0) {
            throw new IllegalArgumentException("Method " + methodName + " has no body");
        }
        int depth = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;

        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unbalanced braces in " + methodName);
    }
}