<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Login - Apach_PC/STORE"/>
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
                    <p class="text-muted small mb-4">Sign in to continue shopping or manage the store.</p>

                    <c:if test="${not empty info}">
			<div class="alert alert-info py-2 small"><c:out value="${info}"/></div>
                    </c:if>
                    <c:if test="${not empty error}">
                        <div class="alert alert-danger py-2 small"><c:out value="${error}"/></div>
                    </c:if>

                    <c:choose>
                    <c:when test="${requiresTwoFactor}">
                    <p class="text-muted small mb-4">Enter the 6-digit code from your authenticator app.</p>
                    <form method="post" action="${pageContext.request.contextPath}/login">
	                   <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="verify2fa">
                        <div class="mb-3">
                            <label class="form-label" for="twoFactorCode">Authentication code</label>
                            <input id="twoFactorCode" type="text" name="code" class="form-control" required
                                   inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6">
                        </div>
                        <button type="submit" class="btn btn-brand w-100">Verify and sign in</button>
                    </form>
                    </c:when>
                    <c:otherwise>
                    <form method="post" action="${pageContext.request.contextPath}/login">
	                   <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="return" value="<c:out value="${param['return']}"/>">
                        <div class="mb-3">
                            <label class="form-label">Username</label>
                            <input type="text" name="username" class="form-control" required
                                   value="<c:out value='${username}'/>">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Password</label>
                            <div class="password-field">
                                <input type="password" name="password" id="loginPassword" class="form-control" required
                                       autocomplete="current-password">
                                <button type="button" class="password-toggle" data-target="loginPassword" aria-label="Show password" tabindex="-1">
                                    <i class="bi bi-eye"></i>
                                </button>
                            </div>
                        </div>
                        <button type="submit" class="btn btn-brand w-100">Login</button>
                    </form>
                    </c:otherwise>
                    </c:choose>

                    <p class="text-center mt-2 mb-0">
                        <a href="${pageContext.request.contextPath}/forgot" class="small">Forgot password?</a>
                    </p>

                    <p class="text-center mt-3 mb-0 small">
                        Don't have an account?
                        <a href="${pageContext.request.contextPath}/register">Register</a>
                    </p>

                    <hr>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../layouts/footer.jspf" %>
