package com.hengtongan.computerstore.web.controller.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards that the 6-digit password-reset flow can actually be walked.
 *
 * <h2>The bug this exists to prevent</h2>
 *
 * The code flow was complete and correct: a six-digit code is minted, hashed with
 * SHA-256, emailed, verified against the database, rate limited, capped at five
 * wrong guesses, and the password change reads the account from the code row rather
 * than the request. Every one of those was right.
 *
 * It was also unreachable. {@code /forgot} emailed a <em>link</em> and stopped.
 * The only caller of {@code requestResetWithCode} was {@code /resend-code}, which
 * is only linked from the verify page, which is only reachable by typing
 * {@code /verify-code} by hand. And nothing on the sign-in page linked to
 * {@code /forgot} at all, so the whole feature -- link flow included -- could not
 * be entered from the UI.
 *
 * <p>That is why the service layer, the schema, the repository and the rate limiter
 * were all green while the feature did not work. Every test that existed checked a
 * component. None checked the path between the login form and a delivered code,
 * which is the only thing a customer experiences.
 *
 * <h2>What is checked here</h2>
 *
 * The chain, link by link: a visitor can get from the sign-in page to a delivered
 * code, and each step carries what the next one needs. Plus the one thing that
 * would be easy to break while fixing this -- see
 * {@link #requestingACodeMustNotUnlockTheResetStep()}.
 *
 * <h2>What is not checked</h2>
 *
 * That SMTP delivers. That needs a real mailbox and credentials, neither of which
 * exists in a test. Everything here reads source: a green suite says the flow is
 * connected and each hand-off carries the right value, not that a message arrives.
 */
class PasswordResetCodeReachabilityTest {

    private static final Path AUTH = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/controller/auth");
    private static final Path VIEWS = Path.of("src/main/webapp/WEB-INF/views/auth");
    private static final Path FORGOT = AUTH.resolve("ForgotPasswordServlet.java");
    private static final Path VERIFY = AUTH.resolve("VerifyCodeServlet.java");
    private static final Path NEW_PASSWORD = AUTH.resolve("NewPasswordServlet.java");
    private static final Path RESEND = AUTH.resolve("ResendCodeServlet.java");
    private static final Path LOGIN_JSP = VIEWS.resolve("login.jsp");
    private static final Path FORGOT_JSP = VIEWS.resolve("forgot-password.jsp");
    private static final Path VERIFY_JSP = VIEWS.resolve("verify-code.jsp");
    private static final Path NEW_PASSWORD_JSP = VIEWS.resolve("new-password.jsp");

    // ------------------------------------------------------------ the chain

    @Test
    void theSignInPageOffersAWayIntoRecovery() throws IOException {
        // The first link in the chain, and the one that was simply absent. Every
        // other page in the flow was reachable only by typing its URL.
        // An anchor with href to /forgot, not the string "/forgot" anywhere: the page
        // also names the path in prose, so a plain contains() stays green when the
        // link itself is deleted -- which is exactly the state this shipped in.
        assertTrue(hasAnchorTo(read(LOGIN_JSP), "/forgot"),
                "The login page must link to /forgot. Without it the recovery flow cannot be "
                        + "entered from the UI at all, no matter how correct the rest of it is.");
    }

    @Test
    void theForgotPageAsksForAnEmailAndPostsToItself() throws IOException {
        String view = read(FORGOT_JSP);
        String form = formAround(view, "action=\"${pageContext.request.contextPath}/forgot\"");
        assertTrue(form.contains("type=\"email\""),
                "The recovery form must ask for an email address");
        assertTrue(form.contains("name=\"email\""),
                "The email field must be named email, which is what the controller reads");
        assertTrue(form.contains("name=\"csrfToken\""),
                "CSRFProtectionFilter validates every POST and exempts nothing");
    }

    @Test
    void theForgotStepActuallyEmailsACode() throws IOException {
        String post = methodBody(read(FORGOT), "protected void doPost");
        assertTrue(post.contains("requestResetWithCode"),
                "POST /forgot must call requestResetWithCode. It used to call requestReset, "
                        + "which emails a link, so the code flow had no entry point and the "
                        + "only way to start one was to hand-type /verify-code.");
        assertTrue(post.contains("redirect(request, response, \"/verify-code\")"),
                "After sending, the flow must continue to the verify step. Stopping on "
                        + "/forgot leaves the customer holding a code and nowhere to type it.");
    }

    @Test
    void theVerifyPageIsActuallyReachableAndRenders() throws IOException {
        String view = read(VERIFY_JSP);
        assertTrue(view.contains("action=\"${pageContext.request.contextPath}/verify-code\""),
                "The verify page must post to /verify-code");
        assertTrue(view.contains("name=\"code\""),
                "The verify form must carry the code field");
        // The email is shown so the customer can tell whether a code went to the
        // right inbox, but it comes from the session now, not the query string.
        assertTrue(view.contains("${email}"),
                "The verify page must show which address it is asking about");
    }

    @Test
    void theVerifyStepReadsTheAddressTheForgotStepPinned() throws IOException {
        String get = methodBody(read(VERIFY), "protected void doGet");
        assertTrue(get.contains("SESSION_PENDING_EMAIL"),
                "GET /verify-code must read the pending address from the session. It used to "
                        + "read it only from the query string, which is why /forgot could not "
                        + "redirect here without putting the address in the URL.");
        assertTrue(get.contains("redirect(request, response, \"/forgot\")"),
                "With no pending address and none in the URL there is nothing to verify, so "
                        + "the customer goes back rather than seeing an empty code box.");
    }

    @Test
    void theNewPasswordStepTakesNoEmailFromTheForm() throws IOException {
        // Pre-existing invariant, kept because this work touched the session keys
        // around it. If it ever fails the feature is an account-takeover primitive:
        // verify a code for your own address, then post somebody else's.
        assertFalse(read(NEW_PASSWORD).contains("getParameter(\"email\")"),
                "/new-password must never read the account from the request. The address must "
                        + "come from the session, where it was pinned by a verified code.");
    }

    // ------------------------------------------- the pending key proves nothing

    @Test
    void requestingACodeMustNotUnlockTheResetStep() throws IOException {
        // This is the trap in the fix. The obvious way to let /verify-code know which
        // address it is waiting on is to reuse NewPasswordServlet.SESSION_EMAIL --
        // the key that /new-password treats as "a code for this address was verified".
        //
        // Reusing it would mean GET /forgot hands every visitor who types an address a
        // session that reaches the password-change form with no code verified at all.
        // The takeover test that already exists would keep passing, because
        // VerifyCodeServlet still overwrites the same key on success.
        String forgot = read(FORGOT);
        assertTrue(forgot.contains("VerifyCodeServlet.SESSION_PENDING_EMAIL"),
                "/forgot must pin the address under the pending key");
        // Scoped to the write, not the file: the comment explaining why this key is
        // the wrong one necessarily names the wrong one, and a whole-file contains()
        // would then forbid the explanation along with the mistake.
        assertFalse(writesVerifiedEmailKey(forgot),
                "/forgot must not write the verified-email key. That key is what unlocks "
                        + "/new-password; setting it here would let anyone who types any "
                        + "address reach the password form without verifying a code.");
        assertFalse(writesVerifiedEmailKey(read(RESEND)),
                "/resend-code must not write the verified-email key either, for the same reason.");

        // And the two keys must genuinely be different values, so the two steps
        // cannot collide even though they both live in the session.
        String pending = declarationOf(read(VERIFY), "SESSION_PENDING_EMAIL");
        String verified = declarationOf(read(NEW_PASSWORD), "SESSION_EMAIL");
        assertFalse(pending.contains("\"passwordResetEmail\""),
                "The pending key must not reuse the verified key's session attribute name. "
                        + "Sharing a name shares a slot, and the reset step would be unlocked "
                        + "by a code merely being requested.");
    }

    @Test
    void theVerifiedAddressIsOnlyWrittenAfterACodeIsVerified() throws IOException {
        String post = methodBody(read(VERIFY), "protected void doPost");
        int verifyCall = post.indexOf("verifyCode(");
        int sessionWrite = post.indexOf("NewPasswordServlet.SESSION_EMAIL");
        assertTrue(verifyCall > 0, "doPost must call verifyCode");
        assertTrue(sessionWrite > verifyCall,
                "The verified-email key must be written after verifyCode succeeds, never "
                        + "before. Writing it first would unlock /new-password on any POST, "
                        + "verified or not.");
        assertTrue(post.contains("SESSION_PENDING_EMAIL") && post.contains("removeAttribute"),
                "doPost must clear the pending key once the code is spent, so the same "
                        + "session cannot start comparing codes for that address again.");
    }

    @Test
    void theVerifyStepIgnoresATamperedEmailField() throws IOException {
        // The hidden email field is a rendering convenience. The session is the
        // authority, so a tampered field cannot redirect the lookup at another
        // account's code.
        String post = methodBody(read(VERIFY), "protected void doPost");
        int sessionRead = post.indexOf("SESSION_PENDING_EMAIL");
        int paramRead = post.indexOf("getParameter(\"email\")");
        assertTrue(sessionRead >= 0, "doPost must read the pending address");
        assertTrue(paramRead < 0 || sessionRead < paramRead,
                "The session must be consulted before the form field, and the form field "
                        + "only as a fallback.");
    }

    // ------------------------------------------ an unconfigured server says so

    @Test
    void anUnconfiguredServerSaysSoRatherThanPromisingACode() throws IOException {
        // The code flow is now the primary path, so a store with no SMTP settings
        // shows the customer "we've sent a 6-digit code" and delivers nothing. The
        // customer waits on an inbox that stays empty and concludes the address is
        // wrong. .env.example already promised the app "says so on the screen";
        // nothing did.
        assertTrue(methodBody(read(FORGOT), "protected void doGet").contains("mailConfigured"),
                "GET /forgot must publish whether email is configured, so the page can warn "
                        + "instead of promising a code that will never arrive.");
        assertTrue(read(FORGOT_JSP).contains("mailConfigured"),
                "The recovery page must act on that. Publishing the flag and ignoring it is "
                        + "the same as not publishing it.");
        // Uniform either way: the warning must not vary with whether the address is
        // registered, or it becomes an account-existence oracle.
        String warning = read(FORGOT_JSP);
        assertTrue(warning.contains("Email is not configured"),
                "The page must say plainly that email is not set up. A vague message leaves "
                        + "the customer waiting; this one tells them who can fix it.");
        assertFalse(warning.contains("${email}exists") || warning.contains("account exists"),
                "The unconfigured warning must not vary by address, or it reveals which "
                        + "addresses are registered.");
    }

    @Test
    void theDeliveryFailureIsNotSilentlySwallowed() throws IOException {
        // The service returns void, so a failed send cannot reach the page. The
        // honest options are the pre-send configuration check above or reporting
        // the outcome; what must not happen is silence.
        String service = read(Path.of(
                "src/main/java/com/hengtongan/computerstore/core/service/PasswordResetService.java"));
        int send = service.indexOf("requestResetWithCode");
        assertTrue(send > 0, "The service still owns code minting");
        assertTrue(service.contains("EmailUtil.send("),
                "The code must actually be emailed. Minting and hashing a code that is "
                        + "never sent is a reset flow that always fails.");
    }

    // ------------------------------------------------- the resend side door

    @Test
    void resendKeepsTheFlowOnTheSessionRatherThanTheUrl() throws IOException {
        String post = methodBody(read(RESEND), "protected void doPost");
        assertTrue(post.contains("requestResetWithCode"),
                "/resend-code must mint a new code");
        // The write specifically. requestResetWithCode invalidates every prior code for
        // the address, so if resend does not re-pin it, /verify-code finds no
        // pending address and bounces the customer back to /forgot. A read of the
        // key is not enough, and reading it happens on the line above.
        assertTrue(writesPendingEmailKey(post),
                "Resending invalidates every prior code for the address, so the session must "
                        + "re-pin the address or /verify-code loses it and bounces to /forgot.");
        assertFalse(post.contains("/verify-code?email="),
                "The resend redirect must not put the address in the query string, where it "
                        + "lands in browser history and goes out in the Referer header.");
    }

    @Test
    void theVerifyPageOffersResendAndLinksBackToStartOver() throws IOException {
        String view = read(VERIFY_JSP);
        assertTrue(view.contains("/resend-code"),
                "A customer whose code expired or went to spam needs a way to ask for "
                        + "another one without restarting from scratch.");
        assertTrue(view.contains("/forgot"),
                "There must be a way back to /forgot for a customer who typed the wrong "
                        + "address; without it they are stuck on a page about an address that "
                        + "is not theirs.");
    }

    @Test
    void theNewPasswordStepRendersAfterVerification() throws IOException {
        String view = read(NEW_PASSWORD_JSP);
        assertTrue(view.contains("name=\"newPassword\""),
                "The final step must offer a new-password field");
        assertTrue(view.contains("name=\"confirmPassword\""),
                "The final step must confirm the new password");
        assertTrue(view.contains("name=\"csrfToken\""),
                "CSRFProtectionFilter validates every POST and exempts nothing");
    }

    // ------------------------------------------------------------ the defaults

    @Test
    void aForgottenMethodFieldStillSendsACode() throws IOException {
        // The form sends method=link on the secondary button and nothing on the
        // primary. So "no method" has to mean the code flow, not the link flow: an
        // older cached form, or a stripped button, must not silently downgrade a
        // customer to the weaker flow.
        String post = methodBody(read(FORGOT), "protected void doPost");
        assertTrue(post.contains("\"link\".equals(request.getParameter(\"method\"))"),
                "The link flow must be opt-in by an explicit method=link. Testing it that "
                        + "way makes the code flow the default for anything else.");
    }

    @Test
    void theLinkFlowIsStillAvailable() throws IOException {
        // Deliberately not deleted. A mailed link is a legitimate option and the
        // token path already exists and is tested; removing it would be a scope
        // change, not a fix.
        String post = methodBody(read(FORGOT), "protected void doPost");
        assertTrue(post.contains("requestReset("),
                "The existing single-use link flow must keep working alongside the code flow.");
        assertTrue(read(FORGOT_JSP).contains("value=\"link\""),
                "The link flow must still be offered to the customer.");
    }

    // ------------------------------------------------------------- self-checks

    /**
     * The extraction has to find declarations, not the first call. {@code doPost}
     * appears in all four servlets, and {@code verifyCode} is called above the
     * constant named {@code SESSION_EMAIL}, so a naive search answers with the
     * wrong method and the assertions pass against the wrong text.
     */
    @Test
    void extractionFindsDeclarationsAndCanFail() throws IOException {
        try {
            methodBody(read(FORGOT), "noSuchMethodAnywhere");
            fail("Expected extraction of a missing method to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("noSuchMethodAnywhere"));
        }

        String post = methodBody(read(VERIFY), "protected void doPost");
        assertTrue(post.contains("verifyCode("),
                "methodBody matched a call rather than the declaration");
        assertFalse(post.contains("class VerifyCodeServlet"),
                "methodBody returned the class rather than one method");
    }

    // ------------------------------------------------------------- utilities

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "Expected to find " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Whether a file writes the key that unlocks {@code /new-password}.
     *
     * <p>Looks for an actual {@code setAttribute} of it. A plain search for the
     * constant's name would also match the comments explaining why it must not be
     * written, which is the wrong way round: the explanation is the thing keeping
     * the mistake from being made.
     */
    /**
     * Whether the source contains a real anchor pointing at a path.
 *
     * <p>Requires {@code href} on the same line. A path named in prose, a comment
 * * or a form action is not a way in for a customer to click.
 */
    private static boolean hasAnchorTo(String view, String path) {
        for (String line : view.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("//") || trimmed.contains("<%--")) {
                continue;
            }
            if (trimmed.contains("<a ") && trimmed.contains("href=")
                    && trimmed.contains(path)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the source actually assigns the pending-email session key. */
    private static boolean writesPendingEmailKey(String source) {
        for (String line : source.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            if (trimmed.contains("setAttribute(VerifyCodeServlet.SESSION_PENDING_EMAIL")) {
                return true;
            }
        }
        return false;
    }

    private static boolean writesVerifiedEmailKey(String source) {
        for (String line : source.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            if (trimmed.contains("setAttribute(NewPasswordServlet.SESSION_EMAIL")) {
                return true;
            }
        }
        return false;
    }

    /** The one {@code <form>} containing a marker. */
    private static String formAround(String view, String marker) {
        int at = view.indexOf(marker);
        assertTrue(at > 0, "Could not find " + marker);
        int open = view.lastIndexOf("<form", at);
        int close = view.indexOf("</form>", at);
        assertTrue(open > 0 && close > open, "Could not delimit the form containing " + marker);
        return view.substring(open, close);
    }

    /** The declaration line of a constant, so its value can be asserted. */
    private static String declarationOf(String source, String name) {
        int from = 0;
        while (true) {
            int hit = source.indexOf(name, from);
            if (hit < 0) {
                throw new IllegalArgumentException("No declaration of " + name);
            }
            int lineStart = source.lastIndexOf('\n', hit) + 1;
            int lineEnd = source.indexOf('\n', hit);
            String line = source.substring(lineStart, lineEnd < 0 ? source.length() : lineEnd);
            if (line.contains("=")) {
                return line;
            }
            from = hit + 1;
        }
    }

    /** The body of a method located by its signature text. */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new IllegalArgumentException("Method not found: " + signature);
        }
        int open = source.indexOf('{', start);
        if (open < 0) {
            throw new IllegalArgumentException("No body after: " + signature);
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        throw new IllegalArgumentException("Unbalanced braces after: " + signature);
    }
}