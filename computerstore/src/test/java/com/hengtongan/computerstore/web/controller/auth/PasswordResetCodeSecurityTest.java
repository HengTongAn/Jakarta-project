package com.hengtongan.computerstore.web.controller.auth;

import com.hengtongan.computerstore.core.domain.entity.PasswordResetCode;
import com.hengtongan.computerstore.core.service.PasswordResetService;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Locks down the 6-digit forgot-password code flow.
 *
 * <h2>The defect this exists to prevent</h2>
 *
 * {@code NewPasswordServlet} used to take the code id from the session but the
 * email from the form, and {@code PasswordResetService.completeResetWithCode}
 * looked the account up by that email. Since {@code claimForUse} only checks
 * that a code is unused and unexpired -- not that it was issued for the address
 * being reset -- anyone could verify a code mailed to their own address and
 * then post a victim's email to rewrite the victim's password. It is a
 * complete account takeover, and it reaches the admin account.
 *
 * <p>The same step had a second fault. Login reads the user through
 * {@code findByUsername}, which is cached for hours, while the transactional
 * {@code updatePassword} overload never invalidated that cache. A successful
 * reset therefore left the old password working and rejected the new one --
 * the exact opposite of what the feature promises.</p>
 *
 * <h2>What is not checked</h2>
 *
 * That the code is emailed, hashed, and matched in the database. Those need the
 * servlet and the database running. This test holds the half that can be read
 * off the source: which inputs the flow is allowed to trust.
 */
class PasswordResetCodeSecurityTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/com/hengtongan/computerstore/core/service/PasswordResetService.java");
    private static final Path NEW_PASSWORD = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/controller/auth/NewPasswordServlet.java");
    private static final Path VERIFY_CODE = Path.of(
            "src/main/java/com/hengtongan/computerstore/web/controller/auth/VerifyCodeServlet.java");
    private static final Path EMAIL_UTIL = Path.of(
            "src/main/java/com/hengtongan/computerstore/infrastructure/messaging/EmailUtil.java");
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Extracts a method body by brace matching, so a check can target one method. */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            fail("method not found in source: " + signature);
        }
        int open = source.indexOf('{', start);
        if (open < 0) {
            fail("no body after: " + signature);
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
        fail("unbalanced braces after: " + signature);
        return "";
    }

    // --- the takeover ------------------------------------------------------

    @Test
    void theResetStepNeverTakesTheAccountFromTheRequest() throws IOException {
        String source = read(NEW_PASSWORD);
        assertFalse(source.contains("getParameter(\"email\")"),
                NEW_PASSWORD + " must not read the account from the form. The email on the "
                        + "request is attacker-controlled: pairing a session's own verified code "
                        + "with somebody else's address is how any user's password was reset.");
    }

    @Test
    void theResetStepTakesTheVerifiedEmailFromTheSession() throws IOException {
        String body = methodBody(read(NEW_PASSWORD), "protected void doPost");
        assertTrue(body.contains("SESSION_EMAIL"),
                NEW_PASSWORD + " doPost must read the verified address from the session, which is "
                        + "the one the code was actually issued for");
        assertTrue(body.contains("completeResetWithCode(codeId, verifiedEmail"),
                NEW_PASSWORD + " must pass the session-pinned address to the service so the "
                        + "service can check it against the code row");
    }

    @Test
    void theServiceDerivesTheAccountFromTheCodeRowNotTheSuppliedAddress() throws IOException {
        String body = methodBody(read(SERVICE), "public void completeResetWithCode");
        assertFalse(body.contains("findByEmail"),
                "completeResetWithCode must not look the account up by a supplied email -- that "
                        + "email is chosen by whoever is calling, so it decides whose password "
                        + "gets rewritten. Read user_id off the verified code row instead.");
        assertTrue(body.contains("getUserId()"),
                "completeResetWithCode must take user_id from the code row it just loaded");
        assertTrue(body.contains("equalsIgnoreCase"),
                "completeResetWithCode must check the supplied address against the code row's own "
                        + "email, so a code cannot be aimed at a different account");
    }

    @Test
    void verifyingPinsBothTheCodeIdAndTheAddressToTheSession() throws IOException {
        String body = methodBody(read(VERIFY_CODE), "protected void doPost");
        assertTrue(body.contains("SESSION_CODE_ID") && body.contains("SESSION_EMAIL"),
                VERIFY_CODE + " doPost must pin the verified code id AND the address it was "
                        + "verified for; pinning only the id leaves the final step with no "
                        + "way to tell whose account it may touch");
    }

    @Test
    void theDetectorReportsTheOriginalTakeover() {
        // The pre-fix shapes. If these ever stop being reported the checks above
        // have gone quiet and would pass against vulnerable code.
        assertTrue(usesSuppliedEmail("User user = userDAO.findByEmail(email.trim());"),
                "looking the account up by the supplied email is the takeover and must be reported");
        assertFalse(usesSuppliedEmail("userDAO.updatePassword(c, resetCode.getUserId(), h);"),
                "taking user_id from the verified code row is the fix and must be accepted");
        assertTrue(readsEmailFromRequest("String email = request.getParameter(\"email\");"),
                "reading the account from the request is the takeover and must be reported");
        assertFalse(readsEmailFromRequest("String e = (String) session.getAttribute(SESSION_EMAIL);"),
                "reading the account from the session is the fix and must be accepted");
    }

    private static boolean usesSuppliedEmail(String body) {
        return body.contains("findByEmail");
    }

    private static boolean readsEmailFromRequest(String body) {
        return body.contains("getParameter(\"email\")");
    }

    // --- the stale cache ---------------------------------------------------

    @Test
    void everyTransactionalPasswordChangeInvalidatesTheUserCache() throws IOException {
        String source = read(SERVICE);
        int writes = countOccurrences(source, "updatePassword(c,");
        int invalidations = countOccurrences(source, "invalidateCachedUsers()");
        assertTrue(writes > 0,
                "expected transactional password writes in " + SERVICE + " -- a silent zero here "
                        + "would make this test useless.");
        assertEquals(writes, invalidations,
                SERVICE + " updates the password on the caller's own transaction connection, which "
                        + "cannot invalidate the cache itself (it does not know when the caller "
                        + "commits). Login reads the user through the cached findByUsername, so "
                        + "each write needs a matching invalidateCachedUsers() after the commit. "
                        + "Without it the old password keeps working and the new one is rejected.");
    }

    @Test
    void theUserCacheHelperIsAvailableToCallers() {
        // The non-transactional overload invalidates in a finally block. The
        // transactional one cannot, which is why the service has to call this.
        assertTrue(hasMethod("com.hengtongan.computerstore.core.repository.UserRepository",
                        "invalidateCachedUsers"),
                "UserRepository.invalidateCachedUsers() is missing, so the transactional password "
                        + "writers have no way to drop the stale user cache entry");
    }

    // --- guessing the code -------------------------------------------------

    @Test
    void theAttemptBudgetIsSmallEnoughToMatter() {
        assertTrue(PasswordResetService.MAX_CODE_ATTEMPTS > 0,
                "a zero budget would lock every customer out of their own reset");
        assertTrue(PasswordResetService.MAX_CODE_ATTEMPTS <= 10,
                "a 6-digit code has a million possible values; a budget of "
                        + PasswordResetService.MAX_CODE_ATTEMPTS + " leaves the code brute-forceable "
                        + "in a way a customer cannot notice");
    }

    @Test
    void aCodeIsExhaustedOnlyOnceTheBudgetIsSpent() {
        PasswordResetCode code = new PasswordResetCode();
        code.setFailedAttempts(0);
        assertFalse(code.isExhausted(5), "a brand new code must still be usable");

        code.setFailedAttempts(4);
        assertFalse(code.isExhausted(5), "the last allowed guess must not already be exhausted");

        code.setFailedAttempts(5);
        assertTrue(code.isExhausted(5), "reaching the budget must exhaust the code");
    }

    @Test
    void aUsedOrExpiredCodeIsNeverUsable() {
        PasswordResetCode code = new PasswordResetCode();
        code.setExpiresAt(new Timestamp(System.currentTimeMillis() + 60_000));
        assertTrue(code.isUsable(), "a live code with no failed attempts must be usable");

        code.setUsedAt(new Timestamp(System.currentTimeMillis()));
        assertFalse(code.isUsable(), "a burned code must not be usable");

        code.setUsedAt(null);
        code.setExpiresAt(new Timestamp(System.currentTimeMillis() - 1));
        assertFalse(code.isUsable(), "an expired code must not be usable");
    }

    @Test
    void wrongGuessesAreChargedAgainstTheIssuedCode() throws IOException {
        String body = methodBody(read(SERVICE), "public int verifyCode");
        assertTrue(body.contains("isExhausted"),
                "verifyCode must refuse a code whose attempt budget is already spent");
        assertTrue(body.contains("chargeFailedAttempt"),
                "verifyCode must charge a wrong guess on every rejection; otherwise the 6-digit "
                        + "key space can be ground one guess at a time");

        // The charging itself lives in a helper so a database failure there can be
        // swallowed without masking the caller's "invalid code" error.
        String charge = methodBody(read(SERVICE), "private void chargeFailedAttempt");
        assertTrue(charge.contains("recordFailedAttempt"),
                "chargeFailedAttempt must persist the attempt against the issued code");
        assertTrue(charge.contains("invalidateForEmail"),
                "chargeFailedAttempt must burn the code once the budget is spent, otherwise the "
                        + "attacker simply keeps guessing against the same code");
    }

    // --- the request must not be an unlimited oracle -----------------------

    @Test
    void theCodeEndpointsAreRateLimited() throws IOException {
        List<String> mappings = filterMappings(read(WEB_XML), "RateLimitingFilter");
        assertTrue(!mappings.isEmpty(),
                "RateLimitingFilter has no url-patterns in web.xml; a silent zero here would "
                        + "make this test useless.");

        List<String> unmapped = new ArrayList<>();
        for (String path : List.of("/forgot", "/verify-code", "/new-password", "/resend-code")) {
            if (!mappings.contains(path)) {
                unmapped.add(path);
            }
        }
        if (!unmapped.isEmpty()) {
            fail("RateLimitingFilter does not cover " + unmapped + ". A 6-digit code is a "
                    + "guessable secret, so the endpoint that checks it must not accept unlimited "
                    + "POSTs from one client.");
        }
    }

    @Test
    void theDetectorCatchesAnUnmappedEndpoint() {
        assertFalse(rateLimitedEndpoints("<url-pattern>/forgot</url-pattern>").contains("/verify-code"),
                "an endpoint missing from the mapping must be reported");
        assertTrue(rateLimitedEndpoints(
                        "<url-pattern>/forgot</url-pattern><url-pattern>/verify-code</url-pattern>")
                        .contains("/verify-code"),
                "a mapped endpoint must be accepted");
    }

    // --- real mail ---------------------------------------------------------

    @Test
    void mailSettingsAreReadFromTheEnvironment() throws IOException {
        String source = read(EMAIL_UTIL);
        for (String envVar : List.of("MAIL_SMTP_HOST", "MAIL_SMTP_PORT", "MAIL_SMTP_AUTH",
                "MAIL_SMTP_STARTTLS", "MAIL_FROM", "MAIL_USERNAME", "MAIL_PASSWORD")) {
            assertTrue(source.contains("\"" + envVar + "\""),
                    EMAIL_UTIL + " must resolve " + envVar + " through AppConfig's environment "
                            + "layer, the same way DBConnection resolves DB_URL. Passing null "
                            + "there means a MAIL_* entry in .env is silently ignored and the "
                            + "forgot-password code never reaches a real inbox.");
        }
    }

    @Test
    void everyMailLookupNamesItsEnvironmentVariable() throws IOException {
        Matcher m = Pattern.compile("AppConfig\\.get\\(([^,]*),").matcher(read(EMAIL_UTIL));
        int checked = 0;
        while (m.find()) {
            checked++;
            assertFalse(m.group(1).trim().equals("null"),
                    "AppConfig.get(" + m.group(1).trim() + ", ...) skips the environment layer; "
                            + "pass the MAIL_* variable name instead");
        }
        assertTrue(checked >= 7,
                "expected all seven mail settings to be resolved through AppConfig, found "
                        + checked + " -- a silent zero here would make this test useless.");
    }

    // --- helpers -----------------------------------------------------------

    private static List<String> filterMappings(String webXml, String filterName) {
        int filterAt = webXml.indexOf("<filter-name>" + filterName + "</filter-name>");
        if (filterAt < 0) {
            return List.of();
        }
        int mappingAt = webXml.indexOf("<filter-mapping>", filterAt);
        if (mappingAt < 0) {
            return List.of();
        }
        int end = webXml.indexOf("</filter-mapping>", mappingAt);
        return rateLimitedEndpoints(webXml.substring(mappingAt, end));
    }

    private static List<String> rateLimitedEndpoints(String mappingBlock) {
        List<String> paths = new ArrayList<>();
        Matcher m = Pattern.compile("<url-pattern>([^<]+)</url-pattern>").matcher(mappingBlock);
        while (m.find()) {
            paths.add(m.group(1).trim());
        }
        return paths;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    private static boolean hasMethod(String className, String methodName) {
        try {
            for (java.lang.reflect.Method m : Class.forName(className).getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException e) {
            fail("class not found: " + className);
            return false;
        }
    }
}
