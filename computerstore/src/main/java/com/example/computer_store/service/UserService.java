package com.example.computer_store.service;

import com.example.computer_store.dao.UserDAO;
import com.example.computer_store.exception.NotFoundException;
import com.example.computer_store.exception.ValidationException;
import com.example.computer_store.model.User;
import com.example.computer_store.util.PasswordUtil;
import com.example.computer_store.util.ValidationUtil;

import java.util.List;

public class UserService {

    private final UserDAO userDAO = new UserDAO();

    public User get(int userId) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new NotFoundException("User does not exist.");
        }
        user.setPasswordHash(null);
        return user;
    }

    public List<User> getCustomers() {
        return userDAO.findByRole(User.Role.CUSTOMER);
    }

    public List<User> getAdmins() {
        List<User> admins = userDAO.findByRole(User.Role.ADMIN);
        admins.forEach(a -> a.setPasswordHash(null));
        return admins;
    }

    public List<User> getAll() {
        List<User> users = userDAO.findAll();
        users.forEach(u -> u.setPasswordHash(null));
        return users;
    }

    public void updateLastActive(int userId) {
        userDAO.updateLastActive(userId);
    }

    /**
     * Admin-created account. Unlike public registration the role is chosen by
     * the acting admin; the caller must already be authenticated as ADMIN.
     */
    public User create(String username, String fullName, String email,
                       String plainPassword, String confirmPassword, User.Role role) {
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
        if (!ValidationUtil.isValidMaxLength(fullName, 100)) {
            throw new ValidationException("Full name must not exceed 100 characters.");
        }
        if (!ValidationUtil.isValidMaxLength(email, 100)) {
            throw new ValidationException("Email must not exceed 100 characters.");
        }
        if (!plainPassword.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match.");
        }
        if (role != User.Role.ADMIN && role != User.Role.CUSTOMER) {
            throw new ValidationException("Invalid role selected.");
        }
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
        user.setRole(role);

        int id = userDAO.create(user);
        if (id <= 0) {
            throw new ValidationException("Account creation failed. Please try again.");
        }
        user.setUserId(id);
        user.setPasswordHash(null);
        return user;
    }

    /**
     * Admin edits another user's profile and role. The acting user can never
     * change their own role (prevents accidental self-lockout).
     */
    public void updateProfile(int actorUserId, int userId, String username,
                              String fullName, String email, User.Role role) {
        if (ValidationUtil.isBlank(fullName) || ValidationUtil.isBlank(username)) {
            throw new ValidationException("Full name and username are required.");
        }
        if (!ValidationUtil.isValidUsername(username)) {
            throw new ValidationException("Username must be 3-30 characters using letters, digits or underscore.");
        }
        if (!ValidationUtil.isValidEmail(email)) {
            throw new ValidationException("Please enter a valid email address.");
        }
        if (role != User.Role.ADMIN && role != User.Role.CUSTOMER) {
            throw new ValidationException("Invalid role selected.");
        }
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new NotFoundException("User does not exist.");
        }
        if (actorUserId == userId && role != user.getRole()) {
            throw new ValidationException("You cannot change your own role.");
        }

        User nameOwner = userDAO.findByUsername(username.trim());
        if (nameOwner != null && nameOwner.getUserId() != userId) {
            throw new ValidationException("A user with this username already exists.");
        }
        User emailOwner = userDAO.findByEmail(email.trim());
        if (emailOwner != null && emailOwner.getUserId() != userId) {
            throw new ValidationException("An account with this email already exists.");
        }

        user.setUsername(username.trim());
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        user.setRole(role);
        userDAO.update(user);
    }

    /**
     * Admin resets a user's password without needing the old one.
     */
    public void resetPassword(int userId, String newPassword, String confirmPassword) {
        if (userDAO.findById(userId) == null) {
            throw new NotFoundException("User does not exist.");
        }
        if (ValidationUtil.isBlank(newPassword)) {
            throw new ValidationException("New password is required.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new ValidationException("Passwords do not match.");
        }
        validatePasswordStrength(newPassword);
        userDAO.updatePassword(userId, PasswordUtil.hash(newPassword));
    }

    public void updateProfile(int userId, String fullName, String email) {
        if (ValidationUtil.isBlank(fullName)) {
            throw new ValidationException("Full name is required.");
        }
        if (!ValidationUtil.isValidEmail(email)) {
            throw new ValidationException("Please enter a valid email address.");
        }
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new NotFoundException("User does not exist.");
        }
        User emailOwner = userDAO.findByEmail(email.trim());
        if (emailOwner != null && emailOwner.getUserId() != userId) {
            throw new ValidationException("An account with this email already exists.");
        }
        user.setFullName(fullName.trim());
        user.setEmail(email.trim());
        userDAO.update(user);
    }

    public void changePassword(int userId, String currentPassword, String newPassword) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new NotFoundException("User does not exist.");
        }
        if (ValidationUtil.isBlank(currentPassword) || ValidationUtil.isBlank(newPassword)) {
            throw new ValidationException("Both passwords are required.");
        }
        if (!PasswordUtil.check(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect.");
        }
        
        // Validate password strength (same as AuthService requirements)
        validatePasswordStrength(newPassword);
        
        userDAO.updatePassword(userId, PasswordUtil.hash(newPassword));
    }

    public void updateAvatar(int userId, String avatarUrl) {
        userDAO.updateAvatar(userId, avatarUrl);
    }

    /**
     * Validates password strength according to security requirements.
     * Matches the validation used in AuthService for consistency.
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