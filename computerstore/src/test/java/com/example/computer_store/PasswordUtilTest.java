package com.example.computer_store;

import com.example.computer_store.util.PasswordUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUtilTest {

    @Test
    void hashedPasswordMatchesOriginal() {
        String hash = PasswordUtil.hash("secret123");
        assertTrue(PasswordUtil.check("secret123", hash));
    }

    @Test
    void wrongPasswordIsRejected() {
        String hash = PasswordUtil.hash("secret123");
        assertFalse(PasswordUtil.check("wrong-password", hash));
    }

    @Test
    void malformedHashIsRejectedSafely() {
        assertFalse(PasswordUtil.check("secret123", null));
        assertFalse(PasswordUtil.check("secret123", ""));
        assertFalse(PasswordUtil.check("secret123", "not-a-bcrypt-hash"));
    }
}