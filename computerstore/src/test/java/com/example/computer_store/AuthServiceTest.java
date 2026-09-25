package com.example.computer_store;

import com.example.computer_store.core.repository.UserRepository;
import com.example.computer_store.core.exception.ValidationException;
import com.example.computer_store.core.domain.entity.User;
import com.example.computer_store.core.service.impl.AuthServiceImpl;
import com.example.computer_store.util.security.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @BeforeAll
    static void disableDatabaseAuditWrites() {
        System.setProperty("computerstore.audit.database.enabled", "false");
    }

    @AfterAll
    static void restoreDatabaseAuditWrites() {
        System.clearProperty("computerstore.audit.database.enabled");
    }

    @Mock
    private UserRepository userDAO;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userDAO);
    }

    @Test
    void loginRejectsBlankCredentials() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> authService.login("  ", "secret"));
        assertEquals("Username and password are required.", ex.getMessage());
        verify(userDAO, never()).findByUsername(any());
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userDAO.findByUsername("nobody")).thenReturn(null);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> authService.login("nobody", "Secret1!"));
        assertEquals("Invalid username or password.", ex.getMessage());
    }

    @Test
    void loginSucceedsAndClearsPasswordHash() {
        User stored = new User();
        stored.setUserId(7);
        stored.setUsername("customer");
        stored.setPasswordHash(PasswordUtil.hash("Customer1!"));
        stored.setRole(User.Role.CUSTOMER);
        when(userDAO.findByUsername("customer")).thenReturn(stored);

        User loggedIn = authService.login("customer", "Customer1!");

        assertEquals(7, loggedIn.getUserId());
        assertEquals("customer", loggedIn.getUsername());
        assertNull(loggedIn.getPasswordHash());
    }

    @Test
    void registerRejectsWeakPassword() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> authService.register("newuser", "New User", "new@example.com",
                        "password", "password"));
        assertTrue(ex.getMessage().toLowerCase().contains("password"));
        verify(userDAO, never()).create(any());
    }

    @Test
    void registerRejectsMismatchedPasswords() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> authService.register("newuser", "New User", "new@example.com",
                        "Strong1!", "Strong2!"));
        assertEquals("Passwords do not match.", ex.getMessage());
    }
}
