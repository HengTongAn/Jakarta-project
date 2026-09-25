package com.hengtongan.computerstore.util.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilTest {

    @Test
    void testIsBlank() {
        assertTrue(ValidationUtil.isBlank(null));
        assertTrue(ValidationUtil.isBlank(""));
        assertTrue(ValidationUtil.isBlank("   "));
        assertTrue(ValidationUtil.isBlank("\t\n\r"));
        assertFalse(ValidationUtil.isBlank("hello"));
        assertFalse(ValidationUtil.isBlank(" hello "));
    }

    @Test
    void testIsValidEmail() {
        assertTrue(ValidationUtil.isValidEmail("user@example.com"));
        assertTrue(ValidationUtil.isValidEmail("user.name@example.com"));
        assertTrue(ValidationUtil.isValidEmail("user+tag@example.co.uk"));
        assertTrue(ValidationUtil.isValidEmail("user123@test-domain.com"));

        assertFalse(ValidationUtil.isValidEmail(null));
        assertFalse(ValidationUtil.isValidEmail(""));
        assertFalse(ValidationUtil.isValidEmail("invalid"));
        assertFalse(ValidationUtil.isValidEmail("@example.com"));
        assertFalse(ValidationUtil.isValidEmail("user@"));
        assertFalse(ValidationUtil.isValidEmail("user@.com"));
        assertFalse(ValidationUtil.isValidEmail("user@example"));
        assertFalse(ValidationUtil.isValidEmail("user@exam ple.com"));
    }

    @Test
    void testIsValidUsername() {
        assertTrue(ValidationUtil.isValidUsername("user123"));
        assertTrue(ValidationUtil.isValidUsername("user_name"));
        assertTrue(ValidationUtil.isValidUsername("User123"));
        assertTrue(ValidationUtil.isValidUsername("abc"));
        assertTrue(ValidationUtil.isValidUsername("a".repeat(30)));

        assertFalse(ValidationUtil.isValidUsername(null));
        assertFalse(ValidationUtil.isValidUsername(""));
        assertFalse(ValidationUtil.isValidUsername("ab")); // too short
        assertFalse(ValidationUtil.isValidUsername("a".repeat(31))); // too long
        assertFalse(ValidationUtil.isValidUsername("user-name")); // hyphen not allowed
        assertFalse(ValidationUtil.isValidUsername("user.name")); // dot not allowed
        assertFalse(ValidationUtil.isValidUsername("user name")); // space not allowed
        assertFalse(ValidationUtil.isValidUsername("user@name")); // @ not allowed
    }

    @Test
    void testParseInt() {
        assertEquals(Integer.valueOf(42), ValidationUtil.parseInt("42"));
        assertEquals(Integer.valueOf(-10), ValidationUtil.parseInt("-10"));
        assertEquals(Integer.valueOf(0), ValidationUtil.parseInt("0"));
        assertEquals(Integer.valueOf(42), ValidationUtil.parseInt(" 42 "));

        assertNull(ValidationUtil.parseInt(null));
        assertNull(ValidationUtil.parseInt(""));
        assertNull(ValidationUtil.parseInt("abc"));
        assertNull(ValidationUtil.parseInt("42.5"));
        assertNull(ValidationUtil.parseInt("  "));
    }

    @Test
    void testParseDecimal() {
        assertEquals(new BigDecimal("42.50"), ValidationUtil.parseDecimal("42.50"));
        assertEquals(new BigDecimal("-10.5"), ValidationUtil.parseDecimal("-10.5"));
        assertEquals(new BigDecimal("0"), ValidationUtil.parseDecimal("0"));
        assertEquals(new BigDecimal("100"), ValidationUtil.parseDecimal(" 100 "));

        assertNull(ValidationUtil.parseDecimal(null));
        assertNull(ValidationUtil.parseDecimal(""));
        assertNull(ValidationUtil.parseDecimal("abc"));
        assertNull(ValidationUtil.parseDecimal("  "));
    }

    @Test
    void testIsValidLength() {
        assertTrue(ValidationUtil.isValidLength("hello", 1, 10));
        assertTrue(ValidationUtil.isValidLength("hello", 5, 5));
        assertTrue(ValidationUtil.isValidLength("", 0, 10));

        assertFalse(ValidationUtil.isValidLength("hello", 6, 10)); // too short
        assertFalse(ValidationUtil.isValidLength("hello", 1, 4)); // too long
        assertFalse(ValidationUtil.isValidLength(null, 1, 10));
        assertTrue(ValidationUtil.isValidLength(null, 0, 10));
    }

    @Test
    void testIsValidMaxLength() {
        assertTrue(ValidationUtil.isValidMaxLength("hello", 10));
        assertTrue(ValidationUtil.isValidMaxLength("hello", 5));
        assertTrue(ValidationUtil.isValidMaxLength("", 10));
        assertTrue(ValidationUtil.isValidMaxLength(null, 10));

        assertFalse(ValidationUtil.isValidMaxLength("hello", 4));
    }

    @Test
    void testIsValidMinLength() {
        assertTrue(ValidationUtil.isValidMinLength("hello", 5));
        assertTrue(ValidationUtil.isValidMinLength("hello", 1));
        assertTrue(ValidationUtil.isValidMinLength("", 0));

        assertFalse(ValidationUtil.isValidMinLength("hello", 6));
        assertFalse(ValidationUtil.isValidMinLength("", 1));
        assertFalse(ValidationUtil.isValidMinLength(null, 1));
        assertTrue(ValidationUtil.isValidMinLength(null, 0));
    }

    @Test
    void testHasNoXssPatterns() {
        // Clean inputs should pass
        assertTrue(ValidationUtil.hasNoXssPatterns("normal text"));
        assertTrue(ValidationUtil.hasNoXssPatterns("user@example.com"));
        assertTrue(ValidationUtil.hasNoXssPatterns("<b>bold</b>")); // basic HTML is OK

        // Suspicious patterns should be detected
        assertFalse(ValidationUtil.hasNoXssPatterns("<script>alert(1)</script>"));
        assertFalse(ValidationUtil.hasNoXssPatterns("javascript:alert(1)"));
        assertFalse(ValidationUtil.hasNoXssPatterns("<img src=x onerror=alert(1)>"));
        assertFalse(ValidationUtil.hasNoXssPatterns("<iframe src=evil.com></iframe>"));
        assertFalse(ValidationUtil.hasNoXssPatterns("<object data=evil.swf></object>"));
        assertFalse(ValidationUtil.hasNoXssPatterns("<embed src=evil.swf>"));
        assertFalse(ValidationUtil.hasNoXssPatterns("expression(alert(1))"));
        assertFalse(ValidationUtil.hasNoXssPatterns("vbscript:msgbox(1)"));
        assertFalse(ValidationUtil.hasNoXssPatterns("onload=alert(1)"));
        assertFalse(ValidationUtil.hasNoXssPatterns("onclick=alert(1)"));
        assertFalse(ValidationUtil.hasNoXssPatterns("onmouseover=alert(1)"));

        // Case insensitive
        assertFalse(ValidationUtil.hasNoXssPatterns("<SCRIPT>alert(1)</SCRIPT>"));
        assertFalse(ValidationUtil.hasNoXssPatterns("JavaScript:alert(1)"));
    }

    @Test
    void testContainsOnlySafeCharacters() {
        assertTrue(ValidationUtil.containsOnlySafeCharacters("Hello World!"));
        assertTrue(ValidationUtil.containsOnlySafeCharacters("user@example.com"));
        assertTrue(ValidationUtil.containsOnlySafeCharacters("Price: $19.99 (50% off)"));
        assertTrue(ValidationUtil.containsOnlySafeCharacters("Path: /home/user/file.txt"));
        assertTrue(ValidationUtil.containsOnlySafeCharacters(null));
        assertTrue(ValidationUtil.containsOnlySafeCharacters(""));

        // These should still pass as they're in the allowed set
        assertTrue(ValidationUtil.containsOnlySafeCharacters("Test@#$%^&*()_+-=[]{};':\"|,.<>/?"));
    }
}