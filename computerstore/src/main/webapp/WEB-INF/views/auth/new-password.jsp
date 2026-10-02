<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="New Password - Apach_PC/STORE"/>
<%@ include file="../layouts/header.jspf" %>
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-md-5 col-lg-4">
            <div class="text-center mb-4">
                <img class="auth-logo" src="${pageContext.request.contextPath}/assets/images/apach-pc-store.svg" alt="Apach_PC/STORE" style="height:44px;width:auto">
                <h4 class="fw-bold mb-0 mt-3">Apach_PC/STORE</h4>
            </div>
            <div class="card card-hover">
                <div class="card-body p-4">
                    <h4 class="card-title mb-1 fw-bold">Choose a new password</h4>
                    <p class="text-muted small mb-4">Enter your new password below.</p>

                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                    </c:if>

                    <%-- The account is taken from the verified code held in the session,
                         not from this form: there is deliberately no email input. --%>
                    <div class="mb-3">
                        <label class="form-label">Account</label>
                        <input type="text" class="form-control" value="<c:out value='${email}'/>" readonly disabled>
                    </div>

                    <form method="post" action="${pageContext.request.contextPath}/new-password">
                        <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}'/>">
                        <div class="mb-3">
                            <label class="form-label">New password</label>
                            <div class="password-field">
                                <input type="password" name="newPassword" id="newPassword" class="form-control" required
                                       autocomplete="new-password">
                                <button type="button" class="password-toggle" data-target="newPassword" aria-label="Show password" tabindex="-1">
                                    <i class="bi bi-eye"></i>
                                </button>
                            </div>
                            <div class="form-text">Minimum 8 characters with uppercase, lowercase, digit, and special character.</div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Confirm new password</label>
                            <div class="password-field">
                                <input type="password" name="confirmPassword" id="confirmPassword" class="form-control" required
                                       autocomplete="new-password">
                                <button type="button" class="password-toggle" data-target="confirmPassword" aria-label="Show password" tabindex="-1">
                                    <i class="bi bi-eye"></i>
                                </button>
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand w-100">Update password</button>
                    </form>

                    <p class="text-center mt-3 mb-0 small">
                        <a href="${pageContext.request.contextPath}/login">Back to login</a>
                    </p>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>
