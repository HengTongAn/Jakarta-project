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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the rule that a hidden payment panel must not be able to block checkout.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * Every payment method shares one {@code <form>}, and each method's extra inputs
 * live in a {@code data-payment-panel} that is hidden with {@code d-none} when
 * another method is chosen. HTML5 constraint validation does <em>not</em> exempt a
 * control for being {@code display:none} -- only {@code disabled}, {@code type=hidden}
 * and a few others remove a control from constraint validation. So a statically
 * {@code required} input inside a hidden panel is still validated, the browser
 * refuses to submit the form, and it cannot report the problem because the
 * offending control cannot be focused.
 *
 * <p>The visible effect was that enabling card payment broke the other two
 * methods: choosing ABA Payway or Cash on delivery and pressing Place order did
 * nothing at all, with no error. Verified in Chrome: {@code checkValidity()} was
 * false and the {@code submit} event never fired.
 *
 * <p>It reached a deployed build because it is invisible to every test that posts
 * the form directly. A server-side test suite sees a perfectly good form, because
 * the browser never sends it. Only a browser enforces this.
 *
 * <h2>Why a source lint</h2>
 *
 * Asserting "the form submits" needs a real browser and an authenticated session,
 * which {@code mvn test} does not have and should not need. Encoding the rule as
 * markup keeps the check free and permanent, and it generalises: the next payment
 * provider added to this page is covered without anyone remembering the rule.
 */
class PaymentPanelRequiredTest {

    private static final Path CHECKOUT = Path.of(
            "src/main/webapp/WEB-INF/views/customer/checkout.jsp");
    private static final Path APP_JS = Path.of("src/main/webapp/assets/js/app.js");

    /** Any tag. Attributes may contain '>', so quoted values are consumed first. */
    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][\\w-]*)((?:\"[^\"]*\"|'[^']*'|[^>])*?)(/?)>");

    /** Elements that open no scope, so they must not affect nesting depth. */
    private static final List<String> VOID = List.of(
            "input", "br", "hr", "img", "meta", "link", "area", "base", "col", "embed",
            "source", "track", "wbr", "option");

    /**
     * A standalone {@code required} attribute. The lookarounds matter: without
     * them {@code data-active-required} and {@code aria-required} would both count
     * as the real thing, and the check would be pure noise.
     */
    private static final Pattern REQUIRED = Pattern.compile("(?<![\\w-])required(?![\\w-])");

    private static final Pattern PANEL = Pattern.compile("data-payment-panel\\s*=\\s*\"([^\"]*)\"");

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    void noPaymentPanelContainsAStaticallyRequiredControl() throws IOException {
        String source = read(CHECKOUT);
        List<String> offenders = staticallyRequiredInsidePanels(source);

        if (!offenders.isEmpty()) {
            fail("These controls are required but sit in a payment panel that gets hidden when "
                    + "another method is selected. The browser will refuse to submit the form and "
                    + "cannot show an error on an invisible control, so choosing another payment "
                    + "method silently does nothing. Use data-active-required=\"true\" instead; "
                    + "initCheckoutPayment applies required only while the panel is active.\n  "
                    + String.join("\n  ", offenders));
        }
    }

    @Test
    void theCardFieldsOptInToBeingRequiredWhileActive() throws IOException {
        // The positive counterpart. Without it, the check above would also pass if
        // the card form were deleted outright -- which would leave card payment
        // accepting a blank number and relying entirely on the server to notice.
        String source = read(CHECKOUT);
        for (String field : List.of("cardName", "cardNumber", "cardExpiry", "cardCvv")) {
            assertTrue(source.contains("id=\"" + field + "\""), "missing field: " + field);
            Matcher tag = tagOf(source, "id=\"" + field + "\"");
            assertTrue(tag.find(), "could not locate the <input> for " + field);
            assertTrue(tag.group(3).contains("data-active-required"),
                    field + " must carry data-active-required so the browser validates it once the "
                            + "card panel is showing. Tag was: <input " + tag.group(3).trim() + ">");
        }
    }

    @Test
    void theScriptActuallyHonoursTheOptIn() throws IOException {
        // Both halves are needed. Markup that opts in while the script ignores
        // data-active-required would leave the card fields permanently optional.
        String js = read(APP_JS);
        assertTrue(js.contains("data-active-required"),
                "app.js no longer reads data-active-required, so the card fields would never "
                        + "become required and the opt-in in checkout.jsp would be dead markup.");
        assertTrue(js.contains("field.required = active"),
                "app.js should set required from panel visibility; the card fields would stay "
                        + "permanently optional without it.");
    }

    // ------------------------------------------------------------- the detector

    @Test
    void theDetectorFlagsTheKnownBadMarkup() {
        List<String> problems = staticallyRequiredInsidePanels("""
                <div data-payment-panel="visa" class="d-none">
                  <input id="cardNumber" required>
                </div>
                """);
        assertTrue(problems.size() == 1, "should flag one control, got: " + problems);
        assertTrue(problems.get(0).contains("cardNumber"), problems.get(0));
    }

    @Test
    void theDetectorAcceptsTheFixedMarkup() {
        assertTrue(staticallyRequiredInsidePanels("""
                <div data-payment-panel="visa" class="d-none">
                  <input id="cardNumber" data-active-required="true">
                  <input id="cardName" data-active-required="true" aria-required="false">
                </div>
                """).isEmpty(), "data-active-required and aria-required are not the real attribute");
    }

    @Test
    void theDetectorIgnoresRequiredOutsideAnyPanel() {
        // Checkout legitimately has required fields that are always visible, and
        // the ABA and cash panels hold no inputs at all. Neither is a problem.
        assertTrue(staticallyRequiredInsidePanels("""
                <div class="col-12">
                  <input id="fullName" required>
                </div>
                <div data-payment-panel="aba">
                  <p>Pay in the ABA app.</p>
                </div>
                <div data-payment-panel="cash">
                  <p>Pay on delivery.</p>
                </div>
                """).isEmpty(), "required outside a payment panel, or a panel with no inputs, is fine");
    }

    @Test
    void theDetectorHandlesASecondPanel() {
        List<String> problems = staticallyRequiredInsidePanels("""
                <div data-payment-panel="visa" class="d-none">
                  <input id="cardNumber" data-active-required="true">
                </div>
                <div data-payment-panel="wallet" class="d-none">
                  <input id="walletToken" required>
                </div>
                """);
        assertTrue(problems.size() == 1, "should flag only the wallet control, got: " + problems);
        assertTrue(problems.get(0).contains("walletToken"), problems.get(0));
    }

    @Test
    void theDetectorDoesNotConfuseSelfClosingTagsForNesting() {
        // A void element written as <input ... /> must not unbalance the panel.
        assertTrue(staticallyRequiredInsidePanels("""
                <div data-payment-panel="visa" class="d-none">
                  <br/>
                  <input id="cardNumber" data-active-required="true"/>
                </div>
                <input id="someOtherField" required>
                """).isEmpty(), "void elements should not change the nesting depth");
    }

    /** Returns a matcher over just the first tag whose attributes contain {@code needle}. */
    private static Matcher tagOf(String source, String needle) {
        Matcher m = TAG.matcher(source);
        while (m.find()) {
            if (m.group(3).contains(needle)) {
                return TAG.matcher(m.group());
            }
        }
        return null;
    }

    /**
     * Returns a description of every statically-required control that sits inside a
     * {@code data-payment-panel}, by walking tags and tracking which panel (if any)
     * encloses the current position.
     */
    private static List<String> staticallyRequiredInsidePanels(String source) {
        List<String> problems = new ArrayList<>();
        Matcher m = TAG.matcher(source);
        int depth = 0;
        // null when outside every panel; otherwise the panel's name and the depth
        // at which it opened, so the right close tag is what ends the panel.
        String panel = null;
        int panelDepth = -1;

        while (m.find()) {
            boolean closing = !m.group(1).isEmpty();
            String name = m.group(2).toLowerCase(java.util.Locale.ROOT);
            String attrs = m.group(3);
            boolean selfClosing = !m.group(4).isEmpty();

            if (closing) {
                depth = Math.max(0, depth - 1);
                if (panel != null && depth < panelDepth) {
                    panel = null;
                    panelDepth = -1;
                }
                continue;
            }

            if (panel != null && REQUIRED.matcher(attrs).find()) {
                String id = attribute(attrs, "id");
                problems.add("payment panel \"" + panel + "\" contains required on "
                        + (id == null ? "an unnamed control" : id));
            }

            if (selfClosing || VOID.contains(name)) {
                continue;
            }
            depth++;
            if (panel == null) {
                Matcher open = PANEL.matcher(attrs);
                if (open.find()) {
                    panel = open.group(1);
                    panelDepth = depth;
                }
            }
        }
        return problems;
    }

    /** Pulls a quoted attribute value out of a tag's attribute text. */
    private static String attribute(String attrs, String key) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }
}
