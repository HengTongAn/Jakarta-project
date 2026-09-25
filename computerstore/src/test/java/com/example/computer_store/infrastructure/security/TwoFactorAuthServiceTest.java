package com.example.computer_store.infrastructure.security;

import com.example.computer_store.core.domain.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorAuthServiceTest {

    private String testEncryptionKey;

    @BeforeEach
    void setUp() {
        // Generate a valid 256-bit key for testing
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        testEncryptionKey = Base64.getEncoder().encodeToString(keyBytes);
        System.setProperty("computerstore.2fa.encryption.key", testEncryptionKey);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("computerstore.2fa.encryption.key");
    }

    @Test
    void testValidateConfigurationWithValidKey() {
        assertDoesNotThrow(() -> TwoFactorAuthService.validateConfiguration());
    }

    @Test
    void testValidateConfigurationWithoutKey() {
        System.clearProperty("computerstore.2fa.encryption.key");
        System.clearProperty("COMPUTERSTORE_2FA_ENCRYPTION_KEY");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TwoFactorAuthService.validateConfiguration());
        assertTrue(ex.getMessage().contains("2FA encryption is not configured"));
    }

    @Test
    void testValidateConfigurationWithInvalidKey() {
        System.setProperty("computerstore.2fa.encryption.key", "invalid-key");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TwoFactorAuthService.validateConfiguration());
        assertTrue(ex.getMessage().contains("Invalid"));
    }

    @Test
    void testGenerateBackupCode() {
        String code1 = TwoFactorAuthService.generateBackupCode();
        String code2 = TwoFactorAuthService.generateBackupCode();

        assertEquals(8, code1.length());
        assertEquals(8, code2.length());
        assertNotEquals(code1, code2); // Should be random

        // Should only contain valid base32 characters
        assertTrue(code1.matches("[A-Z2-7]{8}"));
    }

    @Test
    void testEncryptDecrypt() throws Exception {
        // Use reflection to test private encrypt/decrypt methods
        var encryptMethod = TwoFactorAuthService.class.getDeclaredMethod("encrypt", String.class);
        encryptMethod.setAccessible(true);
        var decryptMethod = TwoFactorAuthService.class.getDeclaredMethod("decrypt", String.class);
        decryptMethod.setAccessible(true);

        String plaintext = "TESTSECRET123456";
        String encrypted = (String) encryptMethod.invoke(null, plaintext);
        String decrypted = (String) decryptMethod.invoke(null, encrypted);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith("v1:"));
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testDecryptLegacyPlaintextReturnsNull() throws Exception {
        var decryptMethod = TwoFactorAuthService.class.getDeclaredMethod("decrypt", String.class);
        decryptMethod.setAccessible(true);

        String result = (String) decryptMethod.invoke(null, "plaintext-secret");
        assertNull(result);
    }

    @Test
    void testDecryptWrongVersionReturnsNull() throws Exception {
        var decryptMethod = TwoFactorAuthService.class.getDeclaredMethod("decrypt", String.class);
        decryptMethod.setAccessible(true);

        String result = (String) decryptMethod.invoke(null, "v2:invalid");
        assertNull(result);
    }

    @Test
    void testDecryptCorruptedDataReturnsNull() throws Exception {
        var decryptMethod = TwoFactorAuthService.class.getDeclaredMethod("decrypt", String.class);
        decryptMethod.setAccessible(true);

        String result = (String) decryptMethod.invoke(null, "v1:corrupteddata==");
        assertNull(result);
    }

    @Test
    void testGenerateSecretKeyRequiresEncryptionKey() {
        System.clearProperty("computerstore.2fa.encryption.key");
        System.clearProperty("COMPUTERSTORE_2FA_ENCRYPTION_KEY");

        User user = new User();
        user.setUserId(1);
        user.setUsername("testuser");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TwoFactorAuthService.generateSecretKey(user));
        assertTrue(ex.getMessage().contains("2FA encryption is not configured"));
    }
}