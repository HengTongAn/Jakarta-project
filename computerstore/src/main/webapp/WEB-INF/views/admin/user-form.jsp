<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${mode == 'reset' ? 'Reset Password' : (mode == 'create' ? 'Add User' : 'Edit User')} - Admin"/>
<c:set var="selectedRole" value="${not empty formRole ? formRole : (not empty targetUser ? targetUser.role.name() : 'CUSTOMER')}"/>
<%@ include file="../common/header.jspf" %>
<%@ include file="../common/admin-nav.jspf" %>

<div class="container py-4">
    <div class="card card-hover mx-auto" style="max-width: 560px;">
        <div class="card-header bg-white">
            <h5 class="fw-bold mb-0">
                <c:choose>
                    <c:when test="${mode == 'reset'}">Reset Password</c:when>
                    <c:when test="${mode == 'create'}">Add User</c:when>
                    <c:otherwise>Edit User</c:otherwise>
                </c:choose>
            </h5>
        </div>
        <div class="card-body">
            <c:if test="${not empty error}">
                <div class="alert alert-danger py-2">${error}</div>
            </c:if>
            <c:if test="${mode == 'reset' && not empty targetUser}">
                <p class="text-muted">Setting a new password for
                    <strong><c:out value="${targetUser.username}"/></strong>
                    (<c:out value="${targetUser.fullName}"/>).</p>
            </c:if>

            <form method="post" enctype="multipart/form-data" action="${pageContext.request.contextPath}/admin/users">
                <input type="hidden" name="csrfToken" value="${csrfToken}"/>
                <input type="hidden" name="action" value="${mode}"/>
                <c:if test="${mode != 'create'}">
                    <input type="hidden" name="userId" value="${param.id}"/>
                </c:if>

                <c:choose>
                    <c:when test="${mode == 'reset'}">
                        <div class="mb-3">
                            <label class="form-label">New Password</label>
                            <div class="password-field">
                                <input type="password" name="newPassword" id="resetNewPassword" class="form-control" required
                                       autocomplete="new-password"/>
                                <button type="button" class="password-toggle" data-target="resetNewPassword" aria-label="Show password" tabindex="-1">
                                    <i class="bi bi-eye"></i>
                                </button>
                            </div>
                            <div class="form-text">8+ chars, upper, lower, digit and special character.</div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Confirm New Password</label>
                            <div class="password-field">
                                <input type="password" name="confirmPassword" id="resetConfirmPassword" class="form-control" required
                                       autocomplete="new-password"/>
                                <button type="button" class="password-toggle" data-target="resetConfirmPassword" aria-label="Show password" tabindex="-1">
                                    <i class="bi bi-eye"></i>
                                </button>
                            </div>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="mb-3">
                            <label class="form-label">Username</label>
                            <input type="text" name="username" class="form-control"
                                   value="${not empty formUsername ? formUsername : targetUser.username}"
                                   pattern="[A-Za-z0-9_]{3,30}" required/>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Full name</label>
                            <input type="text" name="fullName" class="form-control"
                                   value="${not empty formFullName ? formFullName : targetUser.fullName}"
                                   maxlength="100" required/>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Email</label>
                            <input type="email" name="email" class="form-control"
                                   value="${not empty formEmail ? formEmail : targetUser.email}"
                                   maxlength="100" required/>
                        </div>
                        <div class="mb-3 admin-avatar-upload">
                            <label class="form-label" for="avatarFile">Profile image <span class="text-muted fw-normal">(optional)</span></label>
                            <div class="d-flex align-items-center gap-3">
                                <c:choose>
                                    <c:when test="${not empty targetUser.avatarUrl}">
                                        <img id="adminAvatarPreview" src="${pageContext.request.contextPath}/${targetUser.avatarUrl}" class="admin-avatar-preview image-upload-preview" alt="Current profile image">
                                    </c:when>
                                    <c:otherwise>
                                        <span id="adminAvatarPreview" class="admin-avatar-preview admin-avatar-placeholder image-upload-preview"><i class="bi bi-person-fill"></i></span>
                                    </c:otherwise>
                                </c:choose>
                                <div class="flex-grow-1">
                                    <input id="avatarFile" type="file" name="avatarFile" class="form-control" accept="image/jpeg,image/png,image/gif,image/webp" data-image-input data-preview-target="#adminAvatarPreview" data-image-status="#adminAvatarStatus">
                                    <div id="adminAvatarStatus" class="form-text" aria-live="polite">JPG, PNG, GIF or WEBP. Maximum 5MB.</div>
                                </div>
                            </div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Role</label>
                            <c:if test="${mode == 'update' && sessionScope.user.userId == param.id}">
                                <input type="hidden" name="role" value="${selectedRole}"/>
                            </c:if>
                            <select name="role" class="form-select"
                                    <c:if test="${mode == 'update' && sessionScope.user.userId == param.id}">disabled</c:if>>
                                <option value="">-- Select role --</option>
                                <option value="CUSTOMER" <c:if test="${selectedRole == 'CUSTOMER'}">selected</c:if>>Customer</option>
                                <option value="ADMIN" <c:if test="${selectedRole == 'ADMIN'}">selected</c:if>>Admin</option>
                            </select>
                            <c:if test="${mode == 'update' && sessionScope.user.userId == param.id}">
                                <div class="form-text">Your own role cannot be changed.</div>
                            </c:if>
                        </div>
                        <c:if test="${mode == 'create'}">
                            <div class="mb-3">
                                <label class="form-label">Password</label>
                                <div class="password-field">
                                    <input type="password" name="password" id="createPassword" class="form-control" required
                                           autocomplete="new-password"/>
                                    <button type="button" class="password-toggle" data-target="createPassword" aria-label="Show password" tabindex="-1">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                </div>
                                <div class="form-text">8+ chars, upper, lower, digit and special character.</div>
                            </div>
                            <div class="mb-3">
                                <label class="form-label">Confirm Password</label>
                                <div class="password-field">
                                    <input type="password" name="confirmPassword" id="createConfirmPassword" class="form-control" required
                                           autocomplete="new-password"/>
                                    <button type="button" class="password-toggle" data-target="createConfirmPassword" aria-label="Show password" tabindex="-1">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                </div>
                            </div>
                        </c:if>
                    </c:otherwise>
                </c:choose>

                <div class="d-flex gap-2">
                    <button type="submit" class="btn btn-brand">
                        <c:choose>
                            <c:when test="${mode == 'reset'}">Reset Password</c:when>
                            <c:when test="${mode == 'create'}">Create User</c:when>
                            <c:otherwise>Save Changes</c:otherwise>
                        </c:choose>
                    </button>
                    <a href="${pageContext.request.contextPath}/admin/users" class="btn btn-outline-secondary">Cancel</a>
                </div>
            </form>
        </div>
    </div>
</div>
<%@ include file="../common/footer.jspf" %>
