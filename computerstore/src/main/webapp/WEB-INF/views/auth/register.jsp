<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Create Account - TechStore"/>
<%@ include file="../layouts/header.jspf" %>
<div class="container my-5">
    <div class="row justify-content-center">
        <div class="col-md-6 col-lg-5">
            <div class="text-center mb-4">
                <img class="auth-logo" src="${pageContext.request.contextPath}/assets/images/techstore-mark.svg" alt="TechStore" style="height:44px;width:auto">
                <h4 class="fw-bold mb-0 mt-3">TechStore</h4>
            </div>
            <div class="card card-hover">
                <div class="card-body p-4">
                    <h4 class="card-title mb-1 fw-bold">Create your account</h4>
                    <p class="text-muted small mb-4">Join to browse, order and track your purchase history.</p>

                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                    </c:if>

                    <form method="post" action="${pageContext.request.contextPath}/register">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <div class="mb-3">
                            <label class="form-label">Username</label>
                            <input type="text" name="username" class="form-control" required
                                   value="<c:out value='${username}'/>">
                            <div class="form-text">3-30 characters, letters / digits / underscore.</div>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Full name</label>
                            <input type="text" name="fullName" class="form-control" required
                                   value="<c:out value='${fullName}'/>">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Email</label>
                            <input type="email" name="email" class="form-control" required
                                   value="<c:out value='${email}'/>">
                        </div>
                        <div class="row">
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Password</label>
                                <div class="password-field">
                                    <input type="password" name="password" id="regPassword" class="form-control" required
                                           autocomplete="new-password">
                                    <button type="button" class="password-toggle" data-target="regPassword" aria-label="Show password" tabindex="-1">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                </div>
                                <div class="form-text">Minimum 8 characters with uppercase, lowercase, digit, and special character.</div>
                            </div>
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Confirm password</label>
                                <div class="password-field">
                                    <input type="password" name="confirmPassword" id="regConfirmPassword" class="form-control" required
                                           autocomplete="new-password">
                                    <button type="button" class="password-toggle" data-target="regConfirmPassword" aria-label="Show password" tabindex="-1">
                                        <i class="bi bi-eye"></i>
                                    </button>
                                </div>
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand w-100">Create account</button>
                    </form>

                    <p class="text-center mt-3 mb-0 small">
                        Already have an account?
                        <a href="${pageContext.request.contextPath}/login">Login</a>
                    </p>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>
