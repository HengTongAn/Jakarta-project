package com.hengtongan.computerstore.web.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the gateway connection probe on the admin payments page.
 *
 * <h2>Why the probe exists</h2>
 *
 * {@code AbaPaywayClient} has never been run against a real ABA sandbox. Its endpoint
 * paths and the shape of its credentials were written from public documentation, and
 * its own javadoc says so. That leaves an operator about to switch simulation off with
 * no way to find out whether the paths are right before a customer does. The probe is
 * the answer: call the gateway, report what came back, create nothing.
 *
 * <h2>What is dangerous about a probe, and what is guarded here</h2>
 *
 * A probe is a button that makes the server open an authenticated outbound socket to a
 * third party. That makes three things load-bearing, and all three are invisible in a
 * diff:
 *
 * <ul>
 *   <li><b>It must not create a payment.</b> Probing the precreate endpoint would mint a
 *       transaction and a QR image an operator might scan from a production merchant
 *       account. The probe uses the status endpoint with an id that cannot exist.</li>
 *   <li><b>It must report what happened, not what was hoped.</b> A probe that collapsed
 *       every outcome into one boolean would say "connected" for a 401 from a gateway
 *       that refused the credentials, which is the single most useful thing to
 *       distinguish.</li>
 *   <li><b>It must probe the same call a payment would make.</b> A duplicated request
 *       builder could carry different headers than {@code post}, and then a passing
 *       probe would be evidence about a call the store never makes.</li>
 * </ul>
 *
 * <h2>What is not checked</h2>
 *
 * That a real gateway answers. That needs credentials and a network, neither of which
 * exists here. Everything below reads source, and a green suite says the rules are
 * stated -- not that ABA Payway accepts the requests.
 */
class PaymentConnectionProbeTest {

    private static final Path SRC = Path.of("src/main/java/com/hengtongan/computerstore");
    private static final Path VIEWS = Path.of("src/main/webapp/WEB-INF/views");

    private static final Path CLIENT = SRC.resolve("infrastructure/payment/AbaPaywayClient.java");
    private static final Path SERVLET = SRC.resolve("web/controller/admin/AdminPaymentsServlet.java");
    private static final Path PAYMENTS_JSP = VIEWS.resolve("admin/payments.jsp");
    private static final Path FILTER = SRC.resolve("web/filter/web/ComingSoonGateFilter.java");
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");

    // ------------------------------------------------------- the probe is safe

    @Test
    void theProbeNeverCreatesAPayment() throws IOException {
        String probe = methodBody(read(CLIENT), "diagnose");
        assertFalse(probe.contains("precreate("),
                "The probe must not call precreate. That creates a real transaction and a QR "
                        + "image against the merchant account, and an operator pressing a "
                        + "connection test would be minting live payments.");
        assertFalse(probe.contains("simulatedPrecreate"),
                "A probe that fell back to the simulated QR would report success without "
                        + "touching the network, which is the opposite of its purpose");
        assertTrue(probe.contains("STATUS_PATH"),
                "The probe targets the status endpoint, which reads rather than creates");
        assertTrue(probe.contains("CONNECTION-TEST"),
                "The probe uses a transaction id that cannot exist, so a correct gateway "
                        + "rejects it and the rejection itself proves the path and headers work");
    }

    @Test
    void theProbeNeverOpensTheNetworkWhenItCannotSucceed() throws IOException {
        String probe = methodBody(read(CLIENT), "diagnose");
        int liveCheck = probe.indexOf("isLiveEnabled()");
        int call = probe.indexOf("exchange(STATUS_PATH");
        assertTrue(liveCheck >= 0,
                "The probe must refuse to call out while simulation is on. That mode is the "
                        + "default, so an unguarded probe would appear to work and prove nothing.");
        assertTrue(liveCheck < call,
                "The precondition must be checked before the request is built");
        assertTrue(probe.contains("isBlank()"),
                "Missing credentials must be reported as such rather than sent as empty "
                        + "headers, which reads to a gateway as bad credentials and wastes the "
                        + "one call an operator has.");

        // Ordering alone is not enough, which is the trap this assertion set fell into
        // first: a guard that is mentioned but not acted on -- commented out, or
        // short-circuited by a constant -- still passes every positional check above
        // while calling the real gateway on a store that has not opted in. So the
        // condition is recovered with comments and strings blanked out, rather than
        // guessed at from prose that sits a few lines above it.
        String guard = conditionAround(probe, "isLiveEnabled()");
        assertFalse(guard.contains("//"),
                "The live-mode guard appears to be commented out, so the probe would call "
                        + "ABA even while simulation is on");
        // A constant can disable the guard on either side of the call or force it open
        // outright, so the whole condition is inspected rather than the text in front of
        // the call. Checking only the leading position is how "&& false" ships unnoticed.
        assertFalse(guard.matches("(?s).*\\bfalse\\b.*"),
                "The live-mode guard is short-circuited by a constant, so the probe reaches "
                        + "the network regardless of the stored setting");
        assertFalse(guard.matches("(?s).*\\|\\|\\s*true.*"),
                "The live-mode guard is forced open by || true");
        assertEquals("if (!PaymentConfig.isLiveEnabled()) {",
                guard.strip(),
                "The live-mode guard must be a plain 'if (!PaymentConfig.isLiveEnabled())' with "
                        + "nothing added to the condition. Anything else is a guard that may "
                        + "have been rewritten to pass.");
    }

    @Test
    void theProbeNeverWritesTheSecretToTheLog() throws IOException {
        String probe = methodBody(read(CLIENT), "diagnose");
        // Exactly one reference, and it must be the precondition reading the value.
        // A second reference inside a log call would put a live credential in the
        // application log, which the class already refuses to do for gateway bodies.
        int secretUses = countOccurrences(probe, "secret()");
        assertEquals(1, secretUses,
                "diagnose() must reference the API secret exactly once, to check it is set. "
                        + "Any other use risks writing a live credential into the log.");
        assertTrue(probe.contains("secret().isBlank()"),
                "The single reference to the secret must be the missing-credential check");
        assertFalse(probe.contains("LOGGER"),
                "The probe returns a Diagnosis that says what went wrong, so it does not need "
                        + "to log. A logger here is only ever there to log something private.");
    }

    // ------------------------------------------- the probe reports what happened

    @Test
    void reachabilityAndAcceptanceAreSeparateAnswers() throws IOException {
        // The three are fields of Diagnosis, not of diagnose(): a probe that collapsed
        // them into one boolean would say "connected" for a 401 from a gateway that
        // refused the credentials, which is the most useful thing to tell apart.
        String client = read(CLIENT);
        // From the declaration, not from the brace: a record's components sit in its
        // header, ahead of the body, and the components are the whole point here.
        String diagnosis = client.substring(declarationOf(client, "Diagnosis"));
        assertTrue(diagnosis.contains("boolean reachable"),
                "\"Nothing came back at all\" must be distinguishable from \"the gateway said "
                        + "no\". The first is a DNS, TLS or firewall problem; the second means "
                        + "the address was right and something about the request was not.");
        assertTrue(diagnosis.contains("boolean accepted"),
                "A 401 and a 200-that-rejects-the-unknown-id are both responses, but only one "
                        + "means the request was valid. Collapsing them loses the diagnosis.");
        assertTrue(diagnosis.contains("int httpStatus"),
                "The status code is the most concrete thing the probe can report");
        assertTrue(diagnosis.contains("usable()"),
                "The probe's verdict has to be derivable, not re-decided at each call site");
    }

    @Test
    void aGatewaySuppliedMessageCannotFillThePage() throws IOException {
        String clip = methodBody(read(CLIENT), "clip");
        assertTrue(clip.contains("300"),
                "A gateway error body is untrusted text of unbounded length. Without a cap, a "
                        + "large response body becomes the admin page.");
        assertTrue(clip.contains("replaceAll"),
                "Whitespace is collapsed, so a multi-line body cannot break the layout it is "
                        + "rendered into");
    }

    @Test
    void theProbeSharesTheRequestBuilderWithPayments() throws IOException {
        String client = read(CLIENT);
        String exchange = methodBody(client, "exchange");
        String post = methodBody(client, "post");

        // The probe is only evidence about the payment path if both build the request
        // the same way. Two builders means a probe can pass while the header bug that
        // actually breaks checkout is untouched.
        // Exact shape, not just "mentions exchange". post() exists only to apply the
        // readiness gate and hand off; anything it decides about the outcome is a
        // decision the probe would not be exercising.
        // The last statement, not the presence of the expression: a null check inserted
        // before the return leaves "return exchange.json;" sitting in the body and
        // would pass a contains() test, so the claim has to be about what the method
        // actually does with the result.
        assertEquals("return exchange.json;",
                lastStatementIn(post),
                "post() must be a gate plus a straight delegation: return exchange.json. "
                        + "Deciding anything else there means the probe and the payment do "
                        + "not go through the same call.");
        // A guard on the result is what "deciding something" looks like in practice,
        // including a redundant one that changes no behaviour. Stating it as a count
        // keeps the rule about the shape of the method rather than about which
        // particular branch someone happened to write.
        assertEquals(1, countOccurrences(post, "if ("),
                "post() must contain exactly one condition: the readiness gate. A second "
                        + "condition means post interprets the gateway's answer, and anything it "
                        + "decides there is invisible to the probe.");
        assertTrue(exchange.contains("aba-username")
                        && exchange.contains("aba-secret")
                        && exchange.contains("Authorization"),
                "exchange() is the one place a request leaves the process, so it is the only "
                        + "place the credential headers can be correct for both the probe and a "
                        + "real payment");
        assertFalse(probeMentionsPost(client),
                "The probe must not reach around exchange() into a private request builder");
    }

    /** Whether any code builds an HttpRequest outside {@code exchange}. */
    private static boolean probeMentionsPost(String client) {
        String requestBuilding = methodBody(client, "precreate")
                + methodBody(client, "queryStatus")
                + methodBody(client, "diagnose");
        return requestBuilding.contains("HttpRequest.newBuilder()");
    }

    // ------------------------------------------------- the probe is an admin action

    @Test
    void theProbeIsAPostBehindItsOwnAction() throws IOException {
        String servlet = read(SERVLET);
        assertTrue(servlet.contains("\"test\".equals(request.getParameter(\"action\"))"),
                "The probe must be keyed on an explicit action");
        String doPost = methodBody(servlet, "doPost");
        assertTrue(doPost.contains("runConnectionTest"),
                "The probe must be handled from doPost. A GET probe would let any page on the "
                        + "internet make this server issue an authenticated request to ABA by "
                        + "embedding an image tag.");
    }

    @Test
    void theProbeIsAudited() throws IOException {
        String probe = methodBody(read(SERVLET), "runConnectionTest");
        assertTrue(probe.contains("logAdminAction"),
                "This is the only outbound authenticated call an admin can trigger on demand, "
                        + "so it belongs in the audit log like any other admin action");
        // The call being present is not the same as the call happening. An audit line
        // wrapped in a disabled condition reads identically in a diff and records nothing,
        // which defeats the point of auditing the one call that leaves this process.
        String auditCall = codeBefore(probe, "logAdminAction");
        assertFalse(auditCall.contains("//"),
                "The audit call appears to be commented out");
        assertFalse(auditCall.matches("(?s).*if\\s*\\(\\s*false.*"),
                "The audit call is guarded by a condition that cannot be true, so the probe "
                        + "leaves this host unlogged");
        assertFalse(auditCall.contains("return"),
                "The audit call appears to sit after an early return, so a probe that fails "
                        + "early would never be recorded");
        assertFalse(probe.contains("PaymentConfig.secret()"),
                "The audit line and the page must never carry the API secret. Recording "
                        + "reachability and status is what makes the probe traceable.");
    }

    @Test
    void theProbeFormCarriesATokenAndNoSettingsFields() throws IOException {
        String view = read(PAYMENTS_JSP);

        // Scoped to the probe's own form. The settings form has a token too, so a
        // page-wide contains() reports success whether or not the probe form has one,
        // and CSRFProtectionFilter validates every POST and exempts nothing -- a tokenless
        // probe form is a 403 the operator sees only by pressing the button.
        String probeForm = formAround(view, "value=\"test\"");
        assertTrue(probeForm.contains("name=\"csrfToken\""),
                "The probe form must carry a CSRF token. The settings form having one is not "
                        + "enough: they are separate forms and the filter checks per request.");
        assertTrue(probeForm.contains("name=\"action\" value=\"test\""),
                "The probe form must name its action");
        assertFalse(probeForm.contains("name=\"merchantId\"")
                        || probeForm.contains("name=\"abaSecret\""),
                "The probe must report on stored settings. A form carrying the credential "
                        + "fields would let a probe describe what was typed rather than what "
                        + "the store will use.");
    }

    @Test
    void theProbeResultIsRenderedNotOnlyFlashed() throws IOException {
        String view = read(PAYMENTS_JSP);
        assertTrue(view.contains("${probeMessage}"),
                "The gateway's own words are the whole value of the probe and must survive to "
                        + "the page");
        assertTrue(view.contains("${probeStatus}"),
                "The status code is the concrete evidence and must be shown");
        assertTrue(view.contains("not empty probe"),
                "The result block is conditional on the probe having run; without this guard "
                        + "the page renders an empty alert on every visit");
    }

    // ------------------------------------------- the page is actually reachable

    @Test
    void theAdminPaymentsPageIsNotGated() throws IOException {
        assertFalse(read(FILTER).contains("GATED.put(\"/admin/payments\""),
                "The payment configuration page is finished. Gating it leaves the store "
                        + "cash-only with no admin page that can change that, and the operator "
                        + "sees a coming soon modal that is indistinguishable from 'not built'.");
        assertFalse(read(WEB_XML).contains("<url-pattern>/admin/payments</url-pattern>"),
                "web.xml still routes /admin/payments through ComingSoonGateFilter. The filter "
                        + "looks configured in the descriptor while the page loads normally.");
    }

    @Test
    void simulationIsStillTheDefault() throws IOException {
        String config = read(SRC.resolve("core/config/PaymentConfig.java"));
        assertTrue(config.contains("bool(KEY_SIMULATE, true)"),
                "Simulation must remain the default. Un-gating the configuration page makes it "
                        + "reachable for the first time, and a default of false would mean a "
                        + "store with half-entered credentials starts charging real money.");
        assertTrue(config.contains("bool(KEY_ENABLED, false)"),
                "ABA must remain off by default, so an un-gated page does not silently enable a "
                        + "payment method on every existing install");
    }

    // ------------------------------------------------------------- self-checks

    /**
     * The extraction the checks above rely on has to be able to fail, and it has to
     * find the declaration rather than the first call. {@code diagnose} is called
     * nowhere else, but {@code post} is called by both payment paths, so a naive
     * first-match search on it returns a caller.
     */
    @Test
    void methodBodyExtractionFindsDeclarationsAndCanFail() throws IOException {
        String client = read(CLIENT);
        try {
            methodBody(client, "noSuchMethodAnywhere");
            fail("Expected extraction of a missing method to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("noSuchMethodAnywhere"));
        }

        String post = methodBody(client, "post");
        assertTrue(post.contains("return exchange.json"),
                "post() must delegate to exchange() rather than build its own request");
        assertFalse(post.contains("HttpRequest.newBuilder()"),
                "post() must not build the request itself; that belongs to exchange() so the "
                        + "probe and the payment share the same header construction");

        String diagnose = methodBody(client, "diagnose");
        assertTrue(diagnose.length() < client.length(),
                "methodBody returned the whole file rather than one method");
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "Expected to find " + path);
        return Files.readString(path);
    }

    /**
     * The body of one method, from its declaration to the brace that closes it.
     *
     * <p>Finds the declaration rather than the first mention, and counts braces while
     * skipping comments and string literals. Both matter here: this class is mostly
     * prose about gateways, and a comment or a quoted header name containing a brace
     * would otherwise end the extraction early and answer with the wrong method.
     */
    /**
     * The last statement of a method body, comments blanked.
 *
 * <p>Used to assert that a method does exactly one thing. A
 * {@code contains("return x;")} check is satisfied by a method that returns x in
 * three branches, which is the shape that lets a guard be inserted quietly.
 */
    private static String lastStatementIn(String body) {
        String code = body;
        int comment;
        while ((comment = indexOf(code, "//")) >= 0) {
            int end = newlineFrom(code, comment);
            code = blank(code, comment, end);
        }
        while ((comment = indexOf(code, "/*")) >= 0) {
            int end = code.indexOf("*/", comment);
            code = blank(code, comment, end < 0 ? code.length() : end + 2);
        }
        int lastBrace = code.lastIndexOf('}');
        if (lastBrace >= 0) {
            code = code.substring(0, lastBrace);
        }
        int lastSemicolon = code.lastIndexOf(';');
        if (lastSemicolon < 0) {
            throw new IllegalArgumentException("No statement found");
        }
        int statementStart = lastSemicolon;
        while (statementStart > 0 && code.charAt(statementStart - 1) != ';'
                && code.charAt(statementStart - 1) != '{'
                && code.charAt(statementStart - 1) != '}') {
            statementStart--;
        }
        return code.substring(statementStart, lastSemicolon + 1).strip();
    }

    private static String blank(String source, int from, int to) {
        StringBuilder builder = new StringBuilder(source);
        for (int i = from; i < to && i < builder.length(); i++) {
            builder.setCharAt(i, ' ');
        }
        return builder.toString();
    }

    /**
     * The whole {@code if (...) { condition that governs a marker.
 *
 * <p>Reads from the {@code if} that opens the condition to its closing brace, not
 * just the line in front of the marker. A guard disabled by a trailing
 * {@code && false} is invisible to a prefix scan, which is exactly the edit that
 * turns this test from a check into decoration.
 */
    private static String conditionAround(String source, String marker) {
        int at = source.indexOf(marker);
        assertTrue(at >= 0, "Could not find " + marker);
        int open = source.lastIndexOf("if (", at);
        assertTrue(open >= 0, "No 'if' governs " + marker);
        int lineStart = source.lastIndexOf('\n', open) + 1;
        int brace = source.indexOf('{', at);
        assertTrue(brace > open, "Could not find the end of the condition for " + marker);
        return source.substring(lineStart, brace + 1);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            count++;
        }
        return count;
    }

    /**
     * The one {@code <form>} containing a marker.
     *
     * <p>Scanning to the nearest opening tag before the marker and the nearest closing
     * tag after it. This view has two forms, and an assertion about "the probe form" that
     * reads the whole page will be satisfied by the other one.
     */
    private static String formAround(String view, String marker) {
        int markerAt = view.indexOf(marker);
        assertTrue(markerAt > 0, "Could not find " + marker + " in the view");
        int open = view.lastIndexOf("<form", markerAt);
        int close = view.indexOf("</form>", markerAt);
        assertTrue(open > 0 && close > open,
                "Could not delimit the form containing " + marker);
        return view.substring(open, close);
    }

    /**
     * The code on the line before an occurrence, with comments blanked out.
     *
     * <p>Needed because {@code contains} cannot tell a guard that runs from one that is
     * mentioned. These methods are surrounded by prose explaining what the guard is for,
     * and a javadoc {@code //}-free block above a {@code /*} comment would otherwise read
     * as a commented-out statement. Blanking comment runs first means an assertion about
     * whether a line is code is made against the code only.
     */
    private static String codeBefore(String source, String marker) {
        int at = source.indexOf(marker);
        assertTrue(at >= 0, "Could not find " + marker);
        int lineStart = source.lastIndexOf('\n', at) + 1;
        StringBuilder cleaned = new StringBuilder(source.substring(lineStart, at));
        int comment;
        while ((comment = indexOf(cleaned, "//")) >= 0) {
            int end = newlineFrom(cleaned, comment);
            for (int i = comment; i < end; i++) {
                cleaned.setCharAt(i, ' ');
            }
        }
        while ((comment = indexOf(cleaned, "/*")) >= 0) {
            int end = cleaned.indexOf("*/", comment);
            end = end < 0 ? cleaned.length() : end + 2;
            for (int i = comment; i < end; i++) {
                cleaned.setCharAt(i, ' ');
            }
        }
        return cleaned.toString();
    }

    private static int indexOf(CharSequence haystack, String needle) {
        for (int i = 0; i + needle.length() <= haystack.length(); i++) {
            if (matchesAt(haystack, needle, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matchesAt(CharSequence haystack, String needle, int at) {
        for (int j = 0; j < needle.length(); j++) {
            if (haystack.charAt(at + j) != needle.charAt(j)) {
                return false;
            }
        }
        return true;
    }

    private static int newlineFrom(CharSequence haystack, int at) {
        for (int i = at; i < haystack.length(); i++) {
            if (haystack.charAt(i) == '\n') {
                return i;
            }
        }
        return haystack.length();
    }

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

    /**
     * The offset of a method's declaration.
     *
     * <p>A call site also reads as {@code name(}, and both payment paths call
     * {@code post}, so a plain {@code indexOf} would return {@code precreate} and every
     * assertion about {@code post} would be made against the wrong method. A
     * declaration is preceded on its line by a return type or modifiers and never by
     * a {@code .}.
     */
    private static int declarationOf(String source, String methodName) {
        int from = 0;
        while (true) {
            int hit = source.indexOf(methodName + "(", from);
            if (hit < 0) {
                throw new IllegalArgumentException("No declaration of " + methodName);
            }
            String prefix = source.substring(source.lastIndexOf('\n', hit) + 1, hit);
            boolean notQualified = hit == 0 || source.charAt(hit - 1) != '.';
            if (notQualified && isDeclarationPrefix(prefix)) {
                return hit;
            }
            from = hit + 1;
        }
    }

    private static boolean isDeclarationPrefix(String prefix) {
        String trimmed = prefix.strip();
        if (trimmed.isEmpty() || trimmed.contains("=") || trimmed.contains("(")) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_' || c == '$'
                    || c == '<' || c == '>' || c == '.' || c == ' ' || c == ',';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}