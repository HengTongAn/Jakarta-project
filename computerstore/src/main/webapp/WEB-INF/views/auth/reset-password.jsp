<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Choose a New Password - TechStore"/>
<%@ include file="../layouts/header.jspf" %>
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-md-5 col-lg-4">
            <div class="text-center mb-4">
                <img class="auth-logo" src="${pageContext.request.contextPath}/assets/images/techstore-mark.svg" alt="TechStore" style="height:44px;width:auto">
            </div>
            <div class="card card-hover">
                <div class="card-body p-4">
                    <c:choose>
                        <c:when test="${not empty invalid}">
                            <h4 class="card-title mb-1 fw-bold">Link invalid or expired</h4>
                            <p class="text-muted small mb-4">
                                This reset link is invalid or has expired. Please request a new one.
                            </p>
                            <a href="${pageContext.request.contextPath}/forgot" class="btn btn-brand w-100">Request a new link</a>
                        </c:when>
                        <c:otherwise>
                            <h4 class="card-title mb-1 fw-bold">Choose a new password</h4>
                            <p class="text-muted small mb-4">Your identity is confirmed by the emailed link.</p>

                            <c:if test="${not empty error}">
                                <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                            </c:if>

                            <form method="post" action="${pageContext.request.contextPath}/reset">
                                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                                <input type="hidden" name="token" value="<c:out value='${token}'/>">
                                <div class="mb-3">
                                    <label class="form-label">New password</label>
                                    <div class="password-field">
                                        <input type="password" name="newPassword" id="resetPassword" class="form-control" required
                                               autocomplete="new-password">
                                        <button type="button" class="password-toggle" data-target="resetPassword" aria-label="Show password" tabindex="-1">
                                            <i class="bi bi-eye"></i>
                                        </button>
                                    </div>
                                    <div class="form-text">Minimum 8 characters with uppercase, lowercase, digit, and special character.</div>
                                </div>
                                <div class="mb-3">
                                    <label class="form-label">Confirm new password</label>
                                    <div class="password-field">
                                        <input type="password" name="confirmPassword" id="resetConfirmPassword" class="form-control" required
                                               autocomplete="new-password">
                                        <button type="button" class="password-toggle" data-target="resetConfirmPassword" aria-label="Show password" tabindex="-1">
                                            <i class="bi bi-eye"></i>
                                        </button>
                                    </div>
                                </div>
                                <button type="submit" class="btn btn-brand w-100">Update password</button>
                            </form>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>
