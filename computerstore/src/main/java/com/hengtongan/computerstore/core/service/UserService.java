package com.hengtongan.computerstore.core.service;

import com.hengtongan.computerstore.core.repository.UserRepository;
import com.hengtongan.computerstore.core.exception.NotFoundException;
import com.hengtongan.computerstore.core.exception.ValidationException;
import com.hengtongan.computerstore.core.domain.entity.User;
import com.hengtongan.computerstore.util.security.PasswordUtil;
import com.hengtongan.computerstore.util.validation.ValidationUtil;

import java.util.List;

public class UserService {

    private final UserRepository userDAO = new UserRepository();

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
        List<User> admins = userDAO.findAdmins();
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
     * Only a SUPER_ADMIN actor may create ADMIN accounts. The SUPER_ADMIN
     * role itself is never creatable through the app (DB-only, one true root).
     */
    public User create(int actorUserId, String username, String fullName, String email,
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
        if (role == null || (role != User.Role.ADMIN && role != User.Role.CUSTOMER
                && role != User.Role.SUPER_ADMIN)) {
            throw new ValidationException("Invalid role selected.");
        }
        // Option 1 (one true root): SUPER_ADMIN is created only directly in the
        // database, never through the application.
        if (role == User.Role.SUPER_ADMIN) {
            throw new ValidationException("The super admin role is managed directly in the database.");
        }
        if (role != User.Role.CUSTOMER && !isSuperAdminActor(actorUserId)) {
            throw new ValidationException("Only the super admin can create admin accounts.");
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
     * change their own role (prevents accidental self-lockout). The role
     * hierarchy is enforced here: only a SUPER_ADMIN can manage admin roles,
     * and the SUPER_ADMIN role itself is DB-only (one true root).
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
        if (role == null || (role != User.Role.ADMIN && role != User.Role.CUSTOMER
                && role != User.Role.SUPER_ADMIN)) {
            throw new ValidationException("Invalid role selected.");
        }
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new NotFoundException("User does not exist.");
        }
        if (actorUserId == userId && role != user.getRole()) {
            throw new ValidationException("You cannot change your own role.");
        }
        // Option 1 (one true root): the SUPER_ADMIN role can never be granted
        // through the app, and never revoked through the app either. Only the
        // database assigns it. This makes a coup impossible regardless of how
        // many super admins exist.
        if (role == User.Role.SUPER_ADMIN && user.getRole() != User.Role.SUPER_ADMIN) {
            throw new ValidationException("The super admin role is managed directly in the database.");
        }
        if (user.getRole() == User.Role.SUPER_ADMIN && role != User.Role.SUPER_ADMIN) {
            throw new ValidationException("The super admin role cannot be changed through the app.");
        }

        boolean actorIsSuper = isSuperAdminActor(actorUserId);
        // Only the super admin may touch any admin-level role (grant or revoke).
        boolean changesAdminLevel = user.getRole() != User.Role.CUSTOMER || role != User.Role.CUSTOMER;
        if (changesAdminLevel && !actorIsSuper) {
            throw new ValidationException("Only the super admin can manage admin roles.");
        }
        // A super-admin account can only be edited by another super admin.
        if (user.getRole() == User.Role.SUPER_ADMIN && !actorIsSuper) {
            throw new ValidationException("Only the super admin can edit a super admin account.");
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
     * Only a SUPER_ADMIN may reset another SUPER_ADMIN's password
     * (otherwise a regular admin could take over the highest account).
     */
    public void resetPassword(int actorUserId, int userId, String newPassword, String confirmPassword) {
        User target = userDAO.findById(userId);
        if (target == null) {
            throw new NotFoundException("User does not exist.");
        }
        if (target.getRole() == User.Role.SUPER_ADMIN && !isSuperAdminActor(actorUserId)) {
            throw new ValidationException("Only the super admin can reset a super admin's password.");
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

    private boolean isSuperAdminActor(int actorUserId) {
        if (actorUserId <= 0) {
            return false;
        }
        User actor = userDAO.findById(actorUserId);
        return actor != null && actor.getRole() == User.Role.SUPER_ADMIN;
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
     * Public because the forgot-password flow enforces the same rules.
     */
    public static void validatePasswordStrength(String password) {
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