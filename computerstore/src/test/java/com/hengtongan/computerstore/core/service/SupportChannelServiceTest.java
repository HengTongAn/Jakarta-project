package com.hengtongan.computerstore.core.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hermetic tests for support-channel destination validation (no database
 * involved). This is the check standing between an admin-entered value and an
 * {@code href} in the site footer, so the rejection cases matter more than the
 * acceptance ones.
 */
class SupportChannelServiceTest {

    private final SupportChannelService service = new SupportChannelService();

    @Test
    void acceptsRealDestinations() {
        assertNull(service.validate("https://facebook.com/apachpcstore"));
        assertNull(service.validate("http://m.me/apachpcstore"));
        assertNull(service.validate("https://t.me/apachpcstore"));
        assertNull(service.validate("https://x.com/apachpcstore"));
    }

    @Test
    void acceptsBlankAsClear() {
        assertNull(service.validate(null));
        assertNull(service.validate(""));
        assertNull(service.validate("   "));
    }

    @Test
    void acceptsSurroundingWhitespace() {
        // Trimmed on save, so leading/trailing spaces are not a rejection.
        assertNull(service.validate("  https://t.me/apachpcstore  "));
    }

    @Test
    void rejectsNonHttpSchemes() {
        // The footer writes this value into an href, so anything that is not
        // http(s) is a script-injection vector, not a typo.
        assertNotNull(service.validate("javascript:alert(1)"));
        assertNotNull(service.validate("JavaScript:alert(1)"));
        assertNotNull(service.validate("data:text/html,<script>alert(1)</script>"));
        assertNotNull(service.validate("vbscript:msgbox(1)"));
        assertNotNull(service.validate("ftp://example.com/support"));
        assertNotNull(service.validate("//example.com/support"));
    }

    @Test
    void rejectsProtocolRelativeAndBareHost() {
        assertNotNull(service.validate("example.com/support"));
        assertNotNull(service.validate("facebook.com/apachpcstore"));
    }

    @Test
    void rejectsControlCharacters() {
        assertNotNull(service.validate("https://example.com/\nsupport"));
        assertNotNull(service.validate("https://example.com/\tsupport"));
    }

    @Test
    void rejectsValueLongerThanTheColumn() {
        // setting_value is VARCHAR(500); anything longer would be truncated by
        // MySQL, silently storing a different URL than the admin entered.
        assertNull(service.validate("https://example.com/" + "a".repeat(480)));
        assertNotNull(service.validate("https://example.com/" + "a".repeat(600)));
    }

    @Test
    void rejectionMessageNamesTheProblem() {
        String problem = service.validate("javascript:alert(1)");
        assertTrue(problem.contains("http"), "expected the message to say what is accepted: " + problem);
    }
}
