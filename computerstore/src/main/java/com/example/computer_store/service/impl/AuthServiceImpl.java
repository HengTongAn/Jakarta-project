package com.example.computer_store.service.impl;

import com.example.computer_store.service.AuthService;

import com.example.computer_store.dao.UserDAO;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.User;
import com.example.computer_store.util.AuditLogger;
import com.example.computer_store.util.PasswordUtil;
import com.example.computer_store.util.ValidationUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Registration / login business logic. Sensitive data (password hashes) is
 * never exposed outside the service.
 */
public class AuthServiceImpl implements AuthService {

    private final UserDAO userDAO;

    public AuthServiceImpl() {
        this(new UserDAO());
    }

    public AuthServiceImpl(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public User login(String username, String plainPassword) {
        return login(username, plainPassword, null);
    }

    @Override
    public User login(String username, String plainPassword, HttpServletRequest request) {
        if (ValidationUtil.isBlank(username) || ValidationUtil.isBlank(plainPassword)) {
            throw new ValidationException("Username and password are required.");
        }
        User user = userDAO.findByUsername(username.trim());
        if (user == null) {
            String ipAddress = request != null ? getClientIp(request) : "UNKNOWN";
            AuditLogger.logAuthEvent("LOGIN_FAILED", username.trim(), ipAddress, "User not found");
            throw new ValidationException("Invalid username or password.");
        }
        if (!PasswordUtil.check(plainPassword, user.getPasswordHash())) {
            String ipAddress = request != null ? getClientIp(request) : "UNKNOWN";
            AuditLogger.logAuthEvent("LOGIN_FAILED", username.trim(), ipAddress, "Invalid password");
            throw new ValidationException("Invalid username or password.");
        }
        
        String ipAddress = request != null ? getClientIp(request) : "UNKNOWN";
        AuditLogger.logAuthEvent("LOGIN_SUCCESS", username.trim(), ipAddress, "User authenticated successfully");
        
        user.setPasswordHash(null);
        return user;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public User register(String username, String fullName, String email,
                         String plainPassword, String confirmPassword) {
        if (ValidationUtil.isBlank(username) || ValidationUtil.isBlank(fullName)
                || ValidationUtil.isBlank(email) || ValidationUtil.isBlank(plainPassword)) {
            throw new ValidationException("All fields are required.");
        }
        if (!ValidationUtil.isValidUsername(username)) {
            throw new ValidationException("Username must be 3-30 characters using letters, digits or underscore.");
        }
        if (!ValidationUtil.isValidEmail(email)) {
            throw new ValidationException("Please enter a valid email address.");
        }
        
        // Add length validation
        if (!ValidationUtil.isValidMaxLength(fullName, 100)) {
            throw new ValidationException("Full name must not exceed 100 characters.");
        }
        if (!ValidationUtil.isValidMaxLength(email, 100)) {
            throw new ValidationException("Email must not exceed 100 characters.");
        }
        
        if (!plainPassword.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match.");
        }
        
        // Enhanced password validation
        validatePasswordStrength(plainPassword);
        
        if (userDAO.findByUsername(username.trim()) != null) {
            throw new ValidationException("Username is already taken.");
        }
        if (userDAO.findByEmail(email.trim()) != null) {
            throw new ValidationException("An account with this email already exists.");
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setPasswordHash(PasswordUtil.hash(plainPassword));
        user.setRole(User.Role.CUSTOMER);

        int id = userDAO.create(user);
        if (id <= 0) {
            throw new ValidationException("Registration failed. Please try again.");
        }
        user.setUserId(id);
        user.setPasswordHash(null);
        return user;
    }

    /**
     * Validates password strength according to security requirements.
     */
    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long.");
        }
        if (password.length() > 128) {
            throw new ValidationException("Password must not exceed 128 characters.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new ValidationException("Password must contain at least one uppercase letter.");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new ValidationException("Password must contain at least one lowercase letter.");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new ValidationException("Password must contain at least one digit.");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new ValidationException("Password must contain at least one special character (!@#$%^&*()_+-=[]{};':\"|,.<>/?).");
        }
        
        // Check for common weak passwords
        String[] commonPasswords = {"password", "12345678", "qwerty", "abc123", "letmein", "admin", "welcome"};
        String lowerPassword = password.toLowerCase();
        for (String common : commonPasswords) {
            if (lowerPassword.contains(common)) {
                throw new ValidationException("Password contains common weak patterns. Please choose a stronger password.");
            }
        }
    }
}