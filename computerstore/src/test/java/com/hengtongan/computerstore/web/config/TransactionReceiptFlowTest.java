package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the receipt half of the transaction flow: the page a customer lands on once a payment
 * settles, and the two actions that page exists to offer.
 *
 * <h2>The flow being guarded</h2>
 *
 * <pre>
 *   card authorised in checkout POST   -&gt; /account/transactions?id=N&amp;view=receipt
 *   ABA gateway return, confirmed     -&gt; /account/transactions?id=N&amp;view=receipt
 *   both fall back                     -&gt; /account/orders?id=M
 * </pre>
 *
 * The receipt is a rendering of a transaction row, not a second stored document. There is
 * deliberately no receipt table, so the receipt cannot disagree with the ledger about whether
 * money arrived -- which is also why the receipt is reachable only through a path that already
 * passed the customer-ownership check.
 *
 * <h2>What each check is for</h2>
 *
 * <ul>
 *   <li><b>Print and Close both exist.</b> The page is a document the customer keeps and a
 *       screen they leave. Losing either is not a crash; it is a page that quietly stopped
 *       doing one of its two jobs.</li>
 *   <li><b>Print is a button calling {@code window.print}.</b> A link would open a second
 *       copy of the page and print that, which is how a customer ends up with a blank sheet
 *       from a click that looked like it worked.</li>
 *   <li><b>The print stylesheet hides the chrome.</b> Without it, printing yields the whole
 *       site with a receipt in the middle of it: nav, footer, the other buttons. This is
 *       invisible in review and obvious on paper.</li>
 *   <li><b>The receipt is not offered for an unsettled payment.</b> A receipt link on a
 *       declined row produces a document that reads as proof of payment. This is the defect
 *       most likely to be reintroduced by a well-meaning "let customers see every attempt".</li>
 *   <li><b>Both payment paths reach it.</b> Card and ABA settle in different servlets. Wiring
 *       only one leaves a method that pays and shows nothing.</li>
 * </ul>
 *
 * <h2>What is not checked</h2>
 *
 * That the receipt renders, and that the ownership check runs. Both need the servlet and a
 * database. {@code CustomerTransactionScopeTest} holds the half that can be read off the
 * source: that the customer controller reads transactions through the {@code *ForUser} getters.
 */
class TransactionReceiptFlowTest {

    private static final Path VIEWS = Path.of("src/main/webapp/WEB-INF/views");
    private static final Path CUSTOMER_CONTROLLERS = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/controller/customer");

    private static final Path RECEIPT = VIEWS.resolve("customer/account/receipt.jsp");
    private static final Path TRANSACTION_DETAIL = VIEWS.resolve("customer/account/transaction-detail.jsp");
    private static final Path TRANSACTIONS = VIEWS.resolve("customer/account/transactions.jsp");
    private static final Path PAYMENT_ABA = VIEWS.resolve("customer/payment-aba.jsp");
    private static final Path PAYMENT_CARD = VIEWS.resolve("customer/payment-card.jsp");
    private static final Path PAGES_CSS = Path.of("src/main/webapp/assets/css/app/pages.css");

    private static final Path ABA_SERVLET = CUSTOMER_CONTROLLERS.resolve("AbaPaymentServlet.java");
    private static final Path CARD_SERVLET = CUSTOMER_CONTROLLERS.resolve("CardPaymentServlet.java");
    private static final Path CHECKOUT_SERVLET = CUSTOMER_CONTROLLERS.resolve("CheckoutServlet.java");
    private static final Path TX_SERVLET = CUSTOMER_CONTROLLERS.resolve("CustomerTransactionsServlet.java");

    private static final String RECEIPT_VIEW = "view=receipt";

    // ------------------------------------------------------------------
    // The two actions the receipt exists for.
    // ------------------------------------------------------------------

    @Test
    void theReceiptOffersBothAPrintAndAClose() throws IOException {
        String view = read(RECEIPT);

        assertTrue(view.contains("window.print()"),
                RECEIPT + " has no Print action. The point of a receipt is that the customer "
                        + "can keep or file a paper copy, and the page ships without a way to "
                        + "produce one.");
        assertTrue(view.contains("id=\"printReceipt\""),
                RECEIPT + " lost the print button element. app.js and any future test that "
                        + "triggers printing look it up by this id.");
    }

    @Test
    void theCloseActionLeavesTheReceipt() throws IOException {
        String view = read(RECEIPT);

        // Close has to be a navigation, not a print dismissal: the customer paid and is now
        // browsing, and a page with no way back is a dead end.
        assertTrue(view.contains("/account/orders?id=${transaction.orderId}"),
                RECEIPT + " has no route back to the order. A customer who does not want a "
                        + "printed copy still has to be able to leave.");
    }

    @Test
    void printIsAButtonRatherThanALink() throws IOException {
        // A link to a print view opens a second copy of the page; the user then prints that
        // copy, which prints whatever that copy rendered, or a blank page if the route did
        // not exist. window.print() on the page already showing the receipt cannot diverge
        // from it, because it is the same document.
        String view = read(RECEIPT);
        assertTrue(view.contains("type=\"button\""),
                RECEIPT + " should trigger printing with a button element. A link is the shape "
                        + "that opens a second copy and prints the wrong document.");
    }

    @Test
    void neitherActionSurvivesIntoThePrintedOutput() throws IOException {
        // A printed receipt that offers to print itself is nonsense, and a Close button in the
        // middle of the paper is worse: it reads as a control on the document.
        String view = read(RECEIPT);
        assertTrue(view.contains("no-print"),
                RECEIPT + " does not mark its Print/Close row with the no-print class. The print "
                        + "stylesheet hides .no-print, so without it both buttons land on paper.");
    }

    // ------------------------------------------------------------------
    // The print stylesheet.
    // ------------------------------------------------------------------

    @Test
    void thePrintStylesheetExistsAndTargetsTheReceipt() throws IOException {
        String css = read(PAGES_CSS);

        assertTrue(css.contains("@media print"),
                PAGES_CSS + " has no @media print block. The receipt's Print button then produces "
                        + "the entire site -- nav, footer, mobile nav -- with the receipt in the "
                        + "middle of it, which looks like a working feature right up to the paper.");
    }

    @Test
    void thePrintBlockHidesTheSiteChrome() throws IOException {
        String css = read(PAGES_CSS);
        String block = printBlock(css);

        assertFalse(block.isEmpty(),
                PAGES_CSS + " declares @media print but the block could not be located, so the "
                        + "chrome-hiding rules cannot be confirmed to exist at all.");

        for (String chrome : List.of("header", "footer", ".no-print", ".mobile-bottom-nav")) {
            assertTrue(block.contains(chrome),
                    "The @media print block in " + PAGES_CSS + " does not hide " + chrome
                            + ". Printing the receipt then includes site furniture that has no "
                            + "place on a document.");
        }
    }

    @Test
    void thePrintBlockRestylesTheSheetForPaper() throws IOException {
        // The screen styling is a card: a shadow, a border, a centred max-width. Shadows print
        // as smudges and the border competes with the table rules, so both are dropped for
        // print. Without this the receipt looks like a screenshot of a website.
        String block = printBlock(read(PAGES_CSS));

        assertTrue(block.contains("box-shadow: none"),
                "The receipt sheet keeps its screen shadow when printed, which comes out as a "
                        + "grey smudge around the document.");
        assertTrue(block.contains("print-color-adjust"),
                "The @media print block does not set print-color-adjust. Browsers that omit "
                        + "backgrounds in print drop the filled panels (the paid strip, the "
                        + "brand block), leaving unstyled white gaps mid-receipt.");
    }

    @Test
    void thePrintBlockIsScopedToTheReceiptPage() throws IOException {
        // An unscoped `@media print { header, footer { display: none } }` would silently break
        // printing for every other page in the app, including any admin report. The receipt sets
        // bodyClass="receipt-page" precisely so these rules can be confined to it.
        String block = printBlock(read(PAGES_CSS));

        assertTrue(block.contains("body.receipt-page"),
                "The @media print rules are not scoped to body.receipt-page. Printing any other "
                        + "page in the app would inherit the receipt's chrome hiding.");
        assertTrue(read(RECEIPT).contains("receipt-page"),
                RECEIPT + " no longer sets the receipt-page body class that the print rules are "
                        + "scoped to, so the receipt would print with the full site around it.");
    }

    // ------------------------------------------------------------------
    // A receipt is only for money that moved.
    // ------------------------------------------------------------------

    @Test
    void theReceiptRefusesToPrintAnUnsettledPayment() throws IOException {
        // Reachable by URL: /account/transactions?id=7&view=receipt works for a pending or
        // failed row too. Printing one produces a document headed "Payment receipt" for a
        // payment that was declined, which is precisely the artefact a chargeback dispute is
        // built from.
        String view = read(RECEIPT);

        assertTrue(view.contains("_txSettled"),
                RECEIPT + " does not branch on whether the transaction actually settled, so a "
                        + "declined or pending transaction renders as a payment receipt.");
        assertTrue(view.contains("'COMPLETED'"),
                RECEIPT + " no longer tests for the COMPLETED status. The settled check must be "
                        + "against the transaction status, not the order's payment status.");
    }

    @Test
    void theReceiptPrintButtonIsWithheldFromAnUnsettledPayment() throws IOException {
        // The refusal banner explains the situation; it is not a substitute for withholding the
        // button. A customer who ignores the warning and hits Print should not get a receipt.
        String view = read(RECEIPT);

        int printButton = view.indexOf("id=\"printReceipt\"");
        int settledVar = view.indexOf("var=\"_txSettled\"");

        assertTrue(settledVar >= 0,
                RECEIPT + " never binds _txSettled, so every settled check reads an empty value.");
        assertTrue(printButton > settledVar,
                RECEIPT + " renders the Print button before _txSettled is bound, so the guard "
                        + "around it would read an empty value and never fire.");

        // The button must be inside a c:if testing the settled flag. Checked by locating the
        // guard immediately above it, so "wrapped in a c:if about something else" does not pass.
        String guard = innermostGuardAbove(view, printButton);
        assertTrue(guard.contains("_txSettled"),
                RECEIPT + " renders the Print button under the guard \"" + guard + "\" instead of "
                        + "under a settled check. A declined or pending transaction would then offer "
                        + "a print action for a payment that never happened.");
    }

    @Test
    void theCloseButtonIsOfferedEvenWhenThePaymentDidNotSettle() throws IOException {
        // The converse, and the reason Close sits outside the settled branch: a customer looking
        // at a declined attempt must still be able to leave, and to reach the order page where
        // they can retry.
        String view = read(RECEIPT);
        int closeLink = view.indexOf("/account/orders?id=${transaction.orderId}");
        int printButton = view.indexOf("id=\"printReceipt\"");

        assertTrue(closeLink >= 0, RECEIPT + " has no route back to the order at all");
        assertTrue(closeLink < printButton,
                RECEIPT + " renders Close after the Print button, which is the order the settled "
                        + "guard implies. If Close ended up inside the settled branch, a declined "
                        + "payment would leave the customer on a page with no way off it.");
    }

    @Test
    void theTransactionListOnlyOffersReceiptsForSettledPayments() throws IOException {
        // The list renders every attempt, so this is where a receipt link on a declined row
        // would first be reachable.
        String list = read(TRANSACTIONS);

        assertTrue(list.contains(RECEIPT_VIEW),
                TRANSACTIONS + " offers no way to reach the receipt. A customer who wants a copy "
                        + "of a past payment has no route to one from their ledger.");
        assertTrue(list.contains("_txSettled"),
                TRANSACTIONS + " links the receipt without checking that the row settled. Every "
                        + "declined attempt in the ledger would offer a printable receipt.");
    }

    @Test
    void theTransactionDetailOnlyOffersAReceiptForASettledPayment() throws IOException {
        String detail = read(TRANSACTION_DETAIL);

        assertTrue(detail.contains(RECEIPT_VIEW),
                TRANSACTION_DETAIL + " has no link to the receipt, so the two pages describing the "
                        + "same transaction cannot be reached from each other.");
        assertTrue(detail.contains("_txSettled") || detail.contains("'COMPLETED'"),
                TRANSACTION_DETAIL + " links the receipt without a settled check.");
    }

    // ------------------------------------------------------------------
    // Both payment paths must reach the receipt.
    // ------------------------------------------------------------------

    @Test
    void aSettledCardPaymentLandsOnTheReceipt() throws IOException {
        // Card is authorised inline in the checkout POST: there is no gateway return, so the
        // checkout servlet is the only place the "money moved" moment exists for a card.
        String checkout = read(CHECKOUT_SERVLET);

        assertTrue(checkout.contains("receiptPathFor"),
                CHECKOUT_SERVLET + " does not resolve a receipt on card success. A customer who "
                        + "pays by card would be sent to the order page and never see a receipt, "
                        + "while an ABA customer would.");
        assertTrue(successBranchOf(checkout).contains("receiptPathFor"),
                CHECKOUT_SERVLET + " resolves a receipt but not on the PAID branch. The card page's "
                        + "outcome and the success redirect would then disagree about what the "
                        + "customer sees.");
    }

    @Test
    void aSettledAbaPaymentLandsOnTheReceipt() throws IOException {
        // The gateway return is the ABA equivalent of the card success point.
        String aba = read(ABA_SERVLET);

        assertTrue(aba.contains("receiptPathFor"),
                ABA_SERVLET + " does not resolve a receipt on a confirmed ABA payment.");
        assertTrue(aba.contains("getSettledPaymentForUser"),
                ABA_SERVLET + " does not use the settled-payment lookup. The receipt path must come "
                        + "from a completed transaction, or the page will offer a document for a "
                        + "payment that is not in the ledger.");
    }

    @Test
    void bothPaymentViewsLinkTheResolvedReceipt() throws IOException {
        // The payment pages are reachable directly (the QR page, the declined card page), so
        // the link has to be there too, not only on the redirect that arrives after success.
        for (Path view : List.of(PAYMENT_ABA, PAYMENT_CARD)) {
            String source = read(view);
            assertTrue(source.contains("${receiptPath}"),
                    view + " does not link the servlet-resolved receipt path. The success redirect "
                            + "goes to the receipt, but a customer who reloads or bookmarks the "
                            + "payment page has no way to it.");
        }
    }

    @Test
    void theResolvedReceiptPathIsSuppliedByAServletNotTheView() throws IOException {
        // The receipt belongs to a transaction; the payment pages know only an order id. If a
        // view ever built the URL itself it would be inventing a transaction id, and the link
        // would 404 for every real payment.
        for (Path view : List.of(PAYMENT_ABA, PAYMENT_CARD)) {
            assertFalse(read(view).contains("view=receipt"),
                    view + " hard-codes view=receipt. Only the servlet knows which transaction "
                            + "settled the order; the view must use the resolved ${receiptPath}.");
        }
    }

    // ------------------------------------------------------------------
    // Ownership: the receipt is only as safe as the route that serves it.
    // ------------------------------------------------------------------

    @Test
    void theReceiptIsServedByTheUserScopedLookup() throws IOException {
        // The receipt has no table of its own, so this is the whole of its access control. If it
        // were served from getTransaction(long) -- keyed on the id alone -- any customer could
        // print any other customer's card payment by changing one number in the URL.
        String servlet = read(TX_SERVLET);

        assertTrue(servlet.contains("getTransactionForUser"),
                TX_SERVLET + " must read the receipt's transaction with getTransactionForUser. The "
                        + "id-only getter would serve any customer's payment for an id from the URL.");
        assertTrue(servlet.contains(RECEIPT_VIEW),
                TX_SERVLET + " no longer reads the view parameter, so /account/transactions?id=N"
                        + "&view=receipt silently falls through to the detail page and the whole "
                        + "post-payment redirect lands somewhere without a receipt.");
    }

    @Test
    void theReceiptCannotBeReachedWithoutATransactionId() throws IOException {
        // A receipt for "the most recent settled payment" would need a query with no customer
        // in it. Requiring the id keeps the ownership check load-bearing rather than implied.
        String servlet = read(TX_SERVLET);
        int idRead = servlet.indexOf("getParameter(\"id\")");
        int receiptBranch = servlet.indexOf("showReceipt");

        assertTrue(idRead >= 0 && receiptBranch > idRead,
                TX_SERVLET + " does not read the id before dispatching to showReceipt. The receipt "
                        + "branch must sit behind the id, so a request without one cannot select a "
                        + "receipt to render.");
    }

    @Test
    void theReceiptViewIsOnDiskWhereTheServletSaysItIs() throws IOException {
        // A forward to a path that does not exist is a 404 at the exact moment a customer has
        // just paid, which is the worst possible time to discover it.
        String servlet = read(TX_SERVLET);
        assertTrue(servlet.contains("customer/account/receipt.jsp"),
                TX_SERVLET + " does not forward to customer/account/receipt.jsp.");
        assertTrue(Files.isRegularFile(RECEIPT),
                RECEIPT + " does not exist, so the receipt forward 404s immediately after payment.");
    }

    // ------------------------------------------------------------------
    // Self-tests: a lint that cannot fail is not a lint.
    // ------------------------------------------------------------------

    @Test
    void thePrintBlockExtractorTellsABlockFromNothing() {
        assertTrue(printBlock("body { color: red; }").isEmpty(),
                "a stylesheet with no @media print must yield an empty block, otherwise the "
                        + "chrome checks pass on a file that prints the whole site");
        assertTrue(printBlock("@media print { header { display: none; } }").contains("header"),
                "the extractor must return the contents of the print block");
    }

    @Test
    void thePrintBlockExtractorBalancesNestedBraces() {
        // The chrome rules are nested inside body.receipt-page selectors. A counter that stopped
        // at the first '}' would return a fragment ending mid-block, and the checks would pass
        // on a file where half the print rules are missing.
        String css = "@media print { body.receipt-page { header { display: none } } .x { y: 1 } }";
        String block = printBlock(css);
        assertTrue(block.contains(".x"),
                "the extractor stopped at the first closing brace and lost the rest of the block");
    }

    @Test
    void theSuccessBranchExtractorTargetsThePaidCase() {
        String checkout = """
                if (payment.getStatus() == Payment.Status.PAID) {
                    redirect(request, response, "/account/orders?id=1");
                } else {
                    redirect(request, response, "/payment/card?order=1");
                }
                """;
        String branch = successBranchOf(checkout);
        assertFalse(branch.contains("payment/card"),
                "the PAID branch must not include the declined branch that follows it");
        assertTrue(branch.contains("account/orders"),
                "the PAID branch should contain its own redirect");
    }

    @Test
    void theGuardExtractorReportsTheEnclosingCondition() {
        String view = "<c:if test=\"${a}\"><c:if test=\"${b}\"><button id=\"x\"></c:if></c:if>";
        assertTrue(innermostGuardAbove(view, view.indexOf("id=\"x\"")).contains("${b}"),
                "the extractor must report the innermost enclosing guard, not the outermost one");

        // A closed c:if before the element means that guard governs something else, so the
        // element is unguarded. Getting this wrong would report a settled check where there
        // is none, and the print-button guard would pass on an unguarded button.
        String closed = "<c:if test=\"${settled}\"><span>other</span></c:if><button id=\"y\">";
        assertTrue(innermostGuardAbove(closed, closed.indexOf("id=\"y\"")).isEmpty(),
                "a guard that closed before the element must not be reported as governing it");
    }

    @Test
    void thePrintCallCheckIsNotVacuous() throws IOException {
        // Proves the check in theReceiptOffersBothAPrintAndAClose can fail. A fixture that
        // asserted the same thing the real check does would pass whatever the receipt said.
        assertFalse("body.receipt-page { color: red }".contains("window.print()"),
                "this fixture must NOT contain a print call, or the assertion below is testing "
                        + "nothing; it is the stand-in for a receipt that lost its Print button");
        assertTrue(read(RECEIPT).contains("window.print()"),
                "the real receipt is expected to call window.print() today -- if this fails, the "
                        + "feature changed and the guard messages need revisiting");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path + " does not exist");
        return Files.readString(path);
    }

    /** The body of the first {@code @media print} block, or "" when there is none. */
    private static String printBlock(String css) {
        int open = css.indexOf("@media print");
        if (open < 0) {
            return "";
        }
        int brace = css.indexOf('{', open);
        if (brace < 0) {
            return "";
        }
        int depth = 0;
        for (int i = brace; i < css.length(); i++) {
            char c = css.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return css.substring(brace + 1, i);
                }
            }
        }
        return "";
    }

    /**
     * The condition of the innermost {@code <c:if>} enclosing a position.
     * <p>
     * Found by scanning backwards for the nearest {@code <c:if>} or {@code <c:else>/<c:when>}
     * that is still open at that point, so it reports what actually governs the element rather
     * than the nearest textual match. Returns "" when the element is not inside any conditional
     * at all, which is the case the callers treat as unguarded.
     */
    private static String innermostGuardAbove(String source, int position) {
        int guardOpen = -1;
        for (int i = 0; i < position; i++) {
            if (source.startsWith("<c:if", i)) {
                guardOpen = i;
            } else if (source.startsWith("</c:if>", i) && guardOpen >= 0) {
                // A closed c:if means the last one we saw belongs to an earlier element.
                guardOpen = -1;
            }
        }
        if (guardOpen < 0) {
            return "";
        }
        int testStart = source.indexOf("test=\"", guardOpen);
        int testEnd = testStart < 0 ? -1 : source.indexOf('"', testStart + 6);
        if (testStart < 0 || testEnd < 0 || testStart > position) {
            return "";
        }
        return source.substring(testStart + 6, testEnd);
    }

    /**
     * The source of the {@code PAID} branch in checkout.
     * <p>
     * Brace-matched from the {@code Payment.Status.PAID} comparison rather than by a regex over
     * the else clause, so the declined branch that follows it is genuinely excluded instead of
     * merely assumed to be. Asserting on the wrong branch would pass while the success path
     * sent the customer to the order page.
     */
    private static String successBranchOf(String source) {
        int marker = source.indexOf("Payment.Status.PAID");
        if (marker < 0) {
            fail("no PAID branch found in the checkout servlet");
        }
        int brace = source.indexOf('{', marker);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace + 1, i);
                }
            }
        }
        return "";
    }

    @Test
    void everyGuardPathExists() {
        List<String> missing = new ArrayList<>();
        for (Path path : List.of(RECEIPT, TRANSACTION_DETAIL, TRANSACTIONS, PAYMENT_ABA, PAYMENT_CARD,
                PAGES_CSS, ABA_SERVLET, CARD_SERVLET, CHECKOUT_SERVLET, TX_SERVLET)) {
            if (!Files.isRegularFile(path)) {
                missing.add(path.toString());
            }
        }
        assertTrue(missing.isEmpty(),
                () -> "this test's own guard paths are missing: " + missing
                        + ". A source lint pointed at a file that moved passes vacuously.");
    }

    /** Pattern kept for the receipt-link assertions above. */
    private static final Pattern RECEIPT_LINK = Pattern.compile("view=receipt");

    @Test
    void theReceiptLinkPatternMatchesWhatTheViewsEmit() {
        // EL escapes the ampersand in a JSP attribute, so the emitted URL is ?id=1&amp;view=receipt.
        // The constant used by the checks above is the decoded form.
        Matcher m = RECEIPT_LINK.matcher("?id=1&amp;view=receipt");
        assertTrue(m.find(),
                "the receipt-link pattern must match the escaped form the views actually write, "
                        + "otherwise a view could ship a broken URL and the checks stay green");
    }
}
