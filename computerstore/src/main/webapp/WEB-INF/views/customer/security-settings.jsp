<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Security Settings - TechStore"/>
<%@ include file="../common/header.jspf" %>
<div class="container py-4">
    <h4 class="fw-bold mb-3">Security settings</h4>
    <div class="row g-3">
        <div class="col-lg-3">
            <div class="card card-hover customer-sidebar"><div class="card-body p-0"><div class="list-group list-group-flush">
                <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account">Dashboard</a>
                <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/orders">My Orders</a>
                <a class="list-group-item list-group-item-action" href="${pageContext.request.contextPath}/account/profile">Profile</a>
                <a class="list-group-item list-group-item-action active" href="${pageContext.request.contextPath}/account/settings"><i class="bi bi-shield-lock me-2"></i>Security</a>
            </div></div></div>
        </div>
        <div class="col-lg-9">
            <div class="card card-hover security-settings-card"><div class="card-body p-4">
                <div class="security-settings-heading"><span class="security-settings-icon"><i class="bi bi-key-fill"></i></span><div><h5 class="fw-bold mb-1">Change password</h5><p class="text-muted small mb-0">Use a strong, unique password to protect your account.</p></div></div>
                <form method="post" action="${pageContext.request.contextPath}/account/settings" class="mt-4" data-password-confirm>
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <div class="mb-3"><label class="form-label" for="settingsCurrentPassword">Current password</label><div class="password-field"><input id="settingsCurrentPassword" type="password" name="currentPassword" class="form-control" autocomplete="current-password" required><button type="button" class="password-toggle" data-target="settingsCurrentPassword" aria-label="Show password" tabindex="-1"><i class="bi bi-eye"></i></button></div></div>
                    <div class="row"><div class="col-md-6 mb-3"><label class="form-label" for="settingsNewPassword">New password</label><div class="password-field"><input id="settingsNewPassword" type="password" name="newPassword" class="form-control" autocomplete="new-password" required><button type="button" class="password-toggle" data-target="settingsNewPassword" aria-label="Show password" tabindex="-1"><i class="bi bi-eye"></i></button></div></div><div class="col-md-6 mb-3"><label class="form-label" for="settingsConfirmPassword">Confirm new password</label><div class="password-field"><input id="settingsConfirmPassword" type="password" name="confirmPassword" class="form-control" autocomplete="new-password" required><button type="button" class="password-toggle" data-target="settingsConfirmPassword" aria-label="Show password" tabindex="-1"><i class="bi bi-eye"></i></button></div><div class="invalid-feedback">Passwords do not match.</div></div></div>
                    <div class="security-password-help"><i class="bi bi-info-circle"></i><span>Use 8+ characters with uppercase, lowercase, a number, and a special character.</span></div>
                    <button type="submit" class="btn btn-brand mt-4" data-loading="Updating password…"><i class="bi bi-shield-check me-2"></i>Update password</button>
                </form>
            </div></div>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
